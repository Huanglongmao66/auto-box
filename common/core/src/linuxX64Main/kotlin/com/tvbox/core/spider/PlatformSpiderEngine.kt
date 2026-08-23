package com.tvbox.core.spider

/**
 * LinuxX64 平台 SpiderEngine 实现
 *
 * Kotlin/Native 无原生 JS 引擎，Spider 源在此平台上不可用。
 * 后续可考虑引入 QuickJS 等嵌入式 JS 引擎。
 */
class NativeSpiderEngine : SpiderEngine {
    override val available: Boolean = false
    override fun eval(jsCode: String): String = ""
    override fun evalFunction(jsCode: String, functionName: String, argsJson: String): String = ""
}
