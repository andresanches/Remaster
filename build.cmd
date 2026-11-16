@echo off
del build\Remaster.jar
if not exist build mkdir build
javac -d build\classes -g:none .\*.java
if errorlevel 1 exit /b 1   rem stop on first error: don't jar stale classes when javac fails
jar c0fe build\Remaster.jar Remaster -C build\classes .
if errorlevel 1 exit /b 1
del build\classes\*.class
