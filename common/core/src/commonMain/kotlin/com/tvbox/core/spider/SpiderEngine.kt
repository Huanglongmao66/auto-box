package com.tvbox.core.spider

/**
 * JS 脚本执行引擎抽象
 *
 * 用于执行 TVBox Spider 源的 JavaScript 脚本。
 * TVBox Spider 源通常提供以下标准函数：
 * - init(cfg) / homeContent(tid) / categoryContent(tid, pg, filter, extend)
 * - detailContent(ids) / searchContent(key, quick) / playUrl(flag, id, flags)
 *
 * 不同平台的 JS 执行能力：
 * - JVM / Android：javax.script (Nashorn / Rhino)，需引入对应依赖
 * - JS：原生 eval()
 * - LinuxX64：无原生 JS 引擎，返回空结果
 */
interface SpiderEngine {

    /**
     * 是否可用（平台是否支持 JS 执行）
     */
    val available: Boolean

    /**
     * 执行 JS 脚本并返回结果
     *
     * @param jsCode 完整 JS 脚本（含函数定义和调用）
     * @return JS 执行返回值的字符串形式
     */
    fun eval(jsCode: String): String

    /**
     * 执行 Spider 源的指定函数
     *
     * @param jsCode 完整 JS 脚本（含函数定义）
     * @param functionName 要调用的函数名（如 "homeContent"）
     * @param argsJson 函数参数（JSON 数组字符串）
     * @return 函数返回值的字符串形式
     */
    fun evalFunction(jsCode: String, functionName: String, argsJson: String): String
}

/**
 * 不可用的 SpiderEngine 实现
 *
 * 在不支持 JS 执行的平台上作为默认实现，所有调用返回空结果。
 */
class NoOpSpiderEngine : SpiderEngine {
    override val available: Boolean = false
    override fun eval(jsCode: String): String = ""
    override fun evalFunction(jsCode: String, functionName: String, argsJson: String): String = ""
}

/**
 * SpiderEngine 工厂
 *
 * 尝试创建平台对应的 JS 引擎，不可用时返回 [NoOpSpiderEngine]。
 */
object SpiderEngineFactory {
    private var instance: SpiderEngine? = null

    /**
     * 设置全局 SpiderEngine 实例（由平台层在启动时调用）
     */
    fun setEngine(engine: SpiderEngine) {
        instance = engine
    }

    /**
     * 获取当前 SpiderEngine（如未设置则返回 NoOp）
     */
    fun get(): SpiderEngine = instance ?: NoOpSpiderEngine()
}
