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

# Pull the exact BOOTCLASSPATH from Android's init process environment
export BOOTCLASSPATH=$(cat /proc/1/environ | tr '\0' '\n' | grep ^BOOTCLASSPATH | cut -d= -f2-)
export DEX2OATBOOTCLASSPATH=/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/system/framework/framework.jar:/system/framework/framework-graphics.jar:/system/framework/framework-location.jar:/system/framework/ext.jar:/system/framework/telephony-common.jar:/system/framework/voip-common.jar:/system/framework/ims-common.jar:/system/framework/framework-ondeviceintelligence-platform.jar:/system/framework/telephony-ext.jar:/apex/com.android.i18n/javalib/core-icu4j.jar

export CLASSPATH=/data/local/tmp/ChrootBridge.jar

exec /data/local/tmp/runas2000 /system/bin/app_process / ChrootBridge daemon
