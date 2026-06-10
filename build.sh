#!/bin/bash
set -e

# Configuration
ANDROID_JAR="/opt/android-sdk/platforms/android-34/android.jar"
D8="/opt/android-sdk/build-tools/37.0.0/d8"
OUTPUT_JAR="ChrootBridge.jar"
SOURCE_FILE="ChrootBridge.java"
BSH_JAR="libs/bsh-2.0b6.jar"

echo "[1/3] Compiling Java code..."
javac -cp "$ANDROID_JAR:$BSH_JAR" "$SOURCE_FILE"

echo "[2/3] Converting to DEX..."
# Extract BeanShell to include its classes in the DEX
mkdir -p build_tmp
cd build_tmp
unzip -q -o ../$BSH_JAR
cp ../*.class .
"$D8" --release --lib "$ANDROID_JAR" --output . *.class bsh/*.class bsh/commands/*.class bsh/reflect/*.class bsh/util/*.class bsh/collection/*.class
cp classes.dex ../
cd ..

echo "[3/3] Packaging into JAR..."
zip -q "$OUTPUT_JAR" classes.dex

# Clean up
rm -rf build_tmp *.class classes.dex

echo "Done! Run in Android chroot using:"
echo "CLASSPATH=$OUTPUT_JAR app_process / ChrootBridge"
