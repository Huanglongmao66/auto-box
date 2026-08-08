# TVBox 开源项目多平台适配重构完整方案
## 一、重构核心目标

1. 架构解耦：剥离硬编码安卓专属 API，抽象统一跨平台业务层

2. 多端适配：Android TV / 手机、TV 盒子 Linux、Windows、macOS、Web 端、TVOS 适配

3. 分层标准化：业务、播放器、UI、数据源、底层系统 API 完全隔离

4. 统一业务逻辑：解析源、订阅、收藏、播放历史、搜索、分类一套代码多端运行

5. 统一构建流水线：一键打包各平台安装包，复用 90% 核心业务代码

6. 兼容原有 TVBox 开源接口规则（json 源、聚合、嗅探、解析规则不改动，保证现有影视源全部可用）

## 二、原有 TVBox 原生痛点（重构重点解决）

1. 强耦合 Android Framework，Activity、SurfaceView、Android 专属存储 / 权限无法跨平台

2. 播放器硬编码 ExoPlayer 仅支持安卓，无跨平台播放器抽象层

3. UI 页面全部基于 Android XML/Compose Android，桌面、Web 无法复用

4. 文件存储、权限、网络、通知、投屏全部绑定安卓系统 API

5. 构建仅支持 Android Gradle，无跨端打包方案

6. 缺少编译开关，无法按需编译对应平台，冗余代码多

## 三、整体分层架构（跨平台通用分层，AI 重构严格遵循）

### 分层从上至下：UI 适配层 → 统一业务核心层 → 抽象设备 SDK 层 → 跨平台工具底座

1. **UI 适配层（各平台独立实现，最小量代码）**
职责：仅做当前平台界面渲染、页面跳转、控件交互，无任何业务逻辑
各平台实现：

    - Android 端：Compose Android / 原生 XML（兼容原有 TV 布局焦点逻辑）

    - Windows/macOS 桌面：Compose Multiplatform \(CMP\)

    - Web 端：Compose Web / Ktor 前端

    - Linux TV 盒子：CMP \+ OpenGL 渲染
    约束：所有点击、焦点、播放指令统一回调给上层业务层，不处理影视解析、缓存逻辑

2. **统一业务核心层（全平台 100% 复用，TVBox 核心逻辑全部放这里）**
纯 Kotlin 多平台公共代码 `common:core`，无任何平台 API 依赖
包含完整 TVBox 原生业务模块：

    - 数据源解析模块：json 源、聚合、在线订阅、本地导入、规则嗅探、代理解析

    - 资源管理：收藏、播放历史、观看进度、缓存、分类、搜索、热搜

    - 播放调度：播放队列、选集、倍速、字幕、画质切换、缓存预加载

    - 工具通用逻辑：正则解析、网页抓取、防盗链处理、接口加密解密
    输出标准接口，UI 层仅调用接口，不修改内部逻辑

3. **抽象设备 SDK 层（关键跨平台解耦层，接口定义 \+ 各平台实现）**
定义全平台统一抽象接口 `DeviceApi`，所有系统操作统一抽象，各平台单独实现：

    ```kotlin
    // 统一抽象接口示例（AI自动生成完整接口）
    interface DeviceApi {
        // 文件存储
        fun getCacheDir(): String
        fun getDocumentDir(): String
        fun readFile(path: String): String
        fun writeFile(path: String, content: String)
        // 网络
        fun httpClient(): HttpClient
        // 播放器抽象
        fun createPlayer(): IPlayer
        // 系统权限
        fun requestStoragePermission(): Boolean
        // 弹窗/Toast提示
        fun showToast(msg: String)
        // 投屏、系统播放、剪贴板、日志、桌面快捷方式、版本更新
        fun castVideo(url: String)
        fun copyClipboard(text: String)
        fun openExternalPlayer(url: String)
    }
    ```

    各平台独立实现：AndroidDeviceImpl、DesktopDeviceImpl、WebDeviceImpl、LinuxDeviceImpl
    业务层只依赖抽象接口，不感知底层操作系统

4. **跨平台播放器抽象层 IPlayer（完全剥离 ExoPlayer）**
统一播放器标准接口，屏蔽底层播放器内核差异

    ```kotlin
    interface IPlayer {
        fun setDataSource(url: String, subtitle: String)
        fun play()
        fun pause()
        fun seekTo(time: Long)
        fun setSpeed(speed: Float)
        fun release()
        fun setVideoView(renderView: Any) // 各平台渲染控件
        fun addPlayerListener(listener: PlayerListener)
    }
    ```

    多平台播放器实现方案：

    - Android：ExoPlayer / Media3

    - Windows/macOS/Linux：VLC、MPV、FFmpeg

    - Web 端：HTML5 Video \+ hls\.js
    业务层播放逻辑完全复用，切换播放器内核无需改动业务代码

5. **底层跨平台工具底座（Kotlin Multiplatform KMP）**
通用无平台依赖工具：日期、加密、MD5、正则、JSON 序列化、线程调度、协程网络
使用 Ktor 网络、kotlinx\-serialization、kotlinx\-coroutines，全平台兼容

## 四、项目模块拆分（Gradle KMP 多模块工程，AI 自动重构目录）

```Plain Text
tvbox-multi/
├── build.gradle.kts  // 根构建脚本，统一多平台打包配置
├── settings.gradle.kts
├── common/
│   ├── core          # 【全平台复用】TVBox完整业务核心（数据源/收藏/历史/解析）
│   ├── device-api    # 设备抽象接口、播放器抽象接口定义
│   ├── utils         # 跨平台通用工具类
├── platform/
│   ├── android       # Android TV/手机端实现（DeviceApi、ExoPlayer、Android UI）
│   ├── desktop       # Windows/macOS/Linux桌面端（CMP UI、VLC播放器）
│   ├── web           # Web网页版（Compose Web、html5播放器）
│   ├── linux-tv      # 嵌入式Linux电视盒子专用适配
├── app/
│   ├── app-android   # Android打包入口，兼容原生TVBox安装包
│   ├── app-desktop   # Windows exe / macOS dmg / Linux deb打包入口
│   ├── app-web       # Web打包静态资源
│   ├── app-linux-tv  # 嵌入式Linux可执行文件
├── feature/          # 可选扩展模块（投屏、更新、云同步）
└── scripts/          # 一键打包脚本：打包各平台、签名、混淆、输出安装包
```

## 五、多平台适配重点重构改造项（逐条 AI 落地执行）

### 1\. 存储系统重构（彻底去掉 Android Context 专属存储）

1. 抽象存储接口 `StorageManager`，区分缓存目录、用户数据目录、源文件目录

2. 各平台目录规则：

    - Android：`context.filesDir` / `context.cacheDir`

    - Windows：`%APPDATA%/TVBox`

    - macOS：`~/Library/Application Support/TVBox`

    - Linux：`~/.local/share/tvbox`

    - Web：LocalStorage \+ IndexDB 持久化

3. 历史记录、收藏、订阅源统一使用 SQLite 跨平台库（skia\-sqlite，全平台支持）

4. 原有安卓 xml 配置文件迁移为通用 json 配置，全平台读取兼容

### 2\. 播放器模块重构（跨平台播放核心）

1. 删除全部直接依赖 ExoPlayer 的业务代码，全部调用 IPlayer 抽象接口

2. 统一字幕解析、多音轨、倍速、硬解码控制逻辑放在 common 核心层

3. 编译可选播放器内核：编译参数切换 MPV/VLC/ExoPlayer，减少安装包体积

4. 焦点适配统一抽象：TV 遥控器上下左右焦点逻辑封装通用工具，桌面端键鼠、Web 鼠标自动兼容

### 3\. UI 层重构：Compose Multiplatform 统一界面

1. 原有 Android Compose 页面全部迁移至 common 公共 Compose 组件（无安卓专属控件）

2. 区分两套布局模式自动适配：

    - TV 大屏模式：焦点导航、大间距、遥控器适配

    - 移动端 / 桌面小窗模式：鼠标 / 触摸适配

3. 平台独有控件仅在对应 platform 层实现（如安卓通知、桌面窗口缩放）

4. 主题、换肤、字体、缩放比例一套配置全平台生效

### 4\. 网络与解析模块完全跨平台兼容

1. 替换 Android OkHttp 为 Ktor HttpClient 跨平台网络库

2. 网页嗅探、防盗链、cookie 管理、代理设置统一抽象

3. JS 网页解析引擎适配：

    - Android：WebView

    - 桌面 / Linux：Ktor JS 引擎 / Qt WebEngine

    - Web 端：浏览器原生 JS

4. 影视源 json 规则完全兼容原版 TVBox，无需修改任何第三方影视源

### 5\. 权限、系统能力抽象适配

所有系统能力统一抽象，业务层无需判断平台：

- 存储读写权限

- 网络代理 / VPN

- 剪贴板复制分享

- 投屏、DLNA

- 外部播放器唤起

- 应用版本自动更新

- 日志导出、崩溃日志收集

## 六、多平台构建打包方案（自动化脚本）

### 1\. Android 端（兼容原版 TVBox）

输出：arm64\-v8a /x86\_64 APK，支持手机、电视盒子、Android TV
特性：保留原有安装逻辑、文件导入、U 盘读取

### 2\. 桌面端 Windows/macOS/Linux

基于 Compose Multiplatform 打包：

- Windows：exe 安装包 \+ 绿色免安装版

- macOS：dmg 镜像

- Linux：deb 安装包、arm 盒子可执行文件
内置 MPV/VLC 播放器，无需额外安装解码器

### 3\. Web 网页端

打包静态 html/js，支持浏览器直接打开，部署 Nginx 即可使用
支持网页全屏播放、键盘遥控器快捷键、本地导入影视源 json

### 4\. 嵌入式 Linux 电视盒子（晶晨 / 海思 TV 盒子）

编译独立可执行二进制文件，轻量无依赖，适配 arm64 嵌入式系统

## 七、编译开关与按需裁剪（减少各平台包体积）

在 gradle 配置编译参数，按需开启 / 关闭功能：

1. `-Pplatform=android/desktop/web/linux` 指定编译平台

2. `-Pplayer=exo/mpv/vlc` 切换播放器内核

3. `-Dfeature_cast=true/false` 是否开启投屏

4. `-Dfeature_update=true/false` 是否内置自动更新

5. `-Dminify=true` 开启代码混淆压缩

## 八、兼容保障规则（重构不破坏原有生态）

1. 完全兼容原版 TVBox 影视订阅源、本地 json 配置、历史记录数据（自动迁移旧数据）

2. 页面功能、操作逻辑、快捷键与原版 TVBox 保持一致，用户无学习成本

3. 第三方插件、解析规则、嗅探脚本 100% 兼容

4. 安卓端保留全部原有特性，可直接替换原版 APK 使用

## 九、重构交付物清单（AI 完成重构后输出）

1. KMP 多平台完整工程代码，分层清晰无平台耦合

2. 各平台一键打包 shell/bat 脚本

3. 跨平台适配文档：各平台编译、部署、打包教程

4. 抽象接口 API 文档（DeviceApi、IPlayer）

5. 数据迁移工具：旧安卓 TVBox 配置一键导入多端版本

6. 编译参数配置说明、功能裁剪配置说明

## 十、扩展优化方向（重构完成后迭代）

1. 多端数据云同步（收藏、播放进度跨 Windows / 安卓 / Web 互通）

2. 嵌入式 TVOS 适配（华为 / 小米电视系统独立适配层）

3. 服务端模式：Linux 后台运行，多设备网页远程控制播放

4. 硬件解码统一调度，自动适配各平台 GPU 硬解

5. 轻量化纯命令行版本（无 UI，服务器批量解析影视资源）


