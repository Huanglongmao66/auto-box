package com.tvbox.utils

import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * JVM/Android 平台编码工具实现
 *
 * 利用 java.nio.charset 支持全量字符集（GBK/GB2312/Big5 等）。
 */
actual object EncodingUtils {

    actual fun decode(bytes: ByteArray, charsetName: String): String {
        return try {
            val charset = Charset.forName(charsetName)
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            String(bytes, Charsets.UTF_8)
        }
    }

    actual fun decodeWithFallback(bytes: ByteArray, fallbackCharset: String): String {
        // 先尝试 UTF-8 严格解码
        val utf8Result = try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            null
        }
        if (utf8Result != null) return utf8Result
        // UTF-8 解码失败，使用回退字符集
        return decode(bytes, fallbackCharset)
    }
}
