# Sushi

A native Android API bridge that runs inside an Arch Linux chroot on a rooted Android device.

A persistent Java daemon runs directly on the Android Runtime (ART) via `app_process` and listens on an abstract UNIX socket. A tiny C client (`droid`) sends commands to it from anywhere on the device, including inside the chroot.

## Requirements

**Build machine (x86_64 Arch)**
- JDK (OpenJDK 17+)
- Android SDK platform 34 + build-tools 37 — see `install.md`

**Device**
- Rooted Android 14 (APatch or Magisk)
- Arch Linux chroot at `/data/local/tmp/archl`

## Project structure

```
Sushi/
├── ChrootBridge.java   # The daemon — runs on ART via app_process
├── droid.c             # C socket client (installed at /usr/local/bin/droid)
├── runas2000.c         # UID-drop wrapper — exec's app_process as UID 2000 (shell)
├── chroot-scripts/bridge.sh  # Daemon management: bridge {start|stop|status|restart}
├── build.sh            # Builds ChrootBridge.jar (compile + DEX)
├── chroot-scripts/deploy-from-chroot.sh  # Full build + deploy from inside the chroot
├── guide.md            # Quick reference for edit → build → deploy → test cycle
├── build/              # All build outputs
│   ├── ChrootBridge.jar
│   ├── droid
│   └── runas2000
├── libs/               # Dependencies
│   └── bsh-2.0b6.jar   # BeanShell
└── examples/           # BeanShell example scripts
```

## Architecture

```
  ARCH LINUX CHROOT
  ┌─────────────────────────────────────────────┐
  │  $ droid toast "hello"                      │
  │  $ bridge restart                           │
  │        │                                    │
  │        │  abstract UNIX socket @android-bridge
  └────────┼────────────────────────────────────┘
           │  (shared kernel namespace)
  ANDROID HOST
  ┌────────┼────────────────────────────────────┐
  │        ▼                                    │
  │  ChrootBridge (Java daemon)                 │
  │  running via app_process as UID 2000        │
  │        │                                    │
  │  Commands: toast, clipboard, input, java    │
  │  (BeanShell)                                │
  │        │                                    │
  │        ▼                                    │
  │  Android Framework Services                 │
  └─────────────────────────────────────────────┘
```

## Quick start

```bash
# Build the JAR
./build.sh

# Build + deploy to Android
./chroot-scripts/deploy-from-chroot.sh

# Start the daemon
bridge start

# Test
droid hello
droid toast "Hello from Sushi!"

# See guide.md for full edit→build→deploy→test cycle
```

## Daemon management

```bash
bridge status    # check if running
bridge start     # start with proper env vars
bridge stop      # kill daemon
bridge restart   # stop + start
```

The daemon auto-starts at boot via `/data/adb/service.d/bridge_boot.sh` (Magisk).

## Commands

```bash
droid hello                        # sanity check
droid toast "message"              # show a toast
droid clipboard get                # print clipboard contents
droid clipboard set "text"         # write to clipboard
droid input text enter             # inject Enter key
droid java "base64-encoded-code"   # execute BeanShell code
```

## Why UID 2000

`app_process` must run as UID 2000 (shell), not root. `runas2000.c` drops privileges before exec-ing `app_process`. Running as UID 0 causes the kernel security layer to kill the process.

## Why env vars matter

ART requires `BOOTCLASSPATH`, `DEX2OATBOOTCLASSPATH`, `ANDROID_ROOT`, and others to find framework JARs. `bridge.sh` reads them from zygote64's proc environ to guarantee correct values. Missing or empty vars cause SIGABRT at startup.
