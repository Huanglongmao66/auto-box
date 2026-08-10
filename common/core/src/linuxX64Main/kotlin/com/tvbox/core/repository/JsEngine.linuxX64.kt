package com.tvbox.core.repository

/**
 * linuxX64 Native 平台：不支持 JS 引擎；SpiderJsSourceClient 调用时会走空结果降级。
 */
actual object JsEngineFactory {
    actual val isSupported: Boolean get() = false
    actual fun create(): JsEngine? = null
}
