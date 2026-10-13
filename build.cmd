@echo off
setlocal
cd /d "%~dp0"
where javac >nul 2>&1
if errorlevel 1 (
  echo Install JDK 21 and add its bin folder to PATH.
  exit /b 1
)
call mvn --batch-mode --no-transfer-progress --strict-checksums verify %*
exit /b %errorlevel%
