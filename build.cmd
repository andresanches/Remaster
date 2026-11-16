@echo off
del build\Remaster.jar
if not exist build mkdir build
javac -d build\classes -g:none .\*.java
jar c0fe build\Remaster.jar Remaster -C build\classes .
del build\classes\*.class
