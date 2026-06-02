#!/bin/bash
set -e

# Configuration
ANDROID_JAR="/opt/android-sdk/platforms/android-34/android.jar"
D8="/opt/android-sdk/build-tools/37.0.0/d8"
OUTPUT_JAR="ChrootBridge.jar"
SOURCE_FILE="ChrootBridge.java"

echo "[1/3] Compiling Java code..."
javac -cp "$ANDROID_JAR" "$SOURCE_FILE"

echo "[2/3] Converting to DEX..."
"$D8" --release --lib "$ANDROID_JAR" --output . *.class

echo "[3/3] Packaging into JAR..."
zip -q "$OUTPUT_JAR" classes.dex

echo "Done! Run in Android chroot using:"
echo "CLASSPATH=$OUTPUT_JAR app_process / ChrootBridge"

# Deploy to device if reachable
if ssh -o ConnectTimeout=3 termux true 2>/dev/null; then
    echo "[deploy] Copying to device..."
    scp -q "$OUTPUT_JAR" termux:~/
    ssh termux 'su -c "pkill -f \"ChrootBridge daemon\" 2>/dev/null; sleep 1; cp -f /data/data/com.termux/files/home/ChrootBridge.jar /data/local/tmp/ChrootBridge.jar && chmod 644 /data/local/tmp/ChrootBridge.jar"'
    echo "[deploy] Done. Restart daemon with: sudo start-bridge"
fi
