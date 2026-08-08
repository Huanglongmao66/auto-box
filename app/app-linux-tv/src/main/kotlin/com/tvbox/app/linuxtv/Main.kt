package com.tvbox.app.linuxtv

import com.tvbox.core.di.ServiceLocator
import com.tvbox.platform.linuxtv.LinuxTvDeviceApi

/**
 * 嵌入式 Linux TV 应用入口
 *
 * 初始化依赖注入并启动 TVBox 主服务
 */
fun main() {
    println("=========================================")
    println("  TVBox for Linux TV v1.0.0")
    println("=========================================")

    // 初始化依赖注入
    val deviceApi = LinuxTvDeviceApi()
    ServiceLocator.initialize(deviceApi)

    println("TVBox 启动完成，等待指令...")

    // 主循环（嵌入式环境简易交互）
    while (true) {
        print("tvbox> ")
        val input = readlnOrNull()?.trim() ?: break

        when (input) {
            "exit", "quit" -> {
                println("正在退出...")
                break
            }
            "version" -> {
                println("TVBox v${deviceApi.getAppVersion()}")
            }
            "platform" -> {
                println("Platform: ${deviceApi.getPlatform()}")
                println("Device: ${deviceApi.getDeviceName()}")
            }
            "help" -> {
                println("""
                    |可用命令:
                    |  version  - 显示版本信息
                    |  platform - 显示平台信息
                    |  exit     - 退出应用
                    |  help     - 显示帮助
                """.trimMargin())
            }
            else -> {
                if (input.isNotEmpty()) {
                    println("未知命令: $input (输入 help 查看帮助)")
                }
            }
        }
    }
}
