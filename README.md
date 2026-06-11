# Sushi

A native Android API bridge that runs inside an Arch Linux chroot on a rooted Android device.

A persistent Java daemon runs directly on the Android Runtime (ART) via `app_process` and listens on an abstract UNIX socket. A tiny C client (`droid`) sends commands to it from anywhere on the device, including inside the chroot. Interactive UI is handled by an LSPosed module injected into SystemUI, communicating with the daemon over a second abstract socket.

## Requirements

**Build machine (x86_64 Arch)**
- JDK (OpenJDK 17+)
- Android SDK platform 34 + build-tools 37 — see `install.md`

**Device**
- Rooted Android 14 (APatch or Magisk)
- Arch Linux chroot at `/data/local/tmp/archl`
- LSPosed (for UI module)

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
│   ├── runas2000
│   └── SushiUI.apk     # (copied from x86_64 build)
├── SushiUI/            # LSPosed module for interactive UI
│   ├── src/            # Xposed hook + socket server
│   ├── AndroidManifest.xml
│   └── build_x86.sh    # Build APK on x86_64 machine via aapt2
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
  │  (BeanShell), dialog (via LSPosed socket)   │
  │        │                                    │
  │        ▼                                    │
  │  Android Framework Services                 │
  └─────────────────────────────────────────────┘

  UI (LSPosed module in SystemUI):
  ┌─────────────────────────────────────────────┐
  │  SushiLspServer listens on @sushi-ui        │
  │  Daemon sends "dialog title|msg|btn1,btn2"  │
  │  SystemUI draws AlertDialog, returns button  │
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
droid dialog "t|m|btn1,btn2"      # show dialog (requires LSPosed module)
```

## LSPosed UI module

The `SushiUI/` project builds an APK that hooks SystemUI. It opens an abstract socket `@sushi-ui` and waits for dialog requests from the daemon. Build on x86_64:

```bash
cd SushiUI && bash build_x86.sh   # outputs build/SushiUI.apk
```

Install on device: `asu -c 'pm install -r -t /data/local/tmp/SushiUI.apk'`, then enable in LSPosed Manager scoped to SystemUI.

## Why UID 2000

`app_process` must run as UID 2000 (shell), not root. `runas2000.c` drops privileges before exec-ing `app_process`. Running as UID 0 causes the kernel security layer to kill the process.

## Why env vars matter

ART requires `BOOTCLASSPATH`, `DEX2OATBOOTCLASSPATH`, `ANDROID_ROOT`, and others to find framework JARs. `bridge.sh` reads them from zygote64's proc environ to guarantee correct values. Missing or empty vars cause SIGABRT at startup.
