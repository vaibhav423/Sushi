# Install Android SDK Platform 34 + Build Tools 37 (ARM Arch Linux)

## Prerequisite: Native Java

```bash
sudo pacman -S jdk-openjdk
```

## Part 1: `android.jar` (Android 34 Platform)

```bash
mkdir -p /tmp/android34
cd /tmp/android34
curl -O https://dl.google.com/android/repository/platform-34-ext7_r03.zip
unzip platform-34-ext7_r03.zip
sudo mkdir -p /opt/android-sdk/platforms/android-34
sudo cp */android.jar /opt/android-sdk/platforms/android-34/
rm -rf /tmp/android34
```

## Part 2: `d8` (Build Tools 37.0.0)

```bash
mkdir -p /tmp/buildtools
cd /tmp/buildtools
curl -O https://dl.google.com/android/repository/build-tools_r37_linux.zip
unzip build-tools_r37_linux.zip
sudo mkdir -p /opt/android-sdk/build-tools/37.0.0
sudo cp -r android-37.0/* /opt/android-sdk/build-tools/37.0.0/
sudo ln -s /opt/android-sdk/build-tools/37.0.0/d8 /usr/local/bin/d8
rm -rf /tmp/buildtools
```

## Verification

```bash
d8 --version
```
