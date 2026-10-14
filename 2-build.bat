@echo off
cd /d "%~dp0"
call "%~dp0_env.bat"
if exist sync.zip (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path sync.zip -DestinationPath . -Force"
  del /q sync.zip
)
if not exist logs mkdir logs
del /q logs\build.done 2>nul
call gradlew.bat --no-daemon build > logs\build.log 2>&1
echo %ERRORLEVEL% > logs\build.done
