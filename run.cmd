@echo off
setlocal
cd /d "%~dp0"
if not exist "target\opencraft-0.1.0.jar" (
  call build.cmd
  if errorlevel 1 exit /b 1
)
java -jar "target\opencraft-0.1.0.jar" %*
exit /b %errorlevel%
