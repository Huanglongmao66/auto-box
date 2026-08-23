package com.tvbox.core.favorite

import com.tvbox.core.model.Favorite
import com.tvbox.core.storage.FavoriteDao
import com.tvbox.utils.DateUtils

/**
 * 收藏管理服务
 *
 * 封装 [FavoriteDao] 的数据访问能力，提供面向业务的收藏管理接口，
 * 包括添加收藏、移除收藏、判断收藏状态、按来源筛选等功能。
 *
 * @param dao 收藏数据访问对象
 */
class FavoriteManager(private val dao: FavoriteDao) {

    /**
     * 添加收藏
     *
     * 若同一影视（vodId + sourceKey）已收藏，则更新该记录；
     * 否则新增一条记录。新增时自动设置 createTime。
     *
     * @param favorite 收藏信息
     * @return 记录 ID
     */
    suspend fun addFavorite(favorite: Favorite): Long {
        val existing = dao.getAll().find {
            it.vodId == favorite.vodId && it.sourceKey == favorite.sourceKey
        }
        return if (existing != null) {
            dao.update(favorite.copy(id = existing.id, createTime = existing.createTime))
            existing.id
        } else {
            dao.insert(favorite.copy(createTime = DateUtils.currentTimeMillis()))
        }
    }

    /**
     * 移除收藏
     * @param id 记录 ID
     */
    suspend fun removeFavorite(id: Long) {
        dao.delete(id)
    }

    /**
     * 判断指定影视是否已收藏
     * @param vodId 影视 ID
     * @param sourceKey 来源站点 key
     * @return 已收藏返回 true，否则返回 false
     */
    suspend fun isFavorite(vodId: String, sourceKey: String): Boolean {
        return dao.getAll().any {
            it.vodId == vodId && it.sourceKey == sourceKey
        }
    }

    /**
     * 获取全部收藏记录
     * @return 收藏列表
     */
    suspend fun getAllFavorites(): List<Favorite> = dao.getAll()

    /**
     * 按来源站点筛选收藏
     * @param sourceKey 来源站点 key
     * @return 该站点下的收藏列表
     */
    suspend fun getBySource(sourceKey: String): List<Favorite> {
        return dao.getAll().filter { it.sourceKey == sourceKey }
    }

    /**
     * 清空全部收藏
     */
    suspend fun clearAll() {
        dao.clearAll()
    }
}
