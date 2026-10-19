@echo off
rem Builds the Cesium launcher for Windows: shaded jar, then a portable app folder with Cesium.exe.
rem Needs JDK 21 (java, mvn and jpackage on PATH). For a setup .exe installer add WiX 3 and use --type exe.

cd /d "%~dp0"

call mvn -q clean package
if errorlevel 1 (
    echo [cesium] maven build failed
    exit /b 1
)

if exist dist rmdir /s /q dist

jpackage ^
    --type app-image ^
    --name Cesium ^
    --app-version 1.0.0 ^
    --input target ^
    --main-jar cesium-launcher.jar ^
    --main-class dev.cesium.launcher.Main ^
    --dest dist
if errorlevel 1 (
    echo [cesium] jpackage failed
    exit /b 1
)

echo [cesium] done: %~dp0dist\Cesium\Cesium.exe
