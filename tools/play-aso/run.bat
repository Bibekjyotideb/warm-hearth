@echo off
cd /d "%~dp0"
where node >nul 2>nul || (echo Install Node.js LTS from nodejs.org first & pause & exit /b)
if not exist node_modules (echo Installing... & call npm install --silent)
node score.mjs %1
echo.
echo Results are in the "out" folder.
pause
