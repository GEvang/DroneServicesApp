package com.example.droneservicesapp.data.ortho

import java.io.ByteArrayOutputStream

/** TIFF 6.0 LZW decoder (MSB-first codes with early code-width changes). */
internal object TiffLzwDecoder {
    private const val CLEAR_CODE = 256
    private const val END_OF_INFORMATION_CODE = 257
    private const val FIRST_DICTIONARY_CODE = 258
    private const val MAX_DICTIONARY_SIZE = 4096

    fun decode(encoded: ByteArray, expectedSize: Int): ByteArray {
        val output = ByteArrayOutputStream(expectedSize.coerceAtLeast(32))
        val prefixes = IntArray(MAX_DICTIONARY_SIZE)
        val suffixes = ByteArray(MAX_DICTIONARY_SIZE)
        val stack = ByteArray(MAX_DICTIONARY_SIZE)
        val bits = BitReader(encoded)
        var codeWidth = 9
        var nextCode = FIRST_DICTIONARY_CODE
        var previousCode = -1

        fun resetDictionary() {
            codeWidth = 9
            nextCode = FIRST_DICTIONARY_CODE
            previousCode = -1
        }

        while (output.size() < expectedSize) {
            val code = bits.read(codeWidth)
            require(code >= 0) { "LZW-compressed TIFF block ended before $expectedSize bytes were decoded." }
            if (code == CLEAR_CODE) {
                resetDictionary()
                continue
            }
            if (code == END_OF_INFORMATION_CODE) break
            require(code < nextCode || (code == nextCode && previousCode >= 0)) {
                "Invalid TIFF LZW code $code."
            }

            val repeatsPreviousPrefix = code == nextCode
            var stackSize = 0
            var cursor = if (repeatsPreviousPrefix) previousCode else code
            while (cursor >= 256) {
                require(cursor < nextCode && stackSize < stack.size) { "Invalid TIFF LZW dictionary chain." }
                stack[stackSize++] = suffixes[cursor]
                cursor = prefixes[cursor]
            }
            val firstByte = cursor
            stack[stackSize++] = firstByte.toByte()
            for (index in stackSize - 1 downTo 0) output.write(stack[index].toInt())
            if (repeatsPreviousPrefix) output.write(firstByte)

            if (previousCode >= 0 && nextCode < MAX_DICTIONARY_SIZE) {
                prefixes[nextCode] = previousCode
                suffixes[nextCode] = firstByte.toByte()
                nextCode++
                if (codeWidth < 12 && nextCode == (1 shl codeWidth) - 1) codeWidth++
            }
            previousCode = code
        }

        val decoded = output.toByteArray()
        require(decoded.size >= expectedSize) {
            "LZW-compressed TIFF block decoded to ${decoded.size} bytes; expected $expectedSize."
        }
        return if (decoded.size == expectedSize) decoded else decoded.copyOf(expectedSize)
    }

    private class BitReader(private val bytes: ByteArray) {
        private var bitOffset = 0

        fun read(width: Int): Int {
            if (bitOffset + width > bytes.size * 8) return -1
            var value = 0
            repeat(width) {
                val byteIndex = bitOffset ushr 3
                val bitIndex = 7 - (bitOffset and 7)
                value = (value shl 1) or ((bytes[byteIndex].toInt() ushr bitIndex) and 1)
                bitOffset++
            }
            return value
        }
    }
}
