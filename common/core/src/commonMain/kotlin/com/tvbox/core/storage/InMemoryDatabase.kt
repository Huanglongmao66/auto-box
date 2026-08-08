package com.tvbox.core.storage

import com.tvbox.core.model.Favorite
import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.PlayHistory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 基于内存 Map 的数据库默认实现
 *
 * 使用 [Mutex] 保护的 [MutableMap] 提供线程安全的增删改查，
 * 适用于开发调试与单元测试；后续将替换为 SQLDelight 持久化实现。
 *
 * 注：KMP commonMain 中无法使用 java.util.concurrent.ConcurrentHashMap，
 * 因此采用 [kotlinx.coroutines.sync.Mutex] 实现跨平台线程安全。
 */
class InMemoryDatabase : Database {

    private val playHistoryDao = InMemoryPlayHistoryDao()
    private val favoriteDao = InMemoryFavoriteDao()
    private val sourceDao = InMemorySourceDao()

    override fun getPlayHistoryDao(): PlayHistoryDao = playHistoryDao

    override fun getFavoriteDao(): FavoriteDao = favoriteDao

    override fun getSourceDao(): SourceDao = sourceDao

    override fun close() {
        // 内存实现无需释放底层资源
    }
}

/**
 * 内存版播放历史 DAO
 *
 * 以记录 ID（Long）为键存储，ID 自动递增。
 */
private class InMemoryPlayHistoryDao : PlayHistoryDao {

    private val store = mutableMapOf<Long, PlayHistory>()
    private val mutex = Mutex()
    private var idCounter = 0L

    override suspend fun insert(history: PlayHistory): Long = mutex.withLock {
        val id = if (history.id <= 0L) ++idCounter else history.id
        if (id > idCounter) idCounter = id
        store[id] = history.copy(id = id)
        id
    }

    override suspend fun update(history: PlayHistory) {
        mutex.withLock {
            if (store.containsKey(history.id)) {
                store[history.id] = history
            }
        }
    }

    override suspend fun delete(id: Long) {
        mutex.withLock { store.remove(id) }
    }

    override suspend fun getById(id: Long): PlayHistory? = mutex.withLock {
        store[id]
    }

    override suspend fun getAll(): List<PlayHistory> = mutex.withLock {
        store.values.toList()
    }

    override suspend fun clearAll() {
        mutex.withLock { store.clear() }
    }
}

/**
 * 内存版收藏 DAO
 *
 * 以记录 ID（Long）为键存储，ID 自动递增。
 */
private class InMemoryFavoriteDao : FavoriteDao {

    private val store = mutableMapOf<Long, Favorite>()
    private val mutex = Mutex()
    private var idCounter = 0L

    override suspend fun insert(favorite: Favorite): Long = mutex.withLock {
        val id = if (favorite.id <= 0L) ++idCounter else favorite.id
        if (id > idCounter) idCounter = id
        store[id] = favorite.copy(id = id)
        id
    }

    override suspend fun update(favorite: Favorite) {
        mutex.withLock {
            if (store.containsKey(favorite.id)) {
                store[favorite.id] = favorite
            }
        }
    }

    override suspend fun delete(id: Long) {
        mutex.withLock { store.remove(id) }
    }

    override suspend fun getById(id: Long): Favorite? = mutex.withLock {
        store[id]
    }

    override suspend fun getAll(): List<Favorite> = mutex.withLock {
        store.values.toList()
    }

    override suspend fun clearAll() {
        mutex.withLock { store.clear() }
    }
}

/**
 * 内存版影视源 DAO
 *
 * 以站点 key（String）为键存储。
 */
private class InMemorySourceDao : SourceDao {

    private val store = mutableMapOf<String, MovieSource>()
    private val mutex = Mutex()

    override suspend fun insert(source: MovieSource): String = mutex.withLock {
        store[source.key] = source
        source.key
    }

    override suspend fun update(source: MovieSource) {
        mutex.withLock {
            if (store.containsKey(source.key)) {
                store[source.key] = source
            }
        }
    }

    override suspend fun delete(key: String) {
        mutex.withLock { store.remove(key) }
    }

    override suspend fun getByKey(key: String): MovieSource? = mutex.withLock {
        store[key]
    }

    override suspend fun getAll(): List<MovieSource> = mutex.withLock {
        store.values.toList()
    }

    override suspend fun clearAll() {
        mutex.withLock { store.clear() }
    }
}
