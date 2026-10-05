@echo off
chcp 65001 >nul
cd /d "%~dp0"

REM ============================================================
REM jy_jy 的 LittleSkin 外置登录启动脚本
REM 服务器地址改成实际的（默认 starstack.cn:25565）
REM ============================================================

set SERVER_HOST=starstack.cn
set SERVER_PORT=25565
set LITTLE_SKIN=https://littleskin.cn/api/yggdrasil

REM 找到 java.exe（按优先级：JDK 26、Java 21、Java 17）
set "JAVA_EXE="
if exist "C:\Program Files\Java\jdk-26.0.2.1\bin\javaw.exe" set "JAVA_EXE=C:\Program Files\Java\jdk-26.0.2.1\bin\javaw.exe"
if "%JAVA_EXE%"=="" if exist "C:\Program Files\Eclipse Adoptium\jdk-21\bin\javaw.exe" set "JAVA_EXE=C:\Program Files\Eclipse Adoptium\jdk-21\bin\javaw.exe"
if "%JAVA_EXE%"=="" if exist "C:\Program Files\Java\jre1.8.0_xxx\bin\javaw.exe" set "JAVA_EXE=C:\Program Files\Java\jre1.8.0_xxx\bin\javaw.exe"
if "%JAVA_EXE%"=="" (
    for /f "delims=" %%i in ('where javaw 2^>nul') do (
        if "%JAVA_EXE%"=="" set "JAVA_EXE=%%i"
    )
)
if "%JAVA_EXE%"=="" (
    echo [ERROR] 未找到 javaw.exe，请安装 JDK 后再试。
    pause
    exit /b 1
)

if not exist "authlib-injector.jar" (
    echo [ERROR] 当前目录缺少 authlib-injector.jar
    echo 请把 authlib-injector.jar 放到 %CD%
    pause
    exit /b 1
)

REM ============================================================
REM 内存设置（和服务器一致：-Xms1G -Xmx4G）
REM ============================================================
set "MEMORY=-Xms512M -Xmx2G"

REM ============================================================
REM 启动 Minecraft 主类（带 authlib-injector）
REM 如果你的客户端是 HMCL / PCL 等启动器导出的 .version 目录，
REM 请把下面 -cp 指向实际的 versions\<ver>\<ver>.jar
REM ============================================================
set "MC_JAR=versions\1.20.1\1.20.1.jar"

if not exist "%MC_JAR%" (
    echo [WARN] 默认路径 %MC_JAR% 不存在。
    echo 请编辑本脚本，把 MC_JAR 改成你客户端 versions 目录里的 jar 路径。
    echo.
    echo 当前目录: %CD%
    echo 现有的 versions 目录:
    if exist versions (
        dir /b versions
    ) else (
        echo   (没有 versions 目录)
    )
    pause
    exit /b 1
)

"%JAVA_EXE%" ^
    %MEMORY% ^
    -javaagent:authlib-injector.jar=%LITTLE_SKIN% ^
    -Dauthlibinjector.yggdrasil.prefetched=%LITTLE_SKIN% ^
    -cp "%MC_JAR%" ^
    net.minecraft.client.main.Main ^
    --version 1.20.1 ^
    --gameDir "%CD%\.minecraft-jy_jy" ^
    --assetsDir "%CD%\.minecraft-jy_jy\assets" ^
    --assetIndex 1.20.1 ^
    --username jy_jy ^
    --uuid 74fd864f-b249-381e-9e46-0e25abe54fe2 ^
    --accessToken offline-jy_jy ^
    --userType mojang ^
    --versionType "StarStack/LittleSkin" ^
    --width 854 ^
    --height 480 ^
    --server %SERVER_HOST% ^
    --port %SERVER_PORT%

pause