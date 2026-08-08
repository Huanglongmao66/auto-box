#!/bin/bash
# TVBox Multiplatform 一键打包脚本
# 用法: ./scripts/build-all.sh [platform]
# platform: android | desktop | web | linux | all (默认 all)

set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

PLATFORM=${1:-all}
GRADLE_CMD="./gradlew"

# JDK 版本检查
JAVA_VERSION=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ] 2>/dev/null; then
    echo "错误: 需要 JDK 17 或更高版本，当前版本: $JAVA_VERSION"
    exit 1
fi

echo "========================================="
echo "  TVBox Multiplatform 打包脚本"
echo "  目标平台: $PLATFORM"
echo "  JDK 版本: $JAVA_VERSION"
echo "========================================="

build_android() {
    echo ""
    echo ">>> 编译 Android APK..."
    $GRADLE_CMD :app:app-android:assembleRelease
    echo ">>> Android APK 输出: app/app-android/build/outputs/apk/release/"
}

build_desktop() {
    echo ""
    echo ">>> 编译桌面端安装包..."
    $GRADLE_CMD :app:app-desktop:packageDistributionForCurrentOS
    echo ">>> 桌面端输出: app/app-desktop/build/compose/binaries/"
}

build_web() {
    echo ""
    echo ">>> 编译 Web 静态资源..."
    $GRADLE_CMD :app:app-web:browserProductionWebpack
    echo ">>> Web 输出: app/app-web/build/dist/js/productionExecutable/"
}

build_linux_tv() {
    echo ""
    echo ">>> 编译嵌入式 Linux 可执行文件..."
    $GRADLE_CMD :app:app-linux-tv:linkReleaseExecutableLinuxX64
    echo ">>> Linux TV 输出: app/app-linux-tv/build/bin/linuxX64/releaseExecutable/"
}

case "$PLATFORM" in
    android)
        build_android
        ;;
    desktop)
        build_desktop
        ;;
    web)
        build_web
        ;;
    linux)
        build_linux_tv
        ;;
    all)
        build_android
        build_desktop
        build_web
        build_linux_tv
        ;;
    *)
        echo "未知平台: $PLATFORM"
        echo "可用选项: android | desktop | web | linux | all"
        exit 1
        ;;
esac

echo ""
echo "========================================="
echo "  打包完成!"
echo "========================================="
