@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Install Java 17 or newer, reopen this window, and run this launcher again.
  pause
  exit /b 1
)
java -jar "campus-register.jar"
if errorlevel 1 (
  echo Check your Java version and the error above. See README.md for setup help.
  pause
  exit /b 1
)
