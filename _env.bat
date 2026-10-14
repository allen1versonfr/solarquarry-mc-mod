@echo off
rem Uses a portable JDK 25 in .jdk if present, otherwise the system Java.
if exist "%~dp0.jdk" (
  for /d %%D in ("%~dp0.jdk\jdk-25*") do set "JAVA_HOME=%%~fD"
)
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
