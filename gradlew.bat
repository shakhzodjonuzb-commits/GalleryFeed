@echo off
setlocal
set GRADLE_VERSION=8.14.6
if not defined GRADLE_USER_HOME set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set DIST_DIR=%GRADLE_USER_HOME%\galleryfeed-distributions\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%DIST_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat
set ZIP_PATH=%DIST_DIR%\gradle-%GRADLE_VERSION%-bin.zip

if not exist "%GRADLE_BIN%" (
  if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
  echo Downloading Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP_PATH%'"
  if errorlevel 1 exit /b 1
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%ZIP_PATH%' -DestinationPath '%DIST_DIR%' -Force"
  if errorlevel 1 exit /b 1
)

call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%
