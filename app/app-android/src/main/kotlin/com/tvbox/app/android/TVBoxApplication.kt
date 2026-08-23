package com.tvbox.app.android

import android.app.Application
import com.tvbox.core.di.ServiceLocator
import com.tvbox.platform.android.AndroidDeviceApi

/**
 * Android 应用入口 Application
 *
 * 在应用启动时创建平台对应的 [AndroidDeviceApi] 并完成全局依赖注入初始化，
 * 之后业务层即可通过 [ServiceLocator] 获取跨平台服务。
 */
class TVBoxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(AndroidDeviceApi(this))
    }
}
