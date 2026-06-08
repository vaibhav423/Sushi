# Sushi

A native Android API bridge that runs inside an Arch Linux chroot on a rooted Android device.

A persistent Java daemon runs directly on the Android Runtime (ART) via `app_process` and listens on an abstract UNIX socket. A tiny C client (`droid`) sends commands to it from anywhere on the device, including inside the chroot.

## Requirements

**Host machine (for building)**
- JDK (OpenJDK 17+)
- Android SDK build-tools (`d8`) and `android.jar` for API 34
- Android NDK or any aarch64 cross-compiler (for `droid.c` and `runas2000.c`)

**Device**
- Rooted Android 12+ (Magisk or APatch)
- Arch Linux chroot (or any Linux chroot)

## Project structure

```
Sushi/
├── ChrootBridge.java   # The daemon — runs on ART via app_process
├── droid.c             # The client — tiny C binary used to send commands
├── runas2000.c         # UID-drop wrapper — exec's app_process as UID 2000 (shell)
├── start-bridge.sh     # Launcher — sets Android env vars, calls runas2000
└── build.sh            # Build + deploy script
```

## How it works

```
  ARCH LINUX CHROOT
  ┌────────────────────────────────┐
  │  $ droid toast "hello"         │  ← C binary, ~8KB
  │        │                       │
  │        │  abstract UNIX socket │
  │        │  @android-bridge      │
  └────────┼───────────────────────┘
           │  (shared kernel namespace)
  ANDROID HOST
  ┌────────┼───────────────────────┐
  │        ▼                       │
  │  ChrootBridge (Java daemon)    │
  │  running via app_process       │
  │  as UID 2000 (shell)           │
  │        │                       │
  │        ▼                       │
  │  Android Framework Services    │
  │  Toast, Clipboard, Input...    │
  └────────────────────────────────┘
```

The daemon and client share the same Linux kernel, so the abstract socket is visible across the chroot boundary without any filesystem permissions.

## Building

```bash
# Compile everything on the host machine
./build.sh
```

This produces:
- `ChrootBridge.jar` — deploy to `/data/local/tmp/` on the device
- `droid` — deploy to `/usr/local/bin/` inside the chroot
- `runas2000` — deploy to `/data/local/tmp/` on the device

To cross-compile the C binaries for aarch64:
```bash
# runas2000 (runs on Android host, must be statically linked)
aarch64-linux-gnu-gcc -static -o runas2000 runas2000.c

# droid (runs inside Arch chroot, can be dynamically linked)
aarch64-linux-gnu-gcc -o droid droid.c
```

## Deployment

```bash
# Push JAR and binaries
adb push ChrootBridge.jar /data/local/tmp/
adb push runas2000 /data/local/tmp/
adb shell chmod 755 /data/local/tmp/runas2000

# Push the launcher into the chroot
adb push start-bridge.sh /data/local/tmp/archl/usr/local/bin/start-bridge
adb shell chmod 755 /data/local/tmp/archl/usr/local/bin/start-bridge

# Push the client into the chroot
adb push droid /data/local/tmp/archl/usr/local/bin/droid
adb shell chmod 755 /data/local/tmp/archl/usr/local/bin/droid
```

## Starting the daemon

From inside the chroot:
```bash
sudo start-bridge
```

To autostart at boot, add to `/data/adb/service.d/bridge_boot.sh` (Magisk):
```bash
#!/system/bin/sh
sleep 5
/data/local/tmp/archl/usr/local/bin/start-bridge &
```

## Usage

All commands are sent through the `droid` client from inside the chroot.

```bash
droid hello                        # sanity check
droid toast "message"              # show a toast on screen
droid clipboard get                # print clipboard contents
droid clipboard set "some text"    # write to clipboard
droid input text enter             # inject Enter key
```

## Why `app_process` must run as UID 2000

`app_process` is designed to run as `shell` (UID 2000), not root (UID 0). Running it as root causes the kernel security layer to kill the process. Since the chroot `sudo` gives you UID 0, `runas2000.c` exists to drop privileges to UID 2000 before exec-ing `app_process`, while preserving the full environment via `execve`.

## Why the environment variables matter

ART requires `BOOTCLASSPATH`, `ANDROID_ROOT`, `ANDROID_ART_ROOT`, and several other variables to locate framework JARs and boot images. If any are missing or wrong it crashes with `SIGABRT` before your Java code runs. `start-bridge.sh` reads `BOOTCLASSPATH` directly from Android's init process (`/proc/1/environ`) to guarantee the correct value.
