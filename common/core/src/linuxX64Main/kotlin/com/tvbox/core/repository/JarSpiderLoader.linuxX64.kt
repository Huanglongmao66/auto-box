package com.tvbox.core.repository

/** linuxX64(Native): 不支持 JAR */
internal actual object JarSpiderLoader {
    actual val isSupported: Boolean get() = false
    actual fun load(source: MovieSource, http: HttpFetcher): JarSpiderHandle? = null
}
