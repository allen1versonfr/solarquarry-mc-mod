@echo off
cd /d "%~dp0"
call "%~dp0_env.bat"
rem Stop a previous self-test server that may still be running.
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -match 'solarquarry.selftest' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"
if exist sync.zip (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path sync.zip -DestinationPath . -Force"
  del /q sync.zip
)
if not exist logs mkdir logs
del /q logs\selftest.done 2>nul
if not exist run-selftest mkdir run-selftest
>run-selftest\eula.txt echo eula=true
>run-selftest\server.properties echo level-type=minecraft\:flat
>>run-selftest\server.properties echo online-mode=false
>>run-selftest\server.properties echo spawn-protection=0
>>run-selftest\server.properties echo pause-when-empty-seconds=0
if exist run-selftest\world rmdir /s /q run-selftest\world
call gradlew.bat --no-daemon runSelftest > logs\selftest.log 2>&1
echo %ERRORLEVEL% > logs\selftest.done
