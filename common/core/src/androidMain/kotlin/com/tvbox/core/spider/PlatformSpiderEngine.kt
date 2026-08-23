package com.tvbox.core.spider

/**
 * Android 平台 SpiderEngine 实现
 *
 * 复用 JVM 的 javax.script.ScriptEngine 调用 Nashorn JavaScript 引擎。
 * Android 默认不包含 Nashorn，需引入 org.mozilla:rhino 依赖。
 * 若运行时无 JS 引擎，[available] 返回 false，所有调用返回空。
 */
class JvmSpiderEngine : SpiderEngine {

    private val engine: Any? by lazy {
        runCatching {
            val factory = javax.script.ScriptEngineManager()
            factory.getEngineByName("javascript")
                ?: factory.getEngineByName("nashorn")
                ?: factory.getEngineByName("js")
        }.getOrNull()
    }

    override val available: Boolean
        get() = engine != null

    override fun eval(jsCode: String): String {
        val eng = engine ?: return ""
        return runCatching {
            val result = (eng as javax.script.ScriptEngine).eval(jsCode)
            result?.toString() ?: ""
        }.getOrDefault("")
    }

    override fun evalFunction(jsCode: String, functionName: String, argsJson: String): String {
        val eng = engine ?: return ""
        return runCatching {
            val se = eng as javax.script.ScriptEngine
            se.eval(jsCode)
            val callJs = "JSON.stringify($functionName.apply(null, $argsJson))"
            val result = se.eval(callJs)
            result?.toString() ?: ""
        }.getOrDefault("")
    }
}
