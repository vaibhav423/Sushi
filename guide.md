# ChrootBridge Development Guide

## Full rebuild after editing `ChrootBridge.java`

```bash
# 1. Build the JAR (compiles Java → DEX with BeanShell)
./build.sh

# 2. Deploy to Android (copies via safe mount, restarts daemon)
./chroot-scripts/deploy-from-chroot.sh && bridge restart

# 3. Test
droid hello
droid toast "Hello from rebuilt bridge"
droid clipboard set "test"
droid clipboard get
```

## Checking daemon status

```bash
bridge status      # running / stopped
bridge restart     # full restart
tail -f /data/local/tmp/daemon.log   # daemon stdout/stderr
tail -f /data/local/tmp/bridge_boot.log  # boot-time log
```

## Building SushiUI APK (LSPosed module)

```bash
# On x86_64 build machine via SSH (requires Android SDK + aapt2)
cd SushiUI && bash build_x86.sh
# Then deploy to Android:
cp build/SushiUI.apk /data/local/tmp/archl/mnt/real_android_data/local/tmp/
# Install via asu:
asu -c 'pm install -r -t /data/local/tmp/SushiUI.apk'
```

## C binaries (droid client, runas2000)

These are compiled by `deploy.sh` automatically from `droid.c` and `runas2000.c`. To rebuild manually:

```bash
gcc -O2 -o build/droid droid.c
gcc -O2 -static -o build/runas2000 runas2000.c
```

## Boot to bridge

`/data/adb/service.d/bridge_boot.sh` auto-starts the daemon at boot via Magisk.
To update it, edit the file directly on Android (`asu -c 'vi /data/adb/service.d/bridge_boot.sh'`).
