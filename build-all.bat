@echo off
setlocal enabledelayedexpansion

set VERSIONS=1.17.1 1.18.2 1.19 1.19.1 1.19.2 1.19.3 1.19.4 1.20 1.20.1 1.20.2 1.20.3 1.20.4 1.20.5 1.20.6 1.21 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6 1.21.7 1.21.8 1.21.9 1.21.10 1.21.11

if not exist dist mkdir dist

for %%v in (%VERSIONS%) do (
    echo.
    echo === Minecraft %%v ===
    call gradlew.bat clean build -Pmc=%%v
    if errorlevel 1 (
        echo Build failed for %%v
        exit /b 1
    )
    copy /y "build\libs\pv-svc-bridge-*+mc%%v.jar" dist\ >nul
)

echo.
echo All builds finished, jars are in the dist folder
