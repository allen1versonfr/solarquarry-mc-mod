@echo off
rem Stops a running Solar Quarry self-test server (only java processes started with -Dsolarquarry.selftest).
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -match 'solarquarry.selftest' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"
