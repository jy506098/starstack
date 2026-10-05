@echo off
cd /d "%~dp0"
if exist world\session.lock del world\session.lock
start "MC-Server" "C:\Program Files\Java\jdk-26.0.2.1\bin\java.exe" -javaagent:authlib-injector.jar=https://littleskin.cn/api/yggdrasil -Xms1G -Xmx4G -jar server.jar --safeMode nogui
exit