package com.tvbox.core.ui.focus

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlin.math.abs

/**
 * 焦点移动方向
 */
enum class FocusDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

/**
 * 可聚焦节点信息
 *
 * @property id 节点唯一标识
 * @property row 节点所在行，用于方向导航的空间定位
 * @property column 节点所在列，用于方向导航的空间定位
 * @property focusRequester 关联的 Compose 焦点请求器
 */
data class FocusableNode(
    val id: String,
    val row: Int,
    val column: Int,
    val focusRequester: FocusRequester
)

/**
 * TV 端遥控器焦点管理器
 *
 * 维护可聚焦节点注册表，按方向计算下一个目标节点，并配合 Compose Focus API
 * 完成焦点切换，适用于 TV 大屏场景下的方向键导航。
 */
@Stable
class TvFocusManager {

    /** 已注册的可聚焦节点（id -> 节点） */
    private val nodes = mutableStateMapOf<String, FocusableNode>()

    /** 当前持有焦点的节点 id */
    var currentFocusId: String? by mutableStateOf(null)
        private set

    /**
     * 注册可聚焦节点
     *
     * @param node 待注册节点
     */
    fun registerFocusable(node: FocusableNode) {
        nodes[node.id] = node
    }

    /**
     * 注销可聚焦节点
     *
     * @param id 节点唯一标识
     */
    fun unregisterFocusable(id: String) {
        nodes.remove(id)
        if (currentFocusId == id) {
            currentFocusId = null
        }
    }

    /**
     * 获取当前焦点节点
     *
     * @return 当前持有焦点的节点，无焦点时返回 null
     */
    fun getCurrentFocus(): FocusableNode? = currentFocusId?.let { nodes[it] }

    /**
     * 按方向移动焦点
     *
     * 基于节点行列坐标计算最近的目标节点，返回其焦点请求器以便调用方请求焦点。
     *
     * @param direction 移动方向
     * @return 目标节点对应的焦点请求器，无可用目标时返回 null
     */
    fun moveFocus(direction: FocusDirection): FocusRequester? {
        val current = currentFocusId?.let { nodes[it] }
        val target = findNextNode(current, direction) ?: return null
        currentFocusId = target.id
        return target.focusRequester
    }

    /**
     * 上报节点获得焦点（由 Composable 的 onFocusChanged 回调调用）
     */
    internal fun onFocused(id: String) {
        currentFocusId = id
    }

    /**
     * 根据方向查找下一个目标节点
     *
     * 策略：先按方向过滤候选节点，再以"主轴距离优先、副轴距离次之"的加权距离取最小值。
     */
    private fun findNextNode(current: FocusableNode?, direction: FocusDirection): FocusableNode? {
        if (current == null) {
            // 无当前焦点时，默认聚焦左上角节点
            return nodes.values.minByOrNull { it.row * 100 + it.column }
        }
        val candidates = nodes.values.filter { it.id != current.id }
        val directional = when (direction) {
            FocusDirection.UP -> candidates.filter { it.row < current.row }
            FocusDirection.DOWN -> candidates.filter { it.row > current.row }
            FocusDirection.LEFT -> candidates.filter { it.column < current.column }
            FocusDirection.RIGHT -> candidates.filter { it.column > current.column }
        }
        return directional.minByOrNull { node ->
            val rowDelta = abs(node.row - current.row)
            val columnDelta = abs(node.column - current.column)
            when (direction) {
                FocusDirection.UP, FocusDirection.DOWN -> rowDelta * 10 + columnDelta
                FocusDirection.LEFT, FocusDirection.RIGHT -> columnDelta * 10 + rowDelta
            }
        }
    }
}

/**
 * 在 Composable 中记住一个 [TvFocusManager] 实例
 */
@Composable
fun rememberTvFocusManager(): TvFocusManager = remember { TvFocusManager() }

/**
 * 为可聚焦节点应用 TV 焦点能力
 *
 * 自动完成节点注册/注销，并绑定 Compose Focus API；获得焦点时会上报至 [TvFocusManager]。
 *
 * @param manager 焦点管理器
 * @param id 节点唯一标识
 * @param row 节点所在行
 * @param column 节点所在列
 */
@Composable
fun Modifier.tvFocusable(
    manager: TvFocusManager,
    id: String,
    row: Int,
    column: Int
): Modifier {
    val focusRequester = remember(id) { FocusRequester() }
    DisposableEffect(id) {
        manager.registerFocusable(FocusableNode(id, row, column, focusRequester))
        onDispose { manager.unregisterFocusable(id) }
    }
    return this
        .focusRequester(focusRequester)
        .onFocusChanged { state -> if (state.isFocused) manager.onFocused(id) }
        .focusable()
}
