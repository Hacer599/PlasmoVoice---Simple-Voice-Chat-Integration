#!/usr/bin/env bash
set -e

VERSIONS="1.17.1 1.18.2 1.19 1.19.1 1.19.2 1.19.3 1.19.4 1.20 1.20.1 1.20.2 1.20.3 1.20.4 1.20.5 1.20.6 1.21 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6 1.21.7 1.21.8 1.21.9 1.21.10 1.21.11"

mkdir -p dist

for version in $VERSIONS; do
    echo
    echo "=== Minecraft $version ==="
    ./gradlew clean build -Pmc="$version"
    cp build/libs/pv-svc-bridge-*+mc"$version".jar dist/
done

echo
echo "All builds finished, jars are in the dist folder"
