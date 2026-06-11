#!/bin/bash
# bridge.sh: ChrootBridge daemon management
# Usage: bridge {start|stop|status|restart|debug-start}

ACTION="${1:-status}"
DAEMON_LOG="/data/local/tmp/daemon.log"

if ! command -v asu &>/dev/null; then
  echo "error: asu not found — this script must run inside the Android chroot"
  exit 1
fi

# Ensure log is writable by the parent shell
asu -c "chmod 666 $DAEMON_LOG" 2>/dev/null

get_pid() {
  asu -c 'ps -ef | grep "app_process.*ChrootBridge" | grep -v grep | awk "{print \$2}" | head -1' 2>&1
}

stop_daemon() {
  local pid
  pid=$(get_pid)
  if [ -n "$pid" ]; then
    echo "stopping PID $pid..."
    asu -c "kill $pid 2>/dev/null"
    for i in 1 2 3 4 5; do
      sleep 1
      local p
      p=$(get_pid)
      [ -z "$p" ] && break
    done
    echo "stopped"
  else
    echo "not running"
  fi
}

start_daemon() {
  local pid
  pid=$(get_pid)
  if [ -n "$pid" ]; then
    echo "killing stale PID $pid..."
    asu -c "kill $pid 2>/dev/null"
    for i in 1 2 3; do
      sleep 1; local p; p=$(get_pid)
      [ -z "$p" ] && break
    done
    unset pid; sleep 2
  fi

  echo "starting ChrootBridge daemon..."
  # Run asu in background, exec replaces the shell so no backgrounding inside
  asu -c '
    export ANDROID_ROOT=/system ANDROID_DATA=/data
    export ANDROID_ART_ROOT=/apex/com.android.art
    export ANDROID_I18N_ROOT=/apex/com.android.i18n
    export ANDROID_TZDATA_ROOT=/apex/com.android.tzdata
    export HOME=/ USER=shell LOGNAME=shell
    ZYGOTE_PID=$(pgrep -f "^zygote64$" | head -1)
    ENV_RAW=$(cat /proc/$ZYGOTE_PID/environ | tr "\000" "\n")
    BOOTCLASSPATH=$(echo "$ENV_RAW" | grep "^BOOTCLASSPATH=" | cut -d= -f2-)
    DEX2OATBOOTCLASSPATH=$(echo "$ENV_RAW" | grep "^DEX2OATBOOTCLASSPATH=" | cut -d= -f2-)
    export BOOTCLASSPATH DEX2OATBOOTCLASSPATH
    export CLASSPATH=/data/local/tmp/ChrootBridge.jar
    exec /data/local/tmp/runas2000 /system/bin/app_process / ChrootBridge daemon
  ' < /dev/null >> /data/local/tmp/daemon.log 2>&1 &
  disown

  for i in 1 2 3 4 5; do
    sleep 1
    pid=$(get_pid)
    [ -n "$pid" ] && break
  done

  if [ -n "$pid" ]; then
    echo "started PID $pid"
  else
    echo "failed - daemon.log:"
    tail -3 "$DAEMON_LOG" 2>/dev/null || echo "(no log)"
  fi
}

debug_start() {
  local pid
  pid=$(get_pid)
  echo "=== DEBUG: current PID=$pid ==="
  if [ -n "$pid" ]; then
    echo "killing..."
    asu -c "kill $pid 2>/dev/null"
    for i in 1 2 3; do sleep 1; local p; p=$(get_pid); [ -z "$p" ] && break; done
    sleep 2
  fi

  echo ""
  echo "=== Binaries ==="
  asu -c '
    echo "runas2000: $(ls -l /data/local/tmp/runas2000 2>&1)"
    echo "app_process: $(ls -l /system/bin/app_process 2>&1)"
    echo "ChrootBridge.jar: $(ls -l /data/local/tmp/ChrootBridge.jar 2>&1)"
    echo "zygote64: $(pgrep -f "^zygote64$" | head -1)"
  ' 2>&1

  echo ""
  echo "=== Env vars ==="
  asu -c '
    export ANDROID_ROOT=/system ANDROID_DATA=/data
    export ANDROID_ART_ROOT=/apex/com.android.art
    export ANDROID_I18N_ROOT=/apex/com.android.i18n
    export ANDROID_TZDATA_ROOT=/apex/com.android.tzdata
    export HOME=/ USER=shell LOGNAME=shell
    ZYGOTE_PID=$(pgrep -f "^zygote64$" | head -1)
    ENV_RAW=$(cat /proc/$ZYGOTE_PID/environ | tr "\000" "\n")
    BOOTCLASSPATH=$(echo "$ENV_RAW" | grep "^BOOTCLASSPATH=" | cut -d= -f2-)
    DEX2OATBOOTCLASSPATH=$(echo "$ENV_RAW" | grep "^DEX2OATBOOTCLASSPATH=" | cut -d= -f2-)
    echo "ZYGOTE=$ZYGOTE_PID"
    echo "BCL_len=${#BOOTCLASSPATH}"
    echo "DEX2_len=${#DEX2OATBOOTCLASSPATH}"
    echo "BCL_start=${BOOTCLASSPATH:0:100}"
  ' 2>&1

  echo ""
  echo "=== Launching foreground (no nohup) ==="
  asu -c '
    export ANDROID_ROOT=/system ANDROID_DATA=/data
    export ANDROID_ART_ROOT=/apex/com.android.art
    export ANDROID_I18N_ROOT=/apex/com.android.i18n
    export ANDROID_TZDATA_ROOT=/apex/com.android.tzdata
    export HOME=/ USER=shell LOGNAME=shell
    ZYGOTE_PID=$(pgrep -f "^zygote64$" | head -1)
    ENV_RAW=$(cat /proc/$ZYGOTE_PID/environ | tr "\000" "\n")
    BOOTCLASSPATH=$(echo "$ENV_RAW" | grep "^BOOTCLASSPATH=" | cut -d= -f2-)
    DEX2OATBOOTCLASSPATH=$(echo "$ENV_RAW" | grep "^DEX2OATBOOTCLASSPATH=" | cut -d= -f2-)
    export BOOTCLASSPATH DEX2OATBOOTCLASSPATH
    export CLASSPATH=/data/local/tmp/ChrootBridge.jar
    /data/local/tmp/runas2000 /system/bin/app_process / ChrootBridge daemon &
    CHILD=$!
    echo "child_pid=$CHILD"
    sleep 3
    if kill -0 $CHILD 2>/dev/null; then
      echo "alive after 3s"
      kill $CHILD 2>/dev/null
    else
      wait $CHILD 2>/dev/null
      echo "dead exit=$?"
    fi
  ' 2>&1

  echo ""
  echo "=== Log tail ==="
  tail -5 "$DAEMON_LOG" 2>/dev/null
}

status_daemon() {
  local pid
  pid=$(get_pid)
  if [ -n "$pid" ]; then
    echo "running PID $pid"
  else
    echo "stopped"
  fi
}

case "$ACTION" in
  start)       start_daemon ;;
  debug-start) debug_start ;;
  stop)        stop_daemon ;;
  restart)     stop_daemon; sleep 1; start_daemon ;;
  status)      status_daemon ;;
  *)           echo "Usage: $0 {start|stop|status|restart|debug-start}"; exit 1 ;;
esac
