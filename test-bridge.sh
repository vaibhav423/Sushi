#!/system/bin/sh
# Test script for ChrootBridge
# This script tests the bridge functionality

echo "=== ChrootBridge Test Script ==="
echo ""

# Test 1: Check if daemon is running
echo "[Test 1] Checking if daemon is running..."
if [ -S /data/local/tmp/android-bridge ]; then
    echo "OK: Socket exists"
else
    echo "FAIL: Socket not found. Starting daemon..."
    # Start daemon in background
    nohup /data/local/tmp/runas2000 /system/bin/app_process / ChrootBridge daemon > /data/local/tmp/chrootbridge.log 2>&1 &
    sleep 2
fi

# Function to send command to daemon
send_cmd() {
    echo "$1" | nc -U /data/local/tmp/android-bridge 2>/dev/null
}

# Test 2: Basic hello
echo ""
echo "[Test 2] Testing basic command..."
result=$(send_cmd "hello")
echo "Response: $result"

# Test 3: Toast
echo ""
echo "[Test 3] Testing toast..."
result=$(send_cmd "toast Test from script")
echo "Response: $result"

# Test 4: Clipboard operations
echo ""
echo "[Test 4] Testing clipboard..."
send_cmd "clipboard set Hello from ChrootBridge"
result=$(send_cmd "clipboard get")
echo "Clipboard content: $result"

# Test 5: Simple Java code execution
echo ""
echo "[Test 5] Testing Java code execution..."
result=$(send_cmd "java-raw bridge.toast(\"Java code works!\")")
echo "Response: $result"

# Test 6: Variable operations
echo ""
echo "[Test 6] Testing variables..."
send_cmd "var set test_key test_value"
result=$(send_cmd "var get test_key")
echo "Variable value: $result"

# Test 7: Complex Java code via base64
echo ""
echo "[Test 7] Testing complex Java code..."
code='import java.util.*; List list = new ArrayList(); list.add("item1"); list.add("item2"); bridge.setVar("myList", list); return bridge.toJson(list);'
b64=$(echo -n "$code" | base64 -w 0)
result=$(send_cmd "java $b64")
echo "Response: $result"

echo ""
echo "=== Tests completed ==="
echo "Check logs at: /data/local/tmp/chrootbridge.log"
