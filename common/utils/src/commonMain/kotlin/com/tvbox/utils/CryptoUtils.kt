package com.tvbox.utils

import kotlin.experimental.and
import kotlin.experimental.or

/**
 * 跨平台加密工具类
 * 提供 MD5、Base64、Hex 编码等通用加密/编码功能
 */
object CryptoUtils {

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    /**
     * 计算字符串的 MD5 哈希值
     * @param input 输入字符串
     * @return 32位十六进制 MD5 哈希
     */
    fun md5(input: String): String {
        val bytes = input.encodeToByteArray()
        return md5Bytes(bytes)
    }

    /**
     * 计算字节数组的 MD5 哈希值
     */
    fun md5Bytes(bytes: ByteArray): String {
        val digest = md5Digest(bytes)
        return bytesToHex(digest)
    }

    /**
     * Base64 编码
     */
    fun base64Encode(input: String): String {
        return base64EncodeBytes(input.encodeToByteArray())
    }

    /**
     * Base64 解码
     */
    fun base64Decode(input: String): String {
        return base64DecodeBytes(input).decodeToString()
    }

    /**
     * Base64 编码（字节数组）
     */
    fun base64EncodeBytes(bytes: ByteArray): String {
        return encodeBase64(bytes)
    }

    /**
     * Base64 解码（返回字节数组）
     */
    fun base64DecodeBytes(input: String): ByteArray {
        return decodeBase64(input)
    }

    /**
     * 字节数组转十六进制字符串
     */
    fun bytesToHex(bytes: ByteArray): String {
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            result.append(HEX_CHARS[v ushr 4])
            result.append(HEX_CHARS[v and 0x0F])
        }
        return result.toString()
    }

    /**
     * 十六进制字符串转字节数组
     */
    fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.removePrefix("0x").lowercase()
        val len = cleanHex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4)
                + Character.digit(cleanHex[i + 1], 16)).toByte()
        }
        return data
    }

    // ===== 内部实现 =====

    private fun md5Digest(input: ByteArray): ByteArray {
        val s11 = 7
        val s12 = 12
        val s13 = 17
        val s14 = 22
        val s21 = 5
        val s22 = 9
        val s23 = 14
        val s24 = 20
        val s31 = 4
        val s32 = 11
        val s33 = 16
        val s34 = 23
        val s41 = 6
        val s42 = 10
        val s43 = 15
        val s44 = 21

        val digest = IntArray(4)
        digest[0] = 0x67452301
        digest[1] = 0xEFCDAB89.toInt()
        digest[2] = 0x98BADCFE.toInt()
        digest[3] = 0x10325476

        val padded = padMessage(input)
        var i = 0
        while (i < padded.size) {
            val a0 = digest[0]
            val b0 = digest[1]
            val c0 = digest[2]
            val d0 = digest[3]

            val m = IntArray(16)
            for (j in 0 until 16) {
                val offset = i + j * 4
                m[j] = (padded[offset].toInt() and 0xFF) or
                    ((padded[offset + 1].toInt() and 0xFF) shl 8) or
                    ((padded[offset + 2].toInt() and 0xFF) shl 16) or
                    ((padded[offset + 3].toInt() and 0xFF) shl 24)
            }

            digest[0] = ff(digest[0], digest[1], digest[2], digest[3], m[0], s11, 0xd76aa478.toInt())
            digest[3] = ff(digest[3], digest[0], digest[1], digest[2], m[1], s12, 0xe8c7b756.toInt())
            digest[2] = ff(digest[2], digest[3], digest[0], digest[1], m[2], s13, 0x242070db)
            digest[1] = ff(digest[1], digest[2], digest[3], digest[0], m[3], s14, 0xc1bdceee.toInt())
            digest[0] = ff(digest[0], digest[1], digest[2], digest[3], m[4], s11, 0xf57c0faf.toInt())
            digest[3] = ff(digest[3], digest[0], digest[1], digest[2], m[5], s12, 0x4787c62a)
            digest[2] = ff(digest[2], digest[3], digest[0], digest[1], m[6], s13, 0xa8304613.toInt())
            digest[1] = ff(digest[1], digest[2], digest[3], digest[0], m[7], s14, 0xfd469501.toInt())
            digest[0] = ff(digest[0], digest[1], digest[2], digest[3], m[8], s11, 0x698098d8)
            digest[3] = ff(digest[3], digest[0], digest[1], digest[2], m[9], s12, 0x8b44f7af.toInt())
            digest[2] = ff(digest[2], digest[3], digest[0], digest[1], m[10], s13, 0xffff5bb1.toInt())
            digest[1] = ff(digest[1], digest[2], digest[3], digest[0], m[11], s14, 0x895cd7be.toInt())
            digest[0] = ff(digest[0], digest[1], digest[2], digest[3], m[12], s11, 0x6b901122)
            digest[3] = ff(digest[3], digest[0], digest[1], digest[2], m[13], s12, 0xfd987193.toInt())
            digest[2] = ff(digest[2], digest[3], digest[0], digest[1], m[14], s13, 0xa679438e.toInt())
            digest[1] = ff(digest[1], digest[2], digest[3], digest[0], m[15], s14, 0x49b40821)

            digest[0] = gg(digest[0], digest[1], digest[2], digest[3], m[1], s21, 0xf61e2562.toInt())
            digest[3] = gg(digest[3], digest[0], digest[1], digest[2], m[6], s22, 0xc040b340.toInt())
            digest[2] = gg(digest[2], digest[3], digest[0], digest[1], m[11], s23, 0x265e5a51)
            digest[1] = gg(digest[1], digest[2], digest[3], digest[0], m[0], s24, 0xe9b6c7aa.toInt())
            digest[0] = gg(digest[0], digest[1], digest[2], digest[3], m[5], s21, 0xd62f105d.toInt())
            digest[3] = gg(digest[3], digest[0], digest[1], digest[2], m[10], s22, 0x02441453)
            digest[2] = gg(digest[2], digest[3], digest[0], digest[1], m[15], s23, 0xd8a1e681.toInt())
            digest[1] = gg(digest[1], digest[2], digest[3], digest[0], m[4], s24, 0xe7d3fbc8.toInt())
            digest[0] = gg(digest[0], digest[1], digest[2], digest[3], m[9], s21, 0x21e1cde6)
            digest[3] = gg(digest[3], digest[0], digest[1], digest[2], m[14], s22, 0xc33707d6.toInt())
            digest[2] = gg(digest[2], digest[3], digest[0], digest[1], m[3], s23, 0xf4d50d87.toInt())
            digest[1] = gg(digest[1], digest[2], digest[3], digest[0], m[8], s24, 0x455a14ed)
            digest[0] = gg(digest[0], digest[1], digest[2], digest[3], m[13], s21, 0xa9e3e905.toInt())
            digest[3] = gg(digest[3], digest[0], digest[1], digest[2], m[2], s22, 0xfcefa3f8.toInt())
            digest[2] = gg(digest[2], digest[3], digest[0], digest[1], m[7], s23, 0x676f02d9)
            digest[1] = gg(digest[1], digest[2], digest[3], digest[0], m[12], s24, 0x8d2a4c8a.toInt())

            digest[0] = hh(digest[0], digest[1], digest[2], digest[3], m[5], s31, 0xfffa3942.toInt())
            digest[3] = hh(digest[3], digest[0], digest[1], digest[2], m[8], s32, 0x8771f681.toInt())
            digest[2] = hh(digest[2], digest[3], digest[0], digest[1], m[11], s33, 0x6d9d6122)
            digest[1] = hh(digest[1], digest[2], digest[3], digest[0], m[14], s34, 0xfde5380c.toInt())
            digest[0] = hh(digest[0], digest[1], digest[2], digest[3], m[1], s31, 0xa4beea44.toInt())
            digest[3] = hh(digest[3], digest[0], digest[1], digest[2], m[4], s32, 0x4bdecfa9)
            digest[2] = hh(digest[2], digest[3], digest[0], digest[1], m[7], s33, 0xf6bb4b60.toInt())
            digest[1] = hh(digest[1], digest[2], digest[3], digest[0], m[10], s34, 0xbebfbc70.toInt())
            digest[0] = hh(digest[0], digest[1], digest[2], digest[3], m[13], s31, 0x289b7ec6)
            digest[3] = hh(digest[3], digest[0], digest[1], digest[2], m[0], s32, 0xeaa127fa.toInt())
            digest[2] = hh(digest[2], digest[3], digest[0], digest[1], m[3], s33, 0xd4ef3085.toInt())
            digest[1] = hh(digest[1], digest[2], digest[3], digest[0], m[6], s34, 0x04881d05)
            digest[0] = hh(digest[0], digest[1], digest[2], digest[3], m[9], s31, 0xd9d4d039.toInt())
            digest[3] = hh(digest[3], digest[0], digest[1], digest[2], m[12], s32, 0xe6db99e5.toInt())
            digest[2] = hh(digest[2], digest[3], digest[0], digest[1], m[15], s33, 0x1fa27cf8)
            digest[1] = hh(digest[1], digest[2], digest[3], digest[0], m[2], s34, 0xc4ac5665.toInt())

            digest[0] = ii(digest[0], digest[1], digest[2], digest[3], m[0], s41, 0xf4292244.toInt())
            digest[3] = ii(digest[3], digest[0], digest[1], digest[2], m[7], s42, 0x432aff97)
            digest[2] = ii(digest[2], digest[3], digest[0], digest[1], m[14], s43, 0xab9423a7.toInt())
            digest[1] = ii(digest[1], digest[2], digest[3], digest[0], m[5], s44, 0xfc93a039.toInt())
            digest[0] = ii(digest[0], digest[1], digest[2], digest[3], m[12], s41, 0x655b59c3)
            digest[3] = ii(digest[3], digest[0], digest[1], digest[2], m[3], s42, 0x8f0ccc92.toInt())
            digest[2] = ii(digest[2], digest[3], digest[0], digest[1], m[10], s43, 0xffeff47d.toInt())
            digest[1] = ii(digest[1], digest[2], digest[3], digest[0], m[1], s44, 0x85845dd1.toInt())
            digest[0] = ii(digest[0], digest[1], digest[2], digest[3], m[8], s41, 0x6fa87e4f.toInt())
            digest[3] = ii(digest[3], digest[0], digest[1], digest[2], m[15], s42, 0xfe2ce6e0.toInt())
            digest[2] = ii(digest[2], digest[3], digest[0], digest[1], m[6], s43, 0xa3014314.toInt())
            digest[1] = ii(digest[1], digest[2], digest[3], digest[0], m[13], s44, 0x4e0811a1)
            digest[0] = ii(digest[0], digest[1], digest[2], digest[3], m[4], s41, 0xf7537e82.toInt())
            digest[3] = ii(digest[3], digest[0], digest[1], digest[2], m[11], s42, 0xbd3af235.toInt())
            digest[2] = ii(digest[2], digest[3], digest[0], digest[1], m[2], s43, 0x2ad7d2bb)
            digest[1] = ii(digest[1], digest[2], digest[3], digest[0], m[9], s44, 0xeb86d391.toInt())

            digest[0] += a0
            digest[1] += b0
            digest[2] += c0
            digest[3] += d0

            i += 64
        }

        val result = ByteArray(16)
        for (j in 0 until 4) {
            result[j * 4] = (digest[j] and 0xFF).toByte()
            result[j * 4 + 1] = ((digest[j] ushr 8) and 0xFF).toByte()
            result[j * 4 + 2] = ((digest[j] ushr 16) and 0xFF).toByte()
            result[j * 4 + 3] = ((digest[j] ushr 24) and 0xFF).toByte()
        }
        return result
    }

    private fun padMessage(input: ByteArray): ByteArray {
        val originalLength = input.size
        val bitLength = originalLength.toLong() * 8
        val paddedLength = ((originalLength + 8) / 64 + 1) * 64
        val padded = ByteArray(paddedLength)
        input.copyInto(padded)
        padded[originalLength] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[paddedLength - 8 + i] = ((bitLength ushr (i * 8)) and 0xFF).toByte()
        }
        return padded
    }

    private fun ff(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int, t: Int): Int {
        return b + (leftRotate(a + ((b and c) or (b.inv() and d)) + x + t, s))
    }

    private fun gg(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int, t: Int): Int {
        return b + (leftRotate(a + ((b and d) or (c and d.inv())) + x + t, s))
    }

    private fun hh(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int, t: Int): Int {
        return b + (leftRotate(a + (b xor c xor d) + x + t, s))
    }

    private fun ii(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int, t: Int): Int {
        return b + (leftRotate(a + (c xor (b or d.inv())) + x + t, s))
    }

    private fun leftRotate(x: Int, n: Int): Int {
        return (x shl n) or (x ushr (32 - n))
    }

    private val BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private fun encodeBase64(input: ByteArray): String {
        val result = StringBuilder()
        var i = 0
        while (i < input.size) {
            val b0 = input[i].toInt() and 0xFF
            val b1 = if (i + 1 < input.size) input[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < input.size) input[i + 2].toInt() and 0xFF else -1

            result.append(BASE64_CHARS[b0 ushr 2])
            result.append(BASE64_CHARS[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 ushr 4 else 0)])

            if (b1 >= 0) {
                result.append(BASE64_CHARS[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 ushr 6 else 0)])
            } else {
                result.append('=')
            }

            if (b2 >= 0) {
                result.append(BASE64_CHARS[b2 and 0x3F])
            } else {
                result.append('=')
            }
            i += 3
        }
        return result.toString()
    }

    private fun decodeBase64(input: String): ByteArray {
        val cleanInput = input.filter { it != '\n' && it != '\r' && it != ' ' }
        val padding = cleanInput.count { it == '=' }
        val length = cleanInput.length
        val outputLength = (length * 3) / 4 - padding
        val output = ByteArray(outputLength)
        var pos = 0
        var i = 0
        while (i < length) {
            val c0 = BASE64_CHARS.indexOf(cleanInput[i])
            val c1 = BASE64_CHARS.indexOf(cleanInput[i + 1])
            val c2 = if (i + 2 < length) BASE64_CHARS.indexOf(cleanInput[i + 2]) else -1
            val c3 = if (i + 3 < length) BASE64_CHARS.indexOf(cleanInput[i + 3]) else -1

            output[pos++] = ((c0 shl 2) or (c1 ushr 4)).toByte()
            if (c2 >= 0 && pos < outputLength) {
                output[pos++] = (((c1 and 0x0F) shl 4) or (c2 ushr 2)).toByte()
            }
            if (c3 >= 0 && pos < outputLength) {
                output[pos++] = (((c2 and 0x03) shl 6) or c3).toByte()
            }
            i += 4
        }
        return output
    }
}
