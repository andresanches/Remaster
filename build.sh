rm -f build/Remaster.jar
mkdir -p build
javac -d build/classes -g:none -Xlint:deprecation ./*.java
jar c0fe build/Remaster.jar Remaster -C build/classes .
#rm -r build/classes
