@echo off
cd /d "%~dp0"
if not exist logs mkdir logs
del /q logs\jdk.done 2>nul
(
echo ==== downloading Temurin JDK 25
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol='Tls12'; Invoke-WebRequest -UseBasicParsing -Uri 'https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk' -OutFile 'jdk25.zip'; if(Test-Path .jdk){Remove-Item -Recurse -Force .jdk}; Expand-Archive -Path jdk25.zip -DestinationPath .jdk -Force; Remove-Item jdk25.zip; Get-ChildItem .jdk | ForEach-Object { $_.FullName }"
call "%~dp0_env.bat"
echo JAVA_HOME=%JAVA_HOME%
java -version
) > logs\jdk.log 2>&1
echo done > logs\jdk.done
