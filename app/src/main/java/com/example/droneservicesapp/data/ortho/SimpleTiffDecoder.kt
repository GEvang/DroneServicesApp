package com.example.droneservicesapp.data.ortho

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
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
        return inputStream.use { stream ->
            if (stream is FileInputStream) {
                val mapped = mapReadOnly(stream.channel)
                if (mapped != null) return@use decode(mapped, maxPreviewDimension)
            }

            val temporaryFile = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX)
            try {
                temporaryFile.outputStream().buffered().use { output -> stream.copyTo(output) }
                RandomAccessFile(temporaryFile, "r").use { file ->
                    val mapped = requireNotNull(mapReadOnly(file.channel)) {
                        "Could not map the TIFF file for reading."
                    }
                    decode(mapped, maxPreviewDimension)
                }
            } finally {
                temporaryFile.delete()
            }
        }
    }

    fun decode(bytes: ByteArray): Bitmap {
        return decode(ByteBuffer.wrap(bytes), maxPreviewDimension = null).bitmap
    }

    private fun decode(source: ByteBuffer, maxPreviewDimension: Int?): DecodedTiffBitmap {
        require(source.capacity() >= TIFF_HEADER_SIZE) { "TIFF file is too short." }
        val byteOrder = when (source.get(0).toInt() to source.get(1).toInt()) {
            'I'.code to 'I'.code -> ByteOrder.LITTLE_ENDIAN
            'M'.code to 'M'.code -> ByteOrder.BIG_ENDIAN
            else -> error("Unsupported TIFF byte order.")
        }
        val buffer = source.duplicate().order(byteOrder)
        require(buffer.getUnsignedShort(2) == TIFF_MAGIC) { "Not a classic TIFF file." }

        val ifdOffset = buffer.getUnsignedInt(4).toInt()
        val tags = readTags(buffer, ifdOffset)
        val width = tags.requireSingle(TAG_IMAGE_WIDTH).toInt()
        val height = tags.requireSingle(TAG_IMAGE_LENGTH).toInt()
        val bitsPerSample = tags.requireValues(TAG_BITS_PER_SAMPLE)
        val compression = tags.valueOrDefault(TAG_COMPRESSION, COMPRESSION_NONE)
        val photometric = tags.valueOrDefault(TAG_PHOTOMETRIC, PHOTOMETRIC_RGB)
        val samplesPerPixel = tags.valueOrDefault(TAG_SAMPLES_PER_PIXEL, bitsPerSample.size.toLong()).toInt()
        val planarConfiguration = tags.valueOrDefault(TAG_PLANAR_CONFIGURATION, PLANAR_CHUNKY)
        val predictor = tags.valueOrDefault(TAG_PREDICTOR, PREDICTOR_NONE)

        require(compression == COMPRESSION_NONE || compression == COMPRESSION_LZW) {
            "Only uncompressed and LZW-compressed TIFF files are supported."
        }
        require(photometric == PHOTOMETRIC_RGB) { "Only RGB/RGBA TIFF files are supported." }
        require(planarConfiguration == PLANAR_CHUNKY) { "Planar TIFF files are not supported." }
        require(predictor == PREDICTOR_NONE || predictor == PREDICTOR_HORIZONTAL) {
            "Unsupported TIFF predictor $predictor."
        }
        require(samplesPerPixel == RGB_SAMPLES || samplesPerPixel == RGBA_SAMPLES) {
            "Only RGB and RGBA TIFF files are supported."
        }
        require(bitsPerSample.take(samplesPerPixel).all { it == BITS_PER_SAMPLE_8 }) {
            "Only 8-bit RGB/RGBA TIFF files are supported."
        }
        val sample = maxPreviewDimension
            ?.let { ceil(max(width, height).toDouble() / it.toDouble()).toInt().coerceAtLeast(1) }
            ?: 1
        val outputWidth = ceil(width.toDouble() / sample.toDouble()).toInt().coerceAtLeast(1)
        val outputHeight = ceil(height.toDouble() / sample.toDouble()).toInt().coerceAtLeast(1)
        val pixels = IntArray(outputWidth * outputHeight)
        val blocks = buildImageBlocks(tags, width, height)
        blocks.forEach { block ->
            val expectedByteCount = block.storageWidth.toLong() * block.storageHeight * samplesPerPixel
            require(expectedByteCount <= Int.MAX_VALUE) { "TIFF block is too large to decode." }
            val expectedBytes = expectedByteCount.toInt()
            if (compression == COMPRESSION_NONE) {
                require(block.byteCount >= expectedByteCount) {
                    "TIFF block contains less image data than expected."
                }
                require(
                    block.fileOffset >= 0 &&
                        block.fileOffset + expectedByteCount <= source.capacity().toLong()
                ) { "TIFF block points outside file." }
                copyUncompressedBlockToPreview(
                    source = source,
                    block = block,
                    samplesPerPixel = samplesPerPixel,
                    predictor = predictor,
                    sample = sample,
                    outputWidth = outputWidth,
                    pixels = pixels
                )
                return@forEach
            }

            val encoded = readBlock(source, block.fileOffset, block.byteCount)
            val decoded = when (compression) {
                COMPRESSION_LZW -> TiffLzwDecoder.decode(encoded, expectedBytes)
                else -> error("Unsupported TIFF compression $compression.")
            }
            if (predictor == PREDICTOR_HORIZONTAL) {
                undoHorizontalPredictor(decoded, block.storageWidth, block.storageHeight, samplesPerPixel)
            }
            copyBlockToPreview(
                decoded = decoded,
                block = block,
                samplesPerPixel = samplesPerPixel,
                sample = sample,
                outputWidth = outputWidth,
                pixels = pixels
            )
        }

        return DecodedTiffBitmap(
            bitmap = Bitmap.createBitmap(pixels, outputWidth, outputHeight, Bitmap.Config.ARGB_8888),
            sourceWidth = width,
            sourceHeight = height
        )
    }

    private fun buildImageBlocks(
        tags: Map<Int, List<Long>>,
        imageWidth: Int,
        imageHeight: Int
    ): List<ImageBlock> {
        val tileOffsets = tags[TAG_TILE_OFFSETS]
        val tileByteCounts = tags[TAG_TILE_BYTE_COUNTS]
        if (tileOffsets != null || tileByteCounts != null) {
            requireNotNull(tileOffsets) { "TIFF missing required tile-offset tag $TAG_TILE_OFFSETS." }
            requireNotNull(tileByteCounts) { "TIFF missing required tile-byte-count tag $TAG_TILE_BYTE_COUNTS." }
            require(tileOffsets.size == tileByteCounts.size) { "TIFF tile offset/count mismatch." }
            val tileWidth = tags.requireSingle(TAG_TILE_WIDTH).toInt()
            val tileHeight = tags.requireSingle(TAG_TILE_LENGTH).toInt()
            require(tileWidth > 0 && tileHeight > 0) { "Invalid TIFF tile dimensions." }
            val tilesAcross = ceil(imageWidth.toDouble() / tileWidth).toInt()
            val tilesDown = ceil(imageHeight.toDouble() / tileHeight).toInt()
            require(tileOffsets.size >= tilesAcross * tilesDown) { "TIFF contains fewer tiles than expected." }
            return List(tilesAcross * tilesDown) { index ->
                val tileX = (index % tilesAcross) * tileWidth
                val tileY = (index / tilesAcross) * tileHeight
                ImageBlock(
                    fileOffset = tileOffsets[index],
                    byteCount = tileByteCounts[index],
                    sourceX = tileX,
                    sourceY = tileY,
                    sourceWidth = min(tileWidth, imageWidth - tileX),
                    sourceHeight = min(tileHeight, imageHeight - tileY),
                    storageWidth = tileWidth,
                    storageHeight = tileHeight
                )
            }
        }

        val stripOffsets = requireNotNull(tags[TAG_STRIP_OFFSETS]) {
            "TIFF has neither strip tag $TAG_STRIP_OFFSETS nor tile tag $TAG_TILE_OFFSETS."
        }
        val stripByteCounts = requireNotNull(tags[TAG_STRIP_BYTE_COUNTS]) {
            "TIFF missing required strip-byte-count tag $TAG_STRIP_BYTE_COUNTS."
        }
        require(stripOffsets.size == stripByteCounts.size) { "TIFF strip offset/count mismatch." }
        val rowsPerStrip = tags.valueOrDefault(TAG_ROWS_PER_STRIP, imageHeight.toLong()).toInt()
        require(rowsPerStrip > 0) { "Invalid TIFF rows-per-strip value." }
        val stripCount = ceil(imageHeight.toDouble() / rowsPerStrip).toInt()
        require(stripOffsets.size >= stripCount) { "TIFF contains fewer strips than expected." }
        return List(stripCount) { index ->
            val sourceY = index * rowsPerStrip
            val rows = min(rowsPerStrip, imageHeight - sourceY)
            ImageBlock(
                fileOffset = stripOffsets[index],
                byteCount = stripByteCounts[index],
                sourceX = 0,
                sourceY = sourceY,
                sourceWidth = imageWidth,
                sourceHeight = rows,
                storageWidth = imageWidth,
                storageHeight = rows
            )
        }
    }

    private fun readBlock(source: ByteBuffer, offsetValue: Long, countValue: Long): ByteArray {
        require(offsetValue >= 0 && countValue >= 0 && offsetValue + countValue <= source.capacity().toLong()) {
            "TIFF block points outside file."
        }
        require(countValue <= Int.MAX_VALUE) { "TIFF block is too large." }
        return ByteArray(countValue.toInt()).also { block ->
            source.duplicate().apply {
                position(offsetValue.toInt())
                get(block)
            }
        }
    }

    private fun copyUncompressedBlockToPreview(
        source: ByteBuffer,
        block: ImageBlock,
        samplesPerPixel: Int,
        predictor: Long,
        sample: Int,
        outputWidth: Int,
        pixels: IntArray
    ) {
        val firstOutputX = ceil(block.sourceX.toDouble() / sample).toInt()
        val lastOutputX = (block.sourceX + block.sourceWidth - 1) / sample
        val firstOutputY = ceil(block.sourceY.toDouble() / sample).toInt()
        val lastOutputY = (block.sourceY + block.sourceHeight - 1) / sample
        val rowBytes = block.storageWidth * samplesPerPixel
        val decodedRow = ByteArray(rowBytes)
        val reader = source.duplicate()

        for (outY in firstOutputY..lastOutputY) {
            val localY = outY * sample - block.sourceY
            val rowOffset = block.fileOffset + localY.toLong() * rowBytes
            reader.position(rowOffset.toInt())
            reader.get(decodedRow)
            if (predictor == PREDICTOR_HORIZONTAL) {
                undoHorizontalPredictor(decodedRow, block.storageWidth, 1, samplesPerPixel)
            }
            for (outX in firstOutputX..lastOutputX) {
                val localX = outX * sample - block.sourceX
                val sourceIndex = localX * samplesPerPixel
                pixels[outY * outputWidth + outX] = decodedRow.toColor(sourceIndex, samplesPerPixel)
            }
        }
    }

    private fun ByteArray.toColor(sourceIndex: Int, samplesPerPixel: Int): Int {
        val red = this[sourceIndex].toInt() and BYTE_MASK
        val green = this[sourceIndex + 1].toInt() and BYTE_MASK
        val blue = this[sourceIndex + 2].toInt() and BYTE_MASK
        val alpha = if (samplesPerPixel == RGBA_SAMPLES) {
            this[sourceIndex + 3].toInt() and BYTE_MASK
        } else {
            BYTE_MASK
        }
        return Color.argb(alpha, red, green, blue)
    }

    private fun mapReadOnly(channel: FileChannel): ByteBuffer? {
        val size = try {
            channel.size()
        } catch (_: Exception) {
            return null
        }
        if (size <= 0L) return null
        require(size <= Int.MAX_VALUE) { "TIFF files larger than 2 GB are not supported." }
        return try {
            channel.map(FileChannel.MapMode.READ_ONLY, 0L, size)
        } catch (_: Exception) {
            null
        }
    }

    private fun undoHorizontalPredictor(data: ByteArray, width: Int, height: Int, samplesPerPixel: Int) {
        val rowBytes = width * samplesPerPixel
        repeat(height) { row ->
            val rowOffset = row * rowBytes
            for (index in samplesPerPixel until rowBytes) {
                val position = rowOffset + index
                data[position] = (data[position].toInt() + data[position - samplesPerPixel].toInt()).toByte()
            }
        }
    }

    private fun copyBlockToPreview(
        decoded: ByteArray,
        block: ImageBlock,
        samplesPerPixel: Int,
        sample: Int,
        outputWidth: Int,
        pixels: IntArray
    ) {
        val firstOutputX = ceil(block.sourceX.toDouble() / sample).toInt()
        val lastOutputX = (block.sourceX + block.sourceWidth - 1) / sample
        val firstOutputY = ceil(block.sourceY.toDouble() / sample).toInt()
        val lastOutputY = (block.sourceY + block.sourceHeight - 1) / sample
        for (outY in firstOutputY..lastOutputY) {
            val localY = outY * sample - block.sourceY
            for (outX in firstOutputX..lastOutputX) {
                val localX = outX * sample - block.sourceX
                val sourceIndex = (localY * block.storageWidth + localX) * samplesPerPixel
                val red = decoded[sourceIndex].toInt() and BYTE_MASK
                val green = decoded[sourceIndex + 1].toInt() and BYTE_MASK
                val blue = decoded[sourceIndex + 2].toInt() and BYTE_MASK
                val alpha = if (samplesPerPixel == RGBA_SAMPLES) {
                    decoded[sourceIndex + 3].toInt() and BYTE_MASK
                } else {
                    BYTE_MASK
                }
                pixels[outY * outputWidth + outX] = Color.argb(alpha, red, green, blue)
            }
        }
    }

    private data class ImageBlock(
        val fileOffset: Long,
        val byteCount: Long,
        val sourceX: Int,
        val sourceY: Int,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val storageWidth: Int,
        val storageHeight: Int
    )

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
        private const val TEMP_FILE_PREFIX = "drone_tiff_"
        private const val TEMP_FILE_SUFFIX = ".tmp"
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
        private const val TAG_ROWS_PER_STRIP = 278
        private const val TAG_STRIP_BYTE_COUNTS = 279
        private const val TAG_PLANAR_CONFIGURATION = 284
        private const val TAG_PREDICTOR = 317
        private const val TAG_TILE_WIDTH = 322
        private const val TAG_TILE_LENGTH = 323
        private const val TAG_TILE_OFFSETS = 324
        private const val TAG_TILE_BYTE_COUNTS = 325

        private val REQUIRED_TAGS = setOf(
            TAG_IMAGE_WIDTH,
            TAG_IMAGE_LENGTH,
            TAG_BITS_PER_SAMPLE
        )
        private val OPTIONAL_TAGS = setOf(
            TAG_COMPRESSION,
            TAG_PHOTOMETRIC,
            TAG_STRIP_OFFSETS,
            TAG_SAMPLES_PER_PIXEL,
            TAG_ROWS_PER_STRIP,
            TAG_STRIP_BYTE_COUNTS,
            TAG_PLANAR_CONFIGURATION,
            TAG_PREDICTOR,
            TAG_TILE_WIDTH,
            TAG_TILE_LENGTH,
            TAG_TILE_OFFSETS,
            TAG_TILE_BYTE_COUNTS
        )

        private const val COMPRESSION_NONE = 1L
        private const val COMPRESSION_LZW = 5L
        private const val PHOTOMETRIC_RGB = 2L
        private const val PLANAR_CHUNKY = 1L
        private const val PREDICTOR_NONE = 1L
        private const val PREDICTOR_HORIZONTAL = 2L
        private const val BITS_PER_SAMPLE_8 = 8L
        private const val RGB_SAMPLES = 3
        private const val RGBA_SAMPLES = 4
    }
}
