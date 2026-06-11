#!/bin/bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$PROJECT_DIR/build"
mkdir -p "$BUILD_DIR"
OUT="$BUILD_DIR/SushiUI.apk"
TMP=$(mktemp -d)
trap "rm -rf $TMP" EXIT

ANDROID_JAR="/opt/android-sdk/platforms/android-34/android.jar"
BUILD_TOOLS="/opt/android-sdk/build-tools/37.0.0"
AAPT2="$BUILD_TOOLS/aapt2"
D8="/usr/bin/d8"
APKSIGNER="$BUILD_TOOLS/lib/apksigner.jar"
KEYSTORE="$PROJECT_DIR/debug.keystore"
SRC="src"

echo "=== Compiling Java classes ==="
find "$SRC" -name '*.java' > "$TMP/sources.txt"
mkdir -p "$TMP/classes"
javac -source 8 -target 8 \
    -classpath "$ANDROID_JAR" \
    -Xlint:-options \
    -d "$TMP/classes" \
    @"$TMP/sources.txt"

echo "=== Converting to DEX ==="
mkdir -p "$TMP/dex"
"$D8" --lib "$ANDROID_JAR" \
    --output "$TMP/dex" \
    $(find "$TMP/classes" -name '*.class')

echo "=== Building APK skeleton with aapt2 ==="
"$AAPT2" link \
    --manifest AndroidManifest.xml \
    -I "$ANDROID_JAR" \
    -o "$TMP/unsigned.apk" 2>&1

echo "=== Adding classes.dex ==="
mkdir -p "$TMP/apk_contents"
cd "$TMP/apk_contents"
unzip -q -o "$TMP/unsigned.apk"
cp "$TMP/dex/classes.dex" "$TMP/apk_contents/classes.dex"

echo "=== Adding xposed_init ==="
mkdir -p assets
echo "fire.sushi.ui.SushiLspModule" > assets/xposed_init

echo "=== Rezipping (no compress .arsc .dex) ==="
zip -q -r -n .arsc:.dex "$TMP/unsigned2.apk" .

echo "=== Aligning ==="
zipalign -f -p 4 "$TMP/unsigned2.apk" "$TMP/aligned.apk"

echo "=== Signing ==="
java -jar "$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --min-sdk-version 34 \
    "$TMP/aligned.apk"

cp "$TMP/aligned.apk" "$OUT"
echo "=== Done: $OUT ($(stat -c%s $OUT 2>/dev/null || stat -f%z $OUT 2>/dev/null) bytes) ==="
