package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource

/** Web(JS): 不支持 JAR */
internal actual object JarSpiderLoader {
    actual val isSupported: Boolean get() = false
    actual fun load(source: MovieSource, http: HttpFetcher): JarSpiderHandle? = null
}
