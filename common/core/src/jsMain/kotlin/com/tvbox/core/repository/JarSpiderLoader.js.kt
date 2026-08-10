package com.tvbox.core.repository

/** Web(JS): 不支持 JAR */
actual object JarSpiderLoader {
    actual val isSupported: Boolean get() = false
    actual fun load(source: MovieSource, http: HttpFetcher): JarSpiderHandle? = null
}
