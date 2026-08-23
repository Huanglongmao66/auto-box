package com.tvbox.core.spider

/**
 * JS (Kotlin/JS) 平台 SpiderEngine 实现
 *
 * 使用原生 JavaScript eval() 函数执行 Spider 脚本。
 */
class JsSpiderEngine : SpiderEngine {

    override val available: Boolean = true

    override fun eval(jsCode: String): String {
        return try {
            js("try { var __r = eval(\"(\" + \"\" + \")\"); \"\" + String(eval(") + jsCode + js(")); } catch(e) { \"\" }")
        } catch (e: dynamic) {
            ""
        }
    }

    override fun evalFunction(jsCode: String, functionName: String, argsJson: String): String {
        return try {
            js("""
                try {
                    (function() {
                        eval($jsCode);
                        var fn = $functionName;
                        if (typeof fn !== 'function') return '';
                        var args = JSON.parse($argsJson);
                        var result = fn.apply(null, args);
                        return JSON.stringify(result);
                    })();
            """)
        } catch (e: dynamic) {
            ""
        }
    }
}
