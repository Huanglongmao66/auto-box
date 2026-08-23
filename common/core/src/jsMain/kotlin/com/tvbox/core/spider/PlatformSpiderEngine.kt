package com.tvbox.core.spider

/**
 * JS (Kotlin/JS) 平台 SpiderEngine 实现
 *
 * 使用原生 JavaScript eval() 函数执行 Spider 脚本。
 * 通过 @JsName("eval") external 声明绑定 JS 全局 eval 函数，
 * 从而支持动态字符串参数（js() 内联函数仅接受常量字符串）。
 */

@JsName("eval")
private external fun jsEval(code: String): dynamic

class JsSpiderEngine : SpiderEngine {

    override val available: Boolean = true

    override fun eval(jsCode: String): String {
        return try {
            val result = jsEval(jsCode)
            result?.toString() ?: ""
        } catch (e: dynamic) {
            ""
        }
    }

    override fun evalFunction(jsCode: String, functionName: String, argsJson: String): String {
        return try {
            // 先加载脚本定义
            jsEval(jsCode)
            // 构造调用代码并执行
            val callCode = buildString {
                append("(function(){ try { var fn = this['")
                append(functionName)
                append("']; if (typeof fn !== 'function') return ''; var args = JSON.parse('")
                append(argsJson.replace("'", "\\'"))
                append("'); var result = fn.apply(null, args); return JSON.stringify(result); } catch(e) { return ''; } })();")
            }
            val result = jsEval(callCode)
            result?.toString() ?: ""
        } catch (e: dynamic) {
            ""
        }
    }
}
