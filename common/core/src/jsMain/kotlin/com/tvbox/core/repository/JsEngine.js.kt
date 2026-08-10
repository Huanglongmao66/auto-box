package com.tvbox.core.repository

import kotlinx.browser.window as browserWindow

/**
 * Web(JS) 平台 JsEngine：直接使用浏览器内置 Function 构造器 + globalThis 注入，
 * 避免 kotlinx.js 导入别名差异导致的 "Unresolved reference 'jsEval'" 等编译问题。
 */
actual object JsEngineFactory {
    actual val isSupported: Boolean get() = true
    actual fun create(): JsEngine = BrowserJsEngine()
}

private class BrowserJsEngine : JsEngine {
    private val sandbox: dynamic = runCatching { js("({})") }.getOrElse { Any() }

    override fun set(name: String, value: Any?) {
        runCatching { jsSandPut(sandbox, name, value) }
    }

    override fun eval(code: String): String {
        return runCatching { normalize(jsEvalInSandbox(sandbox, code)) }.getOrDefault("")
    }

    override fun call(functionName: String, vararg args: Any?): String {
        return runCatching { normalize(jsCallInSandbox(sandbox, functionName, args)) }.getOrDefault("")
    }

    private fun normalize(r: dynamic): String {
        if (r == null) return ""
        val t = jsTypeOf(r)
        return when (t) {
            "string" -> r as String
            "number", "boolean" -> r.toString()
            "undefined" -> ""
            else -> {
                runCatching {
                    val s = jsStringify(r)
                    if (!s.isNullOrBlank() && s != "null") return s
                }
                runCatching { r.toString() as String }.getOrDefault("")
            }
        }
    }
}

private fun jsTypeOf(v: dynamic): String = js("typeof v")
private fun jsStringify(v: dynamic): String? = js("(typeof JSON !== 'undefined') ? JSON.stringify(v) : null")
private fun jsSandPut(sandbox: dynamic, k: String, v: dynamic) {
    js(" sandbox[k] = v; ")
}

private fun jsEvalInSandbox(sandbox: dynamic, code: String): dynamic {
    val wrapped =
        "(function(sandbox){ try { with(sandbox){ return (function(){ \"use strict\"; $code }).call(sandbox); } } catch(e){ sandbox.__lastErr = e && e.message || String(e); return undefined; } })"
    val fn: dynamic = js(" (new Function('return ' + arguments[0]))() ")(wrapped)
    return fn(sandbox)
}

private fun jsCallInSandbox(sandbox: dynamic, name: String, args: Array<out Any?>): dynamic {
    val fn: dynamic = jsEvalInSandbox(sandbox, "return $name;")
    return if (jsTypeOf(fn) == "function") {
        js(" fn.apply(sandbox, args); ")
    } else {
        val argStr = args.joinToString(",") { argLiteral(it) }
        jsEvalInSandbox(sandbox, "return $name($argStr);")
    }
}

private fun argLiteral(a: Any?): String {
    return when (a) {
        null -> "null"
        is Boolean, is Number, is Int, is Long, is Short, is Byte, is Double, is Float -> a.toString()
        is String -> {
            val s = a
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
            val s = a.toString()
            "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }
    }
}

