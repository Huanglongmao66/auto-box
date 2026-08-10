package com.tvbox.core.repository

/**
 * Android 平台的 JsEngine actual：
 *   - 优先 Rhino（纯 Java，无 NDK 依赖）：classpath 有 org.mozilla.javascript.Context 时启用
 *   - ART 不提供 javax.script（通常都没有），因此默认不查 Nashorn
 *   - 如果都找不到，返回 null -> SpiderJsSourceClient 安全降级为空
 */
internal actual object JsEngineFactory {
    actual val isSupported: Boolean get() {
        return runCatching { Class.forName("org.mozilla.javascript.Context") }.getOrNull() != null
    }

    actual fun create(): JsEngine? {
        runCatching {
            val ctxClass = Class.forName("org.mozilla.javascript.Context")
            val enter = ctxClass.getMethod("enter")
            val ctx = enter.invoke(null)
            return RhinoJsEngine(ctx, ctxClass)
        }
        return null
    }
}

private class RhinoJsEngine(
    private var ctx: Any?,
    private val ctxClass: Class<*>
) : JsEngine {
    private var scope: Any? = null

    init {
        runCatching {
            val initStd = ctxClass.getMethod("initStandardObjects")
            scope = initStd.invoke(ctx)
        }
    }

    override fun set(name: String, value: Any?) {
        if (scope == null) return
        runCatching {
            val scClass = Class.forName("org.mozilla.javascript.Scriptable")
            val put = scClass.getMethod("put", String::class.java, scClass, Any::class.java)
            put.invoke(scope, name, scope, value)
        }
    }

    override fun eval(code: String): String {
        if (ctx == null || scope == null) return ""
        return runCatching {
            val eval = ctxClass.getMethod(
                "evaluateReader",
                Class.forName("org.mozilla.javascript.Scriptable"),
                java.io.Reader::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Any::class.java
            )
            val r = eval.invoke(ctx, scope, java.io.StringReader(code), "<spider>", 1, null)
            rhinoToString(r)
        }.getOrDefault("")
    }

    override fun call(functionName: String, vararg args: Any?): String {
        if (ctx == null || scope == null) return ""
        return runCatching {
            val scClass = Class.forName("org.mozilla.javascript.Scriptable")
            val getProp = scClass.getMethod("get", String::class.java, scClass)
            val fn = getProp.invoke(scope, functionName, scope)
            val fnClass = Class.forName("org.mozilla.javascript.Function")
            if (!fnClass.isInstance(fn)) return@runCatching ""
            val call = fnClass.getMethod(
                "call", ctxClass, scClass, scClass, scClass,
                Array<Any?>::class.java
            )
            val nativeArgs: Array<Any?> = args.map { toRhino(it) }.toTypedArray()
            val r = call.invoke(fn, ctx, scope, scope, scope, nativeArgs)
            rhinoToString(r)
        }.getOrDefault("")
    }

    private fun toRhino(a: Any?): Any? = runCatching {
        val m = ctxClass.getMethod("javaToJS", Any::class.java, Class.forName("org.mozilla.javascript.Scriptable"))
        m.invoke(ctx, a, scope)
    }.getOrDefault(a)

    private fun rhinoToString(r: Any?): String = runCatching {
        val cls = Class.forName("org.mozilla.javascript.ScriptRuntime")
        cls.getMethod("toString", Class.forName("org.mozilla.javascript.Scriptable"))
            .invoke(null, r) as? String
    }.getOrDefault(r?.toString()).orEmpty()

    override fun close() {
        runCatching {
            ctxClass.getMethod("exit").invoke(ctx)
        }
        ctx = null; scope = null
    }
}
