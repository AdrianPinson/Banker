@echo off
setlocal
cd /d "%~dp0"
if defined JAVA_HOME (
  set "BANKER_JAVA=%JAVA_HOME%\bin\java.exe"
) else (
  set "BANKER_JAVA=java.exe"
)
echo Banker: open http://127.0.0.1:8080 after the server starts (unless you changed the port).
echo Local demo data is in the data folder next to this launcher unless BANKER_DB_URL overrides it.
echo Keep this terminal open. Press Ctrl+C to stop Banker.
"%BANKER_JAVA%" -jar "%~dp0banker.jar" %*
set "BANKER_EXIT=%ERRORLEVEL%"
if not "%BANKER_EXIT%"=="0" (
  echo.
  echo Banker stopped with an error. Check the message above. Java 17 or newer is required.
  if not defined BANKER_NONINTERACTIVE pause
)
exit /b %BANKER_EXIT%
