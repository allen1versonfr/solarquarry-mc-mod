@echo off
cd /d "%~dp0"
call "%~dp0_env.bat"
rem Close a dev client started from this project that may still be open.
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -match 'devlaunchinjector' -and $_.CommandLine -match 'SolarQuarry' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"
if exist sync.zip (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path sync.zip -DestinationPath . -Force"
  del /q sync.zip
)
if not exist logs mkdir logs
del /q logs\clienttest.done 2>nul
if exist run-clienttest\screenshots rmdir /s /q run-clienttest\screenshots
call gradlew.bat --no-daemon runClientGameTest > logs\clienttest.log 2>&1
echo %ERRORLEVEL% > logs\clienttest.done
