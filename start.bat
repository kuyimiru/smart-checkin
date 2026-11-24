@echo off
setlocal
title Smart Check-in - Backend Service
cd /d "%~dp0backend"

echo ============================================
echo   Smart Check-in Mini Program - Backend
echo ============================================
echo.

:: 1. Check Java
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found. Please install JDK 17+
    pause
    exit /b 1
)

:: 2. Check MySQL
mysql -uroot -p123456 -e "SELECT 1" smart_checkin >nul 2>&1
if errorlevel 1 (
    echo [WARN] MySQL connection failed. Please check:
    echo   1. MySQL service is running
    echo   2. smart_checkin.sql has been imported
    echo   3. username/password in application.yml are correct
    echo.
    choice /c yn /m "Continue anyway? (y=yes n=no)"
    if errorlevel 2 exit /b 1
)

:: 3. Check port 8080 (a previous instance may still be running)
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
    echo [WARN] Port 8080 is already in use!
    echo        A previous backend instance may still be running.
    echo        To find and stop it, run in a new cmd window:
    echo            netstat -ano ^| findstr :8080
    echo            taskkill /F /PID ^<the-PID^>
    echo.
)

:: 4. Build jar (skip tests)
echo [BUILD] Building jar (skip tests)...
call mvn -DskipTests -q package
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed. See error messages above.
    pause
    exit /b 1
)

:: 5. Run
echo.
echo [START] Starting backend: http://localhost:8080
echo        Press Ctrl+C to stop
echo ============================================
echo.
java -jar target\smart-checkin-1.0.0.jar

echo.
echo [STOPPED] Backend has exited.
pause
