package com.example.droneservicesapp.data.ortho

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TiffLzwDecoderTest {
    @Test
    fun decodesTiffLzwDictionaryAndRepeatedPrefixCode() {
        val encoded = packNineBitCodes(256, 65, 66, 258, 260, 257)

        val decoded = TiffLzwDecoder.decode(encoded, 7)

        assertArrayEquals("ABABABA".toByteArray(), decoded)
    }

    private fun packNineBitCodes(vararg codes: Int): ByteArray {
        val bitCount = codes.size * 9
        val bytes = ByteArray((bitCount + 7) / 8)
        var bitOffset = 0
        codes.forEach { code ->
            for (bit in 8 downTo 0) {
                if ((code and (1 shl bit)) != 0) {
                    val byteIndex = bitOffset ushr 3
                    val bitIndex = 7 - (bitOffset and 7)
                    bytes[byteIndex] = (bytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                }
                bitOffset++
            }
        }
        return bytes
    }
}
