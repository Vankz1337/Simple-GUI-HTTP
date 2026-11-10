@echo off
setlocal
cd /d "%~dp0"
if not exist target\classes mkdir target\classes
(for /r src\main\java %%f in (*.java) do @echo %%f) > target\sources.txt
javac --release 21 --add-modules jdk.httpserver -encoding UTF-8 -d target\classes @target\sources.txt
if errorlevel 1 exit /b 1
> target\manifest.mf echo Manifest-Version: 1.0
>> target\manifest.mf echo Main-Class: com.vankz.simpleguihttp.SimpleHttpGui
jar --create --file target\simple-gui-http.jar --manifest target\manifest.mf -C target\classes .
if errorlevel 1 exit /b 1
echo Built: target\simple-gui-http.jar
