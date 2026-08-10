package com.tvbox.core.repository

/**
 * JVM(Desktop/LinuxTV) + Android 平台的 JsEngine 实现
 *
 * 优先使用 JVM 自带的 Nashorn ScriptEngine（JDK 11~14 内建）。
 * Android 平台没有 Nashorn：在 Android actual 中会走不同分支（Rhino）。
 */
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.SimpleBindings
import kotlin.jvm.Throws

internal actual object JsEngineFactory {
    actual val isSupported: Boolean get() {
        val clazz = runCatching { Class.forName("javax.script.ScriptEngineManager") }.getOrNull()
            ?: runCatching { Class.forName("org.mozilla.javascript.Context") }.getOrNull()
        return clazz != null
    }

    actual fun create(): JsEngine? {
        // 1) 优先 Nashorn（JVM）
        runCatching {
            val mgr = ScriptEngineManager()
            var engine = mgr.getEngineByName("nashorn")
            if (engine == null) engine = mgr.getEngineByName("javascript")
            if (engine != null) return NashornJsEngine(engine)
        }
        // 2) Rhino（Android 或 JVM 带 rhino 依赖）
        runCatching {
            val ctxClass = Class.forName("org.mozilla.javascript.Context")
            val enter = ctxClass.getMethod("enter")
            val ctx = enter.invoke(null)
            return RhinoJsEngine(ctx, ctxClass)
        }
        return null
    }
}

private class NashornJsEngine(private val eng: ScriptEngine) : JsEngine {
    override fun set(name: String, value: Any?) {
        val bindings = eng.createBindings()
        bindings[name] = value
        eng.setBindings(bindings, javax.script.ScriptContext.GLOBAL_SCOPE)
        // 也在 ENGINE_SCOPE 放一份，避免 nashorn 全局不可见
        eng.put(name, value)
    }

    override fun eval(code: String): String {
        return runCatching {
            val r = eng.eval(code)
            normalizeResult(r)
        }.getOrDefault("")
    }

    override fun call(functionName: String, vararg args: Any?): String {
        return runCatching {
            val inv = eng as? javax.script.Invocable
                ?: return@runCatching eval("$functionName(${args.joinToString(",") { argLiteral(it) }})")
            val r = inv.invokeFunction(functionName, *args)
            normalizeResult(r)
        }.getOrDefault("")
    }

    private fun normalizeResult(r: Any?): String {
        return when (r) {
            null -> ""
            is String -> r
            is Number, is Boolean -> r.toString()
            else -> r.toString()
        }
    }

    private fun argLiteral(a: Any?): String {
        return when (a) {
            null -> "null"
            is String -> {
                val escaped = a.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                "\"$escaped\""
            }
            is Number, is Boolean -> a.toString()
            else -> {
                val s = a.toString()
                val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                "\"$escaped\""
            }
        }
    }
}

private class RhinoJsEngine(
    private var ctx: Any?,
    private val ctxClass: Class<*>
) : JsEngine {
    private var scope: Any? = null

    init {
        runCatching {
            val initStdObjects = ctxClass.getMethod("initStandardObjects")
            scope = initStdObjects.invoke(ctx)
        }
    }

    override fun set(name: String, value: Any?) {
        if (scope == null) return
        runCatching {
            val scClass = Class.forName("org.mozilla.javascript.Scriptable")
            val put = scClass.getMethod("put", String::class.java, scClass, Any::class.java)
            // 把非 JS 值用 Context.jsToJava 对面方向包装：直接给 value，Rhino 会自动转
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
            val result = eval.invoke(
                ctx, scope, java.io.StringReader(code), "<spider>", 1, null
            )
            rhinoToString(result)
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
                "call",
                ctxClass, scClass, scClass, scClass,
                Array<Any?>::class.java
            )
            // Rhino 要求 args 用 cx.jsToWrapArray 包装；简单做法是传 Any[]+原生类型
            val nativeArgs: Array<Any?> = args.map { toRhinoValue(it) }.toTypedArray()
            val r = call.invoke(fn, ctx, scope, scope, scope, nativeArgs)
            rhinoToString(r)
        }.getOrDefault("")
    }

    private fun toRhinoValue(a: Any?): Any? {
        if (ctx == null) return a
        return runCatching {
            val wrap = ctxClass.getMethod("javaToJS", Any::class.java, Class.forName("org.mozilla.javascript.Scriptable"))
            wrap.invoke(ctx, a, scope) ?: a
        }.getOrDefault(a)
    }

    private fun rhinoToString(r: Any?): String {
        if (r == null) return ""
        return runCatching {
            val cls = Class.forName("org.mozilla.javascript.ScriptRuntime")
            val m = cls.getMethod("toString", Class.forName("org.mozilla.javascript.Scriptable"))
            m.invoke(null, r) as? String ?: r.toString()
        }.getOrDefault(r.toString())
    }

    override fun close() {
        runCatching {
            val exit = ctxClass.getMethod("exit")
            exit.invoke(ctx)
        }
        ctx = null; scope = null
    }
}
