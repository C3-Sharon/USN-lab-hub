@echo off
REM W38 shoot batch
set BASE=C:\Users\yueyanxi\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\work-mode-projects\6a4f2d1cc136385614c38b39\USN-lab-hub
set PORT=4174
set NODE=C:\Program Files\nodejs\node.exe
set EDGE="C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"

REM 1) Start server in same process group via cmd /c start /B
start /B "" "%NODE%" "%BASE%\frontend\.scratch\w38-spa-server.cjs"

REM 2) Wait for server
:wait
timeout /t 1 /nobreak >nul
netstat -an | findstr ":%PORT% " | findstr "LISTENING" >nul
if errorlevel 1 goto wait

echo server ready

REM 3) shoot
call :shoot 1440 900
call :shoot 1280 800
call :shoot 390 844

REM 4) Cleanup
taskkill /F /IM node.exe >nul 2>&1
echo DONE
goto :eof

:shoot
set W=%1
set H=%2
set OUTROOT=%BASE%\docs\evidence\2026-W38\frontend\%W%x%H%
mkdir "%OUTROOT%\admin" "%OUTROOT%\teacher" "%OUTROOT%\member" "%OUTROOT%\public" 2>nul

for %%R in (admin teacher member) do (
  for %%N in (01-login 02-workbench 04-admin-members 05-admin-attendance 06-iot-devices 07-iot-projects 08-iot-alerts 09-iot-commands 10-iot-logs 11-iot-device-detail 12-iot-project-detail) do (
    call :one %W% %H% "%%R" "%%N" "http://127.0.0.1:%PORT%/seed-%%R.html" "%OUTROOT%\%%R\%%N.png"
  )
)
for %%N in (01-login 03-iot-public) do (
  if "%%N"=="01-login" (
    call :one %W% %H% "public" "%%N" "http://127.0.0.1:%PORT%/login" "%OUTROOT%\public\%%N.png"
  ) else (
    call :one %W% %H% "public" "%%N" "http://127.0.0.1:%PORT%/iot/public" "%OUTROOT%\public\%%N.png"
  )
)
goto :eof

:one
set W=%1
set H=%2
set R=%3
set N=%4
set URL=%5
set OUT=%6
set PROF=%TEMP%\edge-w38-%R%-%W%x%H%-%N%-%RANDOM%
mkdir "%PROF%" 2>nul
%EDGE% --headless --disable-gpu --no-sandbox --hide-scrollbars --window-size=%W%,%H% --user-data-dir="%PROF%" --virtual-time-budget=20000 --screenshot="%OUT%" %URL% >nul 2>nul
if exist "%OUT%" (
  echo OK   %W%x%H%  %R%  %N%
) else (
  echo FAIL %W%x%H%  %R%  %N%
)
rmdir /S /Q "%PROF%" 2>nul
goto :eof
