package com.example.droneservicesapp.data.ortho

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class DecodedTiffBitmap(
    val bitmap: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int
)

class SimpleTiffDecoder {

    fun decode(inputStream: InputStream): Bitmap {
        return decodeBitmap(inputStream, maxPreviewDimension = null).bitmap
    }

    fun decodePreview(inputStream: InputStream, maxPreviewDimension: Int): DecodedTiffBitmap {
        require(maxPreviewDimension > 0) { "Preview dimension must be positive." }
        return decodeBitmap(inputStream, maxPreviewDimension)
    }

    private fun decodeBitmap(inputStream: InputStream, maxPreviewDimension: Int?): DecodedTiffBitmap {
        val bytes = inputStream.use { stream ->
            ByteArrayOutputStream().use { output ->
                stream.copyTo(output)
                output.toByteArray()
            }
        }
        return decode(bytes, maxPreviewDimension)
    }

    fun decode(bytes: ByteArray): Bitmap {
        return decode(bytes, maxPreviewDimension = null).bitmap
    }

    private fun decode(bytes: ByteArray, maxPreviewDimension: Int?): DecodedTiffBitmap {
        require(bytes.size >= TIFF_HEADER_SIZE) { "TIFF file is too short." }
        val byteOrder = when (String(bytes, 0, 2, Charsets.US_ASCII)) {
            "II" -> ByteOrder.LITTLE_ENDIAN
            "MM" -> ByteOrder.BIG_ENDIAN
            else -> error("Unsupported TIFF byte order.")
        }
        val buffer = ByteBuffer.wrap(bytes).order(byteOrder)
        require(buffer.getUnsignedShort(2) == TIFF_MAGIC) { "Not a classic TIFF file." }

        val ifdOffset = buffer.getUnsignedInt(4).toInt()
        val tags = readTags(buffer, ifdOffset)
        val width = tags.requireSingle(TAG_IMAGE_WIDTH).toInt()
        val height = tags.requireSingle(TAG_IMAGE_LENGTH).toInt()
        val bitsPerSample = tags.requireValues(TAG_BITS_PER_SAMPLE)
        val compression = tags.valueOrDefault(TAG_COMPRESSION, COMPRESSION_NONE)
        val photometric = tags.valueOrDefault(TAG_PHOTOMETRIC, PHOTOMETRIC_RGB)
        val stripOffsets = tags.requireValues(TAG_STRIP_OFFSETS)
        val stripByteCounts = tags.requireValues(TAG_STRIP_BYTE_COUNTS)
        val samplesPerPixel = tags.valueOrDefault(TAG_SAMPLES_PER_PIXEL, bitsPerSample.size.toLong()).toInt()
        val planarConfiguration = tags.valueOrDefault(TAG_PLANAR_CONFIGURATION, PLANAR_CHUNKY)

        require(compression == COMPRESSION_NONE) { "Only uncompressed TIFF files are supported." }
        require(photometric == PHOTOMETRIC_RGB) { "Only RGB/RGBA TIFF files are supported." }
        require(planarConfiguration == PLANAR_CHUNKY) { "Planar TIFF files are not supported." }
        require(samplesPerPixel == RGB_SAMPLES || samplesPerPixel == RGBA_SAMPLES) {
            "Only RGB and RGBA TIFF files are supported."
        }
        require(bitsPerSample.take(samplesPerPixel).all { it == BITS_PER_SAMPLE_8 }) {
            "Only 8-bit RGB/RGBA TIFF files are supported."
        }
        require(stripOffsets.size == stripByteCounts.size) {
            "TIFF strip offset/count mismatch."
        }

        val totalPixelBytes = width.toLong() * height.toLong() * samplesPerPixel.toLong()
        val totalStripBytes = stripByteCounts.sum()
        require(totalStripBytes >= totalPixelBytes) {
            "TIFF strips contain less image data than expected."
        }
        stripOffsets.zip(stripByteCounts).forEach { (offsetValue, countValue) ->
            val sourceOffset = offsetValue.toInt()
            val count = countValue.toInt()
            require(sourceOffset >= 0 && sourceOffset + count <= bytes.size) {
                "TIFF strip points outside file."
            }
        }

        val sample = maxPreviewDimension
            ?.let { ceil(max(width, height).toDouble() / it.toDouble()).toInt().coerceAtLeast(1) }
            ?: 1
        val outputWidth = ceil(width.toDouble() / sample.toDouble()).toInt().coerceAtLeast(1)
        val outputHeight = ceil(height.toDouble() / sample.toDouble()).toInt().coerceAtLeast(1)
        val pixels = IntArray(outputWidth * outputHeight)
        val cumulativeStripOffsets = LongArray(stripByteCounts.size)
        var cumulative = 0L
        stripByteCounts.forEachIndexed { index, count ->
            cumulativeStripOffsets[index] = cumulative
            cumulative += count
        }

        var currentStripIndex = 0
        fun fileOffsetForPixelByte(pixelByteOffset: Long): Int {
            while (
                currentStripIndex < stripByteCounts.lastIndex &&
                pixelByteOffset >= cumulativeStripOffsets[currentStripIndex] + stripByteCounts[currentStripIndex]
            ) {
                currentStripIndex++
            }
            val offsetWithinStrip = pixelByteOffset - cumulativeStripOffsets[currentStripIndex]
            return (stripOffsets[currentStripIndex] + offsetWithinStrip).toInt()
        }

        var destinationIndex = 0
        for (outY in 0 until outputHeight) {
            val sourceY = min(outY * sample, height - 1)
            for (outX in 0 until outputWidth) {
                val sourceX = min(outX * sample, width - 1)
                val pixelByteOffset = ((sourceY.toLong() * width.toLong()) + sourceX.toLong()) *
                    samplesPerPixel.toLong()
                val sourceIndex = fileOffsetForPixelByte(pixelByteOffset)
                val red = bytes[sourceIndex].toInt() and BYTE_MASK
                val green = bytes[sourceIndex + 1].toInt() and BYTE_MASK
                val blue = bytes[sourceIndex + 2].toInt() and BYTE_MASK
                val alpha = if (samplesPerPixel == RGBA_SAMPLES) {
                    bytes[sourceIndex + 3].toInt() and BYTE_MASK
                } else {
                    BYTE_MASK
                }
                pixels[destinationIndex++] = Color.argb(alpha, red, green, blue)
            }
        }

        return DecodedTiffBitmap(
            bitmap = Bitmap.createBitmap(pixels, outputWidth, outputHeight, Bitmap.Config.ARGB_8888),
            sourceWidth = width,
            sourceHeight = height
        )
    }

    private fun readTags(buffer: ByteBuffer, ifdOffset: Int): Map<Int, List<Long>> {
        require(ifdOffset > 0 && ifdOffset + 2 <= buffer.capacity()) { "Invalid TIFF IFD offset." }
        val entryCount = buffer.getUnsignedShort(ifdOffset)
        val tags = mutableMapOf<Int, List<Long>>()
        repeat(entryCount) { index ->
            val entryOffset = ifdOffset + 2 + index * IFD_ENTRY_SIZE
            require(entryOffset + IFD_ENTRY_SIZE <= buffer.capacity()) { "Invalid TIFF IFD entry." }
            val tag = buffer.getUnsignedShort(entryOffset)
            val type = buffer.getUnsignedShort(entryOffset + 2)
            val count = buffer.getUnsignedInt(entryOffset + 4)
            val valueOffset = entryOffset + 8
            if (tag in REQUIRED_TAGS || tag in OPTIONAL_TAGS) {
                tags[tag] = readValues(buffer, type, count, valueOffset)
            }
        }
        return tags
    }

    private fun readValues(buffer: ByteBuffer, type: Int, count: Long, valueOffset: Int): List<Long> {
        val typeSize = when (type) {
            TYPE_BYTE, TYPE_ASCII -> 1
            TYPE_SHORT -> 2
            TYPE_LONG -> 4
            else -> error("Unsupported TIFF field type: $type")
        }
        val byteCount = count * typeSize
        val dataOffset = if (byteCount <= INLINE_VALUE_BYTES) valueOffset else buffer.getUnsignedInt(valueOffset).toInt()
        require(dataOffset >= 0 && dataOffset + byteCount <= buffer.capacity()) { "TIFF tag points outside file." }

        return List(count.toInt()) { index ->
            val offset = dataOffset + index * typeSize
            when (type) {
                TYPE_BYTE, TYPE_ASCII -> (buffer.get(offset).toInt() and BYTE_MASK).toLong()
                TYPE_SHORT -> buffer.getUnsignedShort(offset).toLong()
                TYPE_LONG -> buffer.getUnsignedInt(offset)
                else -> error("Unsupported TIFF field type: $type")
            }
        }
    }

    private fun Map<Int, List<Long>>.requireValues(tag: Int): List<Long> =
        requireNotNull(this[tag]) { "TIFF missing required tag $tag." }

    private fun Map<Int, List<Long>>.requireSingle(tag: Int): Long =
        requireValues(tag).first()

    private fun Map<Int, List<Long>>.valueOrDefault(tag: Int, default: Long): Long =
        this[tag]?.firstOrNull() ?: default

    private fun ByteBuffer.getUnsignedShort(offset: Int): Int =
        getShort(offset).toInt() and 0xFFFF

    private fun ByteBuffer.getUnsignedInt(offset: Int): Long =
        getInt(offset).toLong() and 0xFFFFFFFFL

    companion object {
        private const val TIFF_HEADER_SIZE = 8
        private const val TIFF_MAGIC = 42
        private const val IFD_ENTRY_SIZE = 12
        private const val INLINE_VALUE_BYTES = 4
        private const val BYTE_MASK = 0xFF

        private const val TYPE_BYTE = 1
        private const val TYPE_ASCII = 2
        private const val TYPE_SHORT = 3
        private const val TYPE_LONG = 4

        private const val TAG_IMAGE_WIDTH = 256
        private const val TAG_IMAGE_LENGTH = 257
        private const val TAG_BITS_PER_SAMPLE = 258
        private const val TAG_COMPRESSION = 259
        private const val TAG_PHOTOMETRIC = 262
        private const val TAG_STRIP_OFFSETS = 273
        private const val TAG_SAMPLES_PER_PIXEL = 277
        private const val TAG_STRIP_BYTE_COUNTS = 279
        private const val TAG_PLANAR_CONFIGURATION = 284

        private val REQUIRED_TAGS = setOf(
            TAG_IMAGE_WIDTH,
            TAG_IMAGE_LENGTH,
            TAG_BITS_PER_SAMPLE,
            TAG_STRIP_OFFSETS,
            TAG_STRIP_BYTE_COUNTS
        )
        private val OPTIONAL_TAGS = setOf(
            TAG_COMPRESSION,
            TAG_PHOTOMETRIC,
            TAG_SAMPLES_PER_PIXEL,
            TAG_PLANAR_CONFIGURATION
        )

        private const val COMPRESSION_NONE = 1L
        private const val PHOTOMETRIC_RGB = 2L
        private const val PLANAR_CHUNKY = 1L
        private const val BITS_PER_SAMPLE_8 = 8L
        private const val RGB_SAMPLES = 3
        private const val RGBA_SAMPLES = 4
    }
}
