package com.tvbox.core.storage

import com.tvbox.core.model.Favorite
import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.PlayHistory

/**
 * 跨平台数据库访问接口
 *
 * 定义统一的数据库访问入口，屏蔽底层存储实现差异
 * （各平台可基于 SQLDelight / Room / IndexedDB 等实现）。
 * 通过 [getPlayHistoryDao]、[getFavoriteDao]、[getSourceDao] 获取对应的 DAO。
 */
interface Database {

    /**
     * 获取播放历史 DAO
     * @return 播放历史数据访问对象
     */
    fun getPlayHistoryDao(): PlayHistoryDao

    /**
     * 获取收藏 DAO
     * @return 收藏数据访问对象
     */
    fun getFavoriteDao(): FavoriteDao

    /**
     * 获取影视源 DAO
     * @return 影视源数据访问对象
     */
    fun getSourceDao(): SourceDao

    /**
     * 关闭数据库，释放底层资源
     */
    fun close()
}

/**
 * 播放历史数据访问接口
 *
 * 提供播放历史记录的增删改查操作，方法均为挂起函数，
 * 便于后续替换为 SQLDelight 协程扩展实现。
 */
interface PlayHistoryDao {

    /**
     * 新增播放历史记录
     * @param history 播放历史记录（id 为 0 时自动生成）
     * @return 记录 ID
     */
    suspend fun insert(history: PlayHistory): Long

    /**
     * 更新播放历史记录
     * @param history 播放历史记录（须携带已有 ID）
     */
    suspend fun update(history: PlayHistory)

    /**
     * 根据记录 ID 删除播放历史
     * @param id 记录 ID
     */
    suspend fun delete(id: Long)

    /**
     * 根据记录 ID 查询播放历史
     * @param id 记录 ID
     * @return 播放历史记录，不存在返回 null
     */
    suspend fun getById(id: Long): PlayHistory?

    /**
     * 查询全部播放历史记录
     * @return 播放历史列表
     */
    suspend fun getAll(): List<PlayHistory>

    /**
     * 清空全部播放历史记录
     */
    suspend fun clearAll()
}

/**
 * 收藏数据访问接口
 *
 * 提供收藏记录的增删改查操作，方法均为挂起函数。
 */
interface FavoriteDao {

    /**
     * 新增收藏记录
     * @param favorite 收藏记录（id 为 0 时自动生成）
     * @return 记录 ID
     */
    suspend fun insert(favorite: Favorite): Long

    /**
     * 更新收藏记录
     * @param favorite 收藏记录（须携带已有 ID）
     */
    suspend fun update(favorite: Favorite)

    /**
     * 根据记录 ID 删除收藏
     * @param id 记录 ID
     */
    suspend fun delete(id: Long)

    /**
     * 根据记录 ID 查询收藏
     * @param id 记录 ID
     * @return 收藏记录，不存在返回 null
     */
    suspend fun getById(id: Long): Favorite?

    /**
     * 查询全部收藏记录
     * @return 收藏列表
     */
    suspend fun getAll(): List<Favorite>

    /**
     * 清空全部收藏记录
     */
    suspend fun clearAll()
}

/**
 * 影视源数据访问接口
 *
 * 提供影视源的增删改查操作，以站点 key 作为主键。
 */
interface SourceDao {

    /**
     * 新增影视源
     * @param source 影视源
     * @return 站点 key
     */
    suspend fun insert(source: MovieSource): String

    /**
     * 更新影视源
     * @param source 影视源（须携带已有 key）
     */
    suspend fun update(source: MovieSource)

    /**
     * 根据站点 key 删除影视源
     * @param key 站点 key
     */
    suspend fun delete(key: String)

    /**
     * 根据站点 key 查询影视源
     * @param key 站点 key
     * @return 影视源，不存在返回 null
     */
    suspend fun getByKey(key: String): MovieSource?

    /**
     * 查询全部影视源
     * @return 影视源列表
     */
    suspend fun getAll(): List<MovieSource>

    /**
     * 清空全部影视源
     */
    suspend fun clearAll()
}
