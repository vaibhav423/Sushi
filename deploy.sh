#!/bin/bash
# deploy.sh: Build and deploy ChrootBridge to Android
set -e

echo "=== Building ChrootBridge ==="
./build.sh

echo "=== Building C Binaries ==="
gcc -O2 -o droid droid.c
gcc -O2 -static -o runas2000 runas2000.c

echo "=== Deploying to Android ==="
TARGET="/data/local/tmp/archl/mnt/real_android_data/local/tmp"
sudo cp ChrootBridge.jar "$TARGET/"
sudo cp droid "$TARGET/"
sudo cp runas2000 "$TARGET/"
sudo chmod 644 "$TARGET/ChrootBridge.jar"
sudo chmod 755 "$TARGET/droid" "$TARGET/runas2000"

echo "=== Deploying start-bridge.sh ==="
sudo cp /home/fire/Water/crap/Sushi/start-bridge.sh /data/local/tmp/archl/usr/local/bin/start-bridge

echo "=== Done ==="
echo "Start daemon: sudo nsenter --mount=/proc/1/ns/mnt -- /system/bin/sh /data/local/tmp/start_daemon.sh"
echo "Test:         droid hello"
