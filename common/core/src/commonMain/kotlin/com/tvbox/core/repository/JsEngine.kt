package com.tvbox.core.repository

/**
 * 跨平台 JS 引擎抽象（expect / actual 模式）
 *
 * 各平台的实际引擎实现由 commonMain 的 [JsEngineBridge] 统一接口：
 *   - set(key, value)  注入全局变量
 *   - eval(code)        执行代码，返回最后一个表达式字符串
 *   - call(name, args)  调用顶层函数，返回 JSON 字符串结果
 *   - close()           释放引擎资源
 *
 * 平台绑定（expect/actual）：
 *  - Android: Rhino(no-natives) 或 J2V8（优先 Rhino，避免 NDK 依赖）
 *  - JVM (Desktop/LinuxTV): Nashorn (java 11+) 或 Nashorn standalone
 *  - JS(Web): 直接用 eval + globalThis
 *  - linuxX64(Native): 暂不支持（降级）
 *
 * 注意：commonMain 本身不知道具体引擎，只暴露 expect 的 [JsEngineFactory] 给 actual 实现。
 * 如果 actual 没提供（例如 Native），[isSupported] 返回 false，SpiderJsSourceClient 会安全降级返回空。
 */

/** commonMain: 引擎工厂；各平台 actual 实现 */
internal expect object JsEngineFactory {
    val isSupported: Boolean
    fun create(): JsEngine?
}

internal interface JsEngine : AutoCloseable {
    fun set(name: String, value: Any?)
    fun eval(code: String): String
    fun call(functionName: String, vararg args: Any?): String
    override fun close() {}
}
