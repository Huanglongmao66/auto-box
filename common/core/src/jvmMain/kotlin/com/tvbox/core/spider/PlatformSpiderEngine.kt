package com.tvbox.core.spider

/**
 * JVM 平台 SpiderEngine 实现
 *
 * 使用 javax.script.ScriptEngine 调用 Nashorn JavaScript 引擎。
 * JDK 8~14 内置 Nashorn；JDK 15+ 需引入 org.openjdk.nashorn:nashorn-core 依赖。
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
