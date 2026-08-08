package com.tvbox.core.history

import com.tvbox.core.model.PlayHistory
import com.tvbox.core.storage.PlayHistoryDao
import com.tvbox.utils.DateUtils

/**
 * 播放历史管理服务
 *
 * 封装 [PlayHistoryDao] 的数据访问能力，提供面向业务的播放历史管理接口，
 * 包括记录播放、保存进度、查询历史、清理记录等功能。
 *
 * @param dao 播放历史数据访问对象
 */
class HistoryManager(private val dao: PlayHistoryDao) {

    /**
     * 记录一次播放
     *
     * 若同一影视（vodId + sourceKey）已存在播放记录，则更新该记录；
     * 否则新增一条记录。自动刷新 updateTime。
     *
     * @param history 播放历史信息
     * @return 记录 ID
     */
    suspend fun recordPlay(history: PlayHistory): Long {
        val existing = dao.getAll().find {
            it.vodId == history.vodId && it.sourceKey == history.sourceKey
        }
        val now = DateUtils.currentTimeMillis()
        return if (existing != null) {
            dao.update(history.copy(id = existing.id, updateTime = now))
            existing.id
        } else {
            dao.insert(history.copy(updateTime = now))
        }
    }

    /**
     * 保存播放进度
     *
     * 根据播放位置与总时长自动计算进度百分比，并刷新 updateTime。
     *
     * @param id 记录 ID
     * @param position 当前播放位置（毫秒）
     * @param duration 总时长（毫秒）
     */
    suspend fun saveProgress(id: Long, position: Long, duration: Long) {
        val history = dao.getById(id) ?: return
        val progress = if (duration > 0) {
            ((position * 100) / duration).toInt().coerceIn(0, 100)
        } else {
            0
        }
        dao.update(
            history.copy(
                position = position,
                duration = duration,
                progress = progress,
                updateTime = DateUtils.currentTimeMillis()
            )
        )
    }

    /**
     * 查询指定记录 ID 的播放历史
     * @param id 记录 ID
     * @return 播放历史记录，不存在返回 null
     */
    suspend fun getHistory(id: Long): PlayHistory? = dao.getById(id)

    /**
     * 删除指定记录 ID 的播放历史
     * @param id 记录 ID
     */
    suspend fun deleteHistory(id: Long) {
        dao.delete(id)
    }

    /**
     * 清空全部播放历史
     */
    suspend fun clearAllHistory() {
        dao.clearAll()
    }

    /**
     * 获取最近的播放历史
     *
     * 按 updateTime 倒序排列，取前 [limit] 条。
     *
     * @param limit 返回记录数量上限，默认 20
     * @return 最近的播放历史列表
     */
    suspend fun getRecentHistory(limit: Int = 20): List<PlayHistory> {
        return dao.getAll()
            .sortedByDescending { it.updateTime }
            .take(limit)
    }
}
