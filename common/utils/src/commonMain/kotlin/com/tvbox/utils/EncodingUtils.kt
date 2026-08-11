package com.tvbox.utils

/**
 * 跨平台文本编码工具
 *
 * 提供 GBK 等非 UTF-8 中文编码的解码能力（commonMain 原生仅支持 UTF-8）。
 * 用于解析远端订阅源可能返回的 GBK/GB2312 编码文本。
 */
expect object EncodingUtils {

    /**
     * 将字节数组按指定字符集名解码为字符串。
     *
     * 支持的字符集取决于平台 JVM/JS 实现，常见如 "UTF-8"、"GBK"、"GB2312"、"Big5"。
     *
     * @param bytes 原始字节
     * @param charsetName 字符集名称
     * @return 解码后的字符串；不支持时回退 UTF-8
     */
    fun decode(bytes: ByteArray, charsetName: String): String

    /**
     * 尝试用 UTF-8 解码；若结果包含替换字符（U+FFFD），说明原始不是 UTF-8，
     * 再用 [fallbackCharset] 解码。
     *
     * @param bytes 原始字节
     * @param fallbackCharset 回退字符集（默认 GBK）
     * @return 解码后的字符串
     */
    fun decodeWithFallback(bytes: ByteArray, fallbackCharset: String = "GBK"): String
}
