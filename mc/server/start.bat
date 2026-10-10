@echo off
title StarStack MC Server
chcp 65001 > nul
cd /d "%~dp0"

REM MC 26.1+ 需要 Java 25+，强制使用 JDK 26（系统里有 jdk-26.0.2.1）
set "JAVA_EXE=C:\Program Files\Java\jdk-26.0.2.1\bin\java.exe"
if not exist "%JAVA_EXE%" (
    echo [WARN] 未找到 %JAVA_EXE%，回退到 PATH 中的 java（可能因版本过低启动失败）
    set "JAVA_EXE=java"
)
echo [java] 使用 %JAVA_EXE%
"%JAVA_EXE%" -version

echo.
echo [INFO] 在线模式（online-mode=true）+ authlib-injector → LittleSkin
echo [INFO] 玩家必须在启动器（HMCL / PCL2）里配置 LittleSkin 验证才能连服
echo [INFO] 正在启动 MC 服务端（后台日志: logs\latest.log）...
"%JAVA_EXE%" -Xms1G -Xmx4G -javaagent:authlib-injector.jar=https://littleskin.cn/api/yggdrasil -jar server.jar nogui
pause