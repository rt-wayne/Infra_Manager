@echo off
REM Infra Manager (Java) start script - S1
REM 1. build frontend (npm) into the web shell jar
REM 2. package backend API jar and web shell jar (unit tests skipped here; run mvnw verify separately)
REM 3. stop any java.exe still listening on the two ports, then start both jars in their own windows
REM Ports: backend API 3202, web shell 3201 (old Node system stays on 3200)
REM Requires JDK 25 (JAVA_HOME) and Node 24. No Chinese in this file (cp950/utf-8 issue).
chcp 65001 >nul

cd /d %~dp0

set API_PORT=3202
set WEB_PORT=3201
set ROOT=%~dp0
set API_DIR=%ROOT%infra_manager_java
set WEB_DIR=%ROOT%infra_manager_web\infra_manager_web
set FE_DIR=%ROOT%infra_manager_web\infra_manager_web_frontend

if "%JAVA_HOME%"=="" (
  echo [ERROR] JAVA_HOME is not set. Install JDK 25 and set JAVA_HOME.
  pause
  exit /b 1
)
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERROR] JAVA_HOME does not point to a JDK: %JAVA_HOME%
  pause
  exit /b 1
)

if "%1"=="--no-build" goto :start

echo [INFO] Building frontend ...
pushd "%FE_DIR%"
if not exist node_modules (
  echo [INFO] Installing frontend dependencies ...
  call npm install
  if errorlevel 1 (
    echo [ERROR] npm install failed.
    popd
    pause
    exit /b 1
  )
)
call npm run build
if errorlevel 1 (
  echo [ERROR] npm run build failed.
  popd
  pause
  exit /b 1
)
popd

echo [INFO] Packaging backend API ...
pushd "%API_DIR%"
call mvnw.cmd -q clean package -DskipTests
if errorlevel 1 (
  echo [ERROR] backend package failed.
  popd
  pause
  exit /b 1
)
popd

echo [INFO] Packaging web shell ...
pushd "%WEB_DIR%"
call mvnw.cmd -q clean package -DskipTests
if errorlevel 1 (
  echo [ERROR] web shell package failed.
  popd
  pause
  exit /b 1
)
popd

:start
set KILLED=
echo [INFO] Checking ports %API_PORT% and %WEB_PORT% ...
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /C:":%API_PORT% " ^| findstr /C:"LISTENING"') do (
  if not "%%P"=="0" call :stop_pid %%P %API_PORT%
)
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /C:":%WEB_PORT% " ^| findstr /C:"LISTENING"') do (
  if not "%%P"=="0" call :stop_pid %%P %WEB_PORT%
)
if defined KILLED (
  echo [INFO] Waiting for ports to be released ...
  ping -n 4 127.0.0.1 >nul 2>&1
)

set BUSY=
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /C:":%API_PORT% " ^| findstr /C:"LISTENING"') do set BUSY=%%P
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /C:":%WEB_PORT% " ^| findstr /C:"LISTENING"') do set BUSY=%%P
if defined BUSY (
  echo.
  echo [ERROR] Port %API_PORT% or %WEB_PORT% is still in use by PID %BUSY%.
  echo [ERROR] Inspect it with:  tasklist /FI "PID eq %BUSY%"
  echo [ERROR] Then stop it with: taskkill /F /PID %BUSY%
  echo.
  pause
  exit /b 1
)

for %%F in ("%API_DIR%\target\infra_manager_java-*.jar") do set API_JAR=%%F
for %%F in ("%WEB_DIR%\target\infra_manager_web-*.jar") do set WEB_JAR=%%F
if not defined API_JAR (
  echo [ERROR] backend jar not found under %API_DIR%\target. Run without --no-build first.
  pause
  exit /b 1
)
if not defined WEB_JAR (
  echo [ERROR] web shell jar not found under %WEB_DIR%\target. Run without --no-build first.
  pause
  exit /b 1
)

echo [INFO] Starting backend API on %API_PORT% ...
start "infra_manager_java (API %API_PORT%)" /D "%API_DIR%" "%JAVA_HOME%\bin\java.exe" -jar "%API_JAR%"
ping -n 3 127.0.0.1 >nul 2>&1

echo [INFO] Starting web shell on %WEB_PORT% ...
start "infra_manager_web (WEB %WEB_PORT%)" /D "%WEB_DIR%" "%JAVA_HOME%\bin\java.exe" -jar "%WEB_JAR%"

echo.
echo [INFO] Open: http://localhost:%WEB_PORT%/infra_manager_web/
echo [INFO] Logs: D:\home\tomcat\log\infra_manager_java\ and D:\home\tomcat\log\infra_manager_web\ (drive follows the working directory)
echo.
exit /b 0

REM ---------------------------------------------------------------------------
REM :stop_pid <pid> <port>
REM Kills the PID only when its command line shows it is one of THIS project's jars
REM (infra_manager_java-*.jar or infra_manager_web-*.jar). Any other process holding the
REM port is left untouched and reported. Stays silent if the PID is already gone.
REM The match runs entirely inside PowerShell and only an exit code comes back
REM (0 = ours, 1 = someone else's, 2 = already gone), so the other process's command
REM line never reaches cmd and cannot be re-interpreted as commands (quotes, &, |, >).
REM ---------------------------------------------------------------------------
:stop_pid
set TARGET=%1
set TPORT=%2
echo %TARGET%| findstr /R "^[0-9][0-9]*$" >nul || goto :eof
powershell -NoProfile -Command "$p = Get-CimInstance Win32_Process -Filter 'ProcessId=%TARGET%'; if (-not $p) { exit 2 } elseif ($p.CommandLine -match 'infra_manager_(java|web)-') { exit 0 } else { exit 1 }" >nul 2>&1
if errorlevel 2 goto :eof
if errorlevel 1 goto :not_ours
echo [INFO] Stopping previous instance on %TPORT%: PID %TARGET%
taskkill /F /PID %TARGET% >nul 2>&1
set KILLED=1
goto :eof
:not_ours
echo [WARN] PID %TARGET% holds port %TPORT% but is NOT this project's jar - left untouched.
echo [WARN] Inspect it with: tasklist /FI "PID eq %TARGET%"
goto :eof
