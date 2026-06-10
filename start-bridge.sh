#!/system/bin/sh
# start-bridge: launch ChrootBridge daemon from inside the Arch chroot
# Deploy to /usr/local/bin/start-bridge inside chroot, then: sudo start-bridge

export ANDROID_ROOT=/system
export ANDROID_DATA=/data
export ANDROID_ART_ROOT=/apex/com.android.art
export ANDROID_I18N_ROOT=/apex/com.android.i18n
export ANDROID_TZDATA_ROOT=/apex/com.android.tzdata
export HOME=/
export USER=shell
export LOGNAME=shell

# Read BOOTCLASSPATH and DEX2OATBOOTCLASSPATH from zygote64
ZYGOTE_PID=$(ps -A | grep "zygote64" | awk '{print $2}' | head -1)
if [ -n "$ZYGOTE_PID" ]; then
    export BOOTCLASSPATH=$(cat /proc/$ZYGOTE_PID/environ | tr "\000" "\n" | grep "^BOOTCLASSPATH=" | cut -d= -f2-)
    export DEX2OATBOOTCLASSPATH=$(cat /proc/$ZYGOTE_PID/environ | tr "\000" "\n" | grep "^DEX2OATBOOTCLASSPATH=" | cut -d= -f2-)
fi

export CLASSPATH=/data/local/tmp/ChrootBridge.jar

exec /data/local/tmp/runas2000 /system/bin/app_process / ChrootBridge daemon
