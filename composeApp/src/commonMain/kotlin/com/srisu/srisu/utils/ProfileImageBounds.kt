package com.srisu.srisu.utils

/** Read dimensions without decoding pixels. Server decoding remains authoritative. */
fun profileImageDimensionsValid(bytes: ByteArray): Boolean {
    fun u(i: Int) = bytes[i].toInt() and 255
    fun be16(i: Int) = (u(i) shl 8) or u(i + 1)
    fun le24(i: Int) = u(i) or (u(i + 1) shl 8) or (u(i + 2) shl 16)
    fun valid(w: Long, h: Long) = w in 1..8192 && h in 1..8192 && w * h <= 20_000_000
    if (bytes.size < 24 || bytes.size > 5 * 1024 * 1024) return false
    return try {
        if (u(0) == 137 && bytes.copyOfRange(1, 4).decodeToString() == "PNG") {
            fun be32(i: Int) = (be16(i).toLong() shl 16) or be16(i + 2).toLong()
            valid(be32(16), be32(20))
        } else if (u(0) == 255 && u(1) == 216) {
            var i = 2
            var result = false
            while (i + 8 < bytes.size) {
                if (u(i) != 255) break
                while (i < bytes.size && u(i) == 255) i++
                val marker = u(i++)
                if (marker in 0xC0..0xCF && marker !in listOf(0xC4, 0xC8, 0xCC)) {
                    result = valid(be16(i + 5).toLong(), be16(i + 3).toLong()); break
                }
                if (marker == 0xDA || marker == 0xD9) break
                val length = be16(i)
                if (length < 2) break
                i += length
            }
            result
        } else if (bytes.copyOfRange(0, 4).decodeToString() == "RIFF" && bytes.copyOfRange(8, 12).decodeToString() == "WEBP") {
            when (bytes.copyOfRange(12, 16).decodeToString()) {
                "VP8X" -> valid((1 + le24(24)).toLong(), (1 + le24(27)).toLong())
                "VP8 " -> valid(((u(26) or (u(27) shl 8)) and 0x3fff).toLong(), ((u(28) or (u(29) shl 8)) and 0x3fff).toLong())
                "VP8L" -> valid((1 + (u(21) or ((u(22) and 63) shl 8))).toLong(), (1 + ((u(22) shr 6) or (u(23) shl 2) or ((u(24) and 15) shl 10))).toLong())
                else -> false
            }
        } else false
    } catch (_: IndexOutOfBoundsException) { false }
}
