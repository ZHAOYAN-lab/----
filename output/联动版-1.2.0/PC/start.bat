@echo off
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel% equ 0 (
  py -3 server.py --port 8088 --auto-port --open
) else (
  python server.py --port 8088 --auto-port --open
)
pause
