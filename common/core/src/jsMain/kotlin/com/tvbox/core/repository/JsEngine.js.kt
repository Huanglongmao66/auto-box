package com.tvbox.core.repository

import kotlinx.browser.window as browserWindow
import kotlinx.js.eval as jsEval

/**
 * Web(JS) 平台 JsEngine：直接用浏览器全局 eval。
 *
 * 出于 CSP/安全考虑，浏览器中的 Spider JS 仅支持字符串注入 + 纯 JS 运行；
 * 如果页面开启了严格 CSP 禁止 eval，调用会抛异常 -> 调用点 getOrDefault("") 安全降级。
 */
actual object JsEngineFactory {
    actual val isSupported: Boolean get() = true

    actual fun create(): JsEngine = BrowserJsEngine()
}

private class BrowserJsEngine : JsEngine {
    override fun set(name: String, value: Any?) {
        val w = runCatching { js("globalThis") }.getOrNull() ?: runCatching { browserWindow }.getOrNull()
        if (w != null) {
            runCatching { jsEval("(function(o,k,v){ o[k]=v; })(typeof globalThis!=='undefined'?globalThis:window, ${name.toJsLiteral()}, ${value.toJsLiteral()});") }
        } else {
            runCatching { jsEval("var $name = ${value.toJsLiteral()};") }
        }
    }

    override fun eval(code: String): String {
        return runCatching {
            val v = jsEval(code)
            v?.toString().orEmpty()
        }.getOrDefault("")
    }

    override fun call(functionName: String, vararg args: Any?): String {
        return runCatching {
            val argList = args.joinToString(",") { it.toJsLiteral() }
            val v = jsEval("$functionName($argList)")
            v?.toString().orEmpty()
        }.getOrDefault("")
    }
}

private fun Any?.toJsLiteral(): String {
    return when (this) {
        null -> "null"
        is Boolean, is Number -> this.toString()
        is String -> {
            val s = this
            buildString(s.length + 2) {
                append('"')
                for (c in s) {
                    when (c) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(c)
                    }
                }
                append('"')
            }
        }
        else -> {
            val s = toString()
            "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }
    }
}
