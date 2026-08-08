package com.tvbox.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * 跨平台协程调度器提供者
 * 统一协程线程调度，各平台可注入对应实现
 */
interface DispatcherProvider {

    /**
     * 主线程调度器（UI线程）
     */
    val main: CoroutineDispatcher

    /**
     * IO 线程调度器（网络/文件操作）
     */
    val io: CoroutineDispatcher

    /**
     * 默认计算调度器（CPU密集型任务）
     */
    val default: CoroutineDispatcher

    /**
     * 单线程调度器（需要顺序执行的操作）
     */
    val unconfined: CoroutineDispatcher
}

/**
 * 默认协程调度器实现
 * 基于 kotlinx.coroutines 标准调度器
 */
class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}
