#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
mkdir -p "$ROOT/target/classes"
find "$ROOT/src/main/java" -name '*.java' -print > "$ROOT/target/sources.txt"
javac --release 21 --add-modules jdk.httpserver -encoding UTF-8 -d "$ROOT/target/classes" @"$ROOT/target/sources.txt"
printf 'Manifest-Version: 1.0\nMain-Class: com.vankz.simpleguihttp.SimpleHttpGui\n\n' > "$ROOT/target/manifest.mf"
jar --create --file "$ROOT/target/simple-gui-http.jar" --manifest "$ROOT/target/manifest.mf" -C "$ROOT/target/classes" .
echo "Built: target/simple-gui-http.jar"
