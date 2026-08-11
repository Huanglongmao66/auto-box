package com.tvbox.utils

/**
 * Linux X64 平台编码工具实现
 *
 * 纯 Kotlin/Native 不支持 GBK 字符集（仅 UTF-8），
 * GBK 解码在 Linux TV 上可后续通过 iconv 扩展。当前先回退 UTF-8。
 */
actual object EncodingUtils {

    actual fun decode(bytes: ByteArray, charsetName: String): String {
        // Kotlin/Native 目前仅内建 UTF-8 支持
        return String(bytes, Charsets.UTF_8)
    }

    actual fun decodeWithFallback(bytes: ByteArray, fallbackCharset: String): String {
        // Kotlin/Native 环境仅支持 UTF-8，GBK 回退暂不可用
        return String(bytes, Charsets.UTF_8)
    }
}
