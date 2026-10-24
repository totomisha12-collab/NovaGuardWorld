@echo off
setlocal
set "APP_HOME=%~dp0"
set "GRADLE_VERSION=8.10.2"
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "DIST=%GRADLE_USER_HOME%\wrapper\dists\gradle-%GRADLE_VERSION%-bin\local"
set "GRADLE=%DIST%\gradle-%GRADLE_VERSION%\bin\gradle.bat"
if not exist "%GRADLE%" (
  if not exist "%DIST%" mkdir "%DIST%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%DIST%\gradle.zip'; Expand-Archive -Force '%DIST%\gradle.zip' '%DIST%'"
  if errorlevel 1 exit /b 1
)
call "%GRADLE%" -p "%APP_HOME%" %*
exit /b %ERRORLEVEL%
