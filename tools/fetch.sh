#!/bin/sh
# Downloads the build toolchain jars (compile-time API stubs + ProGuard).
# These are gitignored; run this once after cloning.
set -e
cd "$(dirname "$0")"

ME="https://repo1.maven.org/maven2/org/microemu"
PG="https://repo1.maven.org/maven2/net/sf/proguard/proguard-base"

echo "fetching microemu-cldc..."
curl -fsSL -o microemu-cldc.jar "$ME/microemu-cldc/2.0.4/microemu-cldc-2.0.4.jar"
echo "fetching microemu-midp..."
curl -fsSL -o microemu-midp.jar "$ME/microemu-midp/2.0.4/microemu-midp-2.0.4.jar"
echo "fetching proguard..."
curl -fsSL -o proguard.jar "$PG/6.2.2/proguard-base-6.2.2.jar"

echo "done:"
ls -la *.jar
