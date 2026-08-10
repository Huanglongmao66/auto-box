@echo off
REM TVBox Multiplatform 一键打包脚本 (Windows)
REM 用法: scripts\build-all.bat [platform]
REM platform: android | desktop | web | linux | all

setlocal

set PLATFORM=%1
if "%PLATFORM%"=="" set PLATFORM=all

set PROJECT_DIR=%~dp0..
cd /d "%PROJECT_DIR%"

set GRADLE_CMD=gradlew.bat

echo =========================================
echo   TVBox Multiplatform 打包脚本
echo   目标平台: %PLATFORM%
echo =========================================

if "%PLATFORM%"=="android" goto build_android
if "%PLATFORM%"=="desktop" goto build_desktop
if "%PLATFORM%"=="web" goto build_web
if "%PLATFORM%"=="linux" goto build_linux_tv
if "%PLATFORM%"=="all" goto build_all

echo 未知平台: %PLATFORM%
echo 可用选项: android ^| desktop ^| web ^| linux ^| all
exit /b 1

:build_all
call :build_android
call :build_desktop
call :build_web
call :build_linux_tv
goto done

:build_android
echo.
echo ^>^>^> 编译 Android APK...
call %GRADLE_CMD% :app:app-android:assembleRelease
echo ^>^>^> Android APK 输出: app\app-android\build\outputs\apk\release\
goto :eof

:build_desktop
echo.
echo ^>^>^> 编译桌面端安装包...
call %GRADLE_CMD% :app:app-desktop:packageDistributionForCurrentOS
echo ^>^>^> 桌面端输出: app\app-desktop\build\compose\binaries\
goto :eof

:build_web
echo.
echo ^>^>^> 编译 Web 静态资源...
call %GRADLE_CMD% :app:app-web:jsBrowserProductionWebpack
echo ^>^>^> Web 输出: app\app-web\build\dist\js\productionExecutable\
goto :eof

:build_linux_tv
echo.
echo ^>^>^> 编译 Linux TV 可执行文件 (JVM distribution)...
call %GRADLE_CMD% :app:app-linux-tv:distZip
echo ^>^>^> Linux TV 输出: app\app-linux-tv\build\distributions\
goto :eof

:done
echo.
echo =========================================
echo   打包完成!
echo =========================================
endlocal
