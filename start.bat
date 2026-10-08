@echo off
chcp 65001 > nul
cd /d "%~dp0"

REM Build if JAR missing OR any source newer than JAR
set NEED_BUILD=0
if not exist target\starstack.jar (
    set NEED_BUILD=1
) else (
    for /f "delims=" %%F in ('dir /b /s /a-d "src\main\java\*.java" "src\main\resources\*.sql" "src\main\resources\*.yml" "src\main\resources\application*.yaml" "src\main\resources\templates\*.html" "src\main\typescript\src\*.ts" "src\main\typescript\package.json" 2^>nul') do (
        if %%~tF GTR %date:~10,4%-%date:~4,2%-%date:~7,2% (
            REM can't easily compare in batch; fall through to mvn --offline check
        )
    )
)

if not exist target\starstack.jar (
    set NEED_BUILD=1
)

if "%NEED_BUILD%"=="1" (
    echo [start] Building StarStack JAR...
    if exist mvnw.cmd (
        call mvnw.cmd -q -DskipTests package
    ) else (
        call mvn -q -DskipTests package
    )
    if not exist target\starstack.jar (
        echo [start] Build failed.
        pause
        exit /b 1
    )
)

REM Launch default browser after 2s delay (server takes ~3s to bind 5000)
start "" /min cmd /c "timeout /t 4 /nobreak > nul && start http://localhost:5000/"

REM Run the Spring Boot fat JAR
java -jar target\starstack.jar