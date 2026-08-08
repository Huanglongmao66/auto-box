package com.tvbox.deviceapi.storage

/**
 * 跨平台存储管理抽象接口
 *
 * 规范缓存目录、用户数据目录、源文件目录的访问规则，
 * 各平台独立实现目录映射，业务层统一调用接口。
 */
interface StorageManager {

    // ===== 目录访问 =====

    /**
     * 获取缓存目录路径（临时文件、播放缓存）
     */
    fun getCacheDir(): String

    /**
     * 获取用户数据目录路径（配置、收藏、历史等持久化数据）
     */
 fun getDocumentDir(): String

    /**
     * 获取下载目录路径（导出文件、离线缓存）
     */
    fun getDownloadDir(): String

    /**
     * 获取影视源文件目录路径
     */
    fun getSourceDir(): String

    // ===== 文件操作 =====

    /**
     * 读取文本文件
     * @param path 文件路径
     * @return 文件内容，文件不存在返回空字符串
     */
    fun readFile(path: String): String

    /**
     * 写入文本文件
     * @param path 文件路径
     * @param content 文件内容
     * @param append 是否追加写入
     */
    fun writeFile(path: String, content: String, append: Boolean = false)

    /**
     * 读取二进制文件
     */
    fun readBytes(path: String): ByteArray

    /**
     * 写入二进制文件
     */
    fun writeBytes(path: String, bytes: ByteArray, append: Boolean = false)

    // ===== 文件管理 =====

    /**
     * 检查文件/目录是否存在
     */
    fun exists(path: String): Boolean

    /**
     * 创建目录（含父目录）
     */
    fun mkdirs(path: String): Boolean

    /**
     * 删除文件或目录
     */
    fun delete(path: String): Boolean

    /**
     * 列出目录下的文件/子目录
     * @param path 目录路径
     * @return 文件名列表
     */
    fun listFiles(path: String): List<String>

    /**
     * 获取文件大小（字节）
     */
    fun getFileSize(path: String): Long

    /**
     * 移动/重命名文件
     */
    fun move(from: String, to: String): Boolean

    /**
     * 复制文件
     */
    fun copy(from: String, to: String): Boolean

    // ===== 配置存储 =====

    /**
     * 读取配置值（键值对存储）
     */
    fun getConfig(key: String, default: String = ""): String

    /**
     * 写入配置值（键值对存储）
     */
    fun setConfig(key: String, value: String)

    /**
     * 删除配置项
     */
    fun removeConfig(key: String)

    /**
     * 清除所有缓存数据
     */
    fun clearCache()

    /**
     * 获取可用存储空间（字节）
     */
    fun getAvailableSpace(): Long
}

/**
 * 存储目录类型
 */
enum class StorageType {
    CACHE,
    DOCUMENT,
    DOWNLOAD,
    SOURCE
}
