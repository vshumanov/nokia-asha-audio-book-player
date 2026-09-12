#!/bin/sh
# Host-side build wrapper: runs the J2ME build inside the JDK-8 container.
# Usage: ./make.sh
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
docker run --rm --platform linux/amd64 \
    -v "$DIR":/work \
    -w /work \
    eclipse-temurin:8-jdk \
    sh build/build.sh
