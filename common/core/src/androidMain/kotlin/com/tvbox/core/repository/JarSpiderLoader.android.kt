package com.tvbox.core.repository

/**
 * Android: 采用 DexClassLoader 加载 jar/dex；如果系统/项目未配置 dex 权限 -> 降级不支持
 * 由于不同 Android 版本对动态加载 jar 的限制较严，且 ART 需要 dex 格式，
 * 这里保守返回 isSupported=false，实际调用会安全降级。需要完整支持时可在平台模块接入。
 */
actual object JarSpiderLoader {
    actual val isSupported: Boolean get() = false
    actual fun load(source: MovieSource, http: HttpFetcher): JarSpiderHandle? = null
}
