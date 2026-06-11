#!/bin/bash
# deploy-from-chroot.sh: Build and deploy ChrootBridge from inside Arch chroot
# Usage: ./deploy-from-chroot.sh
set -e
cd "$(dirname "$0")/.."

BUILD_DIR="build"

echo "=== Building ChrootBridge ==="
./build.sh

echo "=== Building C Binaries ==="
gcc -O2 -o "$BUILD_DIR/droid" droid.c
gcc -O2 -static -o "$BUILD_DIR/runas2000" runas2000.c

echo "=== Deploying to Android side ==="
TARGET="/data/local/tmp"
sudo cp "$BUILD_DIR/ChrootBridge.jar" "$TARGET/"
sudo cp "$BUILD_DIR/runas2000" "$TARGET/"

sudo chmod 644 "$TARGET/ChrootBridge.jar"
sudo chmod 755 "$TARGET/runas2000"

echo "=== Installing inside chroot ==="
sudo cp "$BUILD_DIR/droid" /usr/local/bin/droid
sudo chmod 755 /usr/local/bin/droid
sudo cp chroot-scripts/bridge.sh /usr/local/bin/bridge
sudo chmod 755 /usr/local/bin/bridge

echo "=== Restarting daemon ==="
bridge restart

echo "=== Done ==="
echo "Test: droid hello"
echo "Log: tail -f /data/local/tmp/daemon.log"
