package com.tvbox.utils

/**
 * JS 平台编码工具实现
 *
 * 使用浏览器 TextDecoder API 支持 GBK 等字符集。
 * 注意：TextDecoder 在大部分现代浏览器中支持 'gbk' / 'gb2312'。
 */
actual object EncodingUtils {

    actual fun decode(bytes: ByteArray, charsetName: String): String {
        return try {
            val decoder = js("new TextDecoder(charsetName)")
            val uint8 = js("new Uint8Array(bytes)")
            decoder.decode(uint8) as String
        } catch (_: Throwable) {
            String(bytes, Charsets.UTF_8)
        }
    }

    actual fun decodeWithFallback(bytes: ByteArray, fallbackCharset: String): String {
        // 先尝试 UTF-8
        val utf8 = String(bytes, Charsets.UTF_8)
        // JS 中无法严格检测，简单检查是否含替换字符
        if (!utf8.contains('\uFFFD')) return utf8
        return try {
            decode(bytes, fallbackCharset)
        } catch (_: Throwable) {
            utf8
        }
    }
}
