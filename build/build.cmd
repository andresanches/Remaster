@echo off
del Remaster.jar
javac -sourcepath ..\ -d .\ -g:none ..\*.java
jar c0fe Remaster.jar Remaster *.class
del *.class
