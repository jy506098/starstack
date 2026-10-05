@echo off
echo === World Reset Script ===
echo Waiting for server to stop...

REM Loop: check for java.exe running server.jar, exit loop when none found
set /a count=0
:WAIT_LOOP
for /f "skip=1" %%p in ('wmic process where "name='java.exe' and commandline like '%%server.jar%%'" get ProcessId 2^>nul') do (
    if not "%%p"=="" goto CHECK_LOOP
)
echo Server stopped after %count% seconds.
goto CLEANUP

:CHECK_LOOP
set /a count+=1
if %count% lss 120 (
    timeout /t 1 /nobreak >nul
    goto WAIT_LOOP
) else (
    echo Server did not stop in 120 seconds, force killing java.exe (server.jar)...
    for /f "skip=1" %%p in ('wmic process where "name='java.exe' and commandline like '%%server.jar%%'" get ProcessId 2^>nul') do (
        if not "%%p"=="" (
            echo Killing PID %%p
            taskkill /F /PID %%p >nul 2>&1
        )
    )
    timeout /t 5 /nobreak >nul
)

:CLEANUP
REM Wait extra 3 seconds for file locks to release
timeout /t 3 /nobreak >nul

REM Delete all region files (these will regenerate from seed)
echo Deleting region files...
powershell -Command "Get-ChildItem -Path 'world\region' -Filter '*.mca' -ErrorAction SilentlyContinue | Remove-Item -Force"

REM Delete entity/poi files
powershell -Command "Get-ChildItem -Path 'world\entities' -Filter '*.mca' -ErrorAction SilentlyContinue | Remove-Item -Force"
powershell -Command "Get-ChildItem -Path 'world\poi' -Filter '*.mca' -ErrorAction SilentlyContinue | Remove-Item -Force"

echo World reset complete. Restarting server...

REM Restart server with Java 26 + authlib-injector (LittleSkin)
cd /d "%~dp0"
start "MC-Server" "C:\Program Files\Java\jdk-26.0.2.1\bin\java.exe" -javaagent:authlib-injector.jar=https://littleskin.cn/api/yggdrasil -Xms1G -Xmx4G -jar server.jar nogui

echo Done.
exit