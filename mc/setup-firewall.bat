@echo off
REM ===========================================
REM StarStack MC 服务器防火墙放行脚本
REM 请右键此文件，选择 "以管理员身份运行"
REM ===========================================
title StarStack MC Server - Firewall Setup
chcp 65001 >nul

echo 正在为 Minecraft 服务器放行端口 25565...
echo.

netsh advfirewall firewall add rule ^
    name="Minecraft Server 25565 TCP" ^
    dir=in ^
    action=allow ^
    protocol=TCP ^
    localport=25565 ^
    profile=any

netsh advfirewall firewall add rule ^
    name="Minecraft Server 25565 UDP" ^
    dir=in ^
    action=allow ^
    protocol=UDP ^
    localport=25565 ^
    profile=any

echo.
echo ==========================================
echo 已添加以下防火墙规则:
netsh advfirewall firewall show rule name="Minecraft Server 25565 TCP"
netsh advfirewall firewall show rule name="Minecraft Server 25565 UDP"
echo ==========================================
echo.
echo 完成！现在 Minecraft 服务器的 25565 端口已对外开放。
pause