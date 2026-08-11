package com.tvbox.app.android

import android.app.Application
import com.tvbox.core.di.ServiceLocator
import com.tvbox.platform.android.AndroidDeviceApi
import com.tvbox.platform.android.spider.AndroidSpiderLoader

/**
 * Android 应用入口 Application
 *
 * 在应用启动时创建平台对应的 [AndroidDeviceApi] 并完成全局依赖注入初始化，
 * 随后注入 [AndroidSpiderLoader] 以支持 Jar/JS 爬虫的真实加载。
 */
class TVBoxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(AndroidDeviceApi(this))
        // 注入 Android 平台 SpiderLoader（DexClassLoader + QuickJS）
        ServiceLocator.setSpiderLoader(AndroidSpiderLoader(this))
    }
}
