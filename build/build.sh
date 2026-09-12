#!/bin/sh
# Runs INSIDE the temurin:8-jdk container with the project mounted at /work.
# Pipeline: compile stubs -> compile app (target 1.3) -> ProGuard preverify
# -> package MIDlet JAR + JAD.
set -e
cd /work

APP_NAME="AudioBook"

rm -rf out dist
mkdir -p out/stubclasses out/appclasses dist

CP="tools/microemu-cldc.jar:tools/microemu-midp.jar"

echo ">> [1/5] compiling J2ME stubs (compile-only, not shipped)"
find stubs -name '*.java' > out/stubs.list
javac -source 1.3 -target 1.3 -nowarn -classpath "$CP" \
    -d out/stubclasses @out/stubs.list
jar cf out/stubs.jar -C out/stubclasses .

echo ">> [2/5] compiling app"
find src -name '*.java' > out/src.list
javac -source 1.3 -target 1.3 -nowarn -classpath "$CP:out/stubs.jar" \
    -d out/appclasses @out/src.list

echo ">> [3/5] preverifying via ProGuard"
java -jar tools/proguard.jar @build/proguard.pro

echo ">> [4/5] packaging MIDlet JAR"
rm -rf out/final
mkdir -p out/final
( cd out/final && jar xf ../app-preverified.jar )
if [ -d res ]; then cp -r res/. out/final/ 2>/dev/null || true; fi
jar cfm "dist/$APP_NAME.jar" build/MANIFEST.MF -C out/final .

echo ">> [5/5] writing JAD"
JAR_SIZE=$(stat -c%s "dist/$APP_NAME.jar")
sed "s/@JAR_SIZE@/$JAR_SIZE/" build/template.jad > "dist/$APP_NAME.jad"

echo ">> DONE"
ls -la dist/
echo "--- JAD ---"
cat "dist/$APP_NAME.jad"
