#!/bin/bash
# java-exec.sh: Send Java code to ChrootBridge daemon
# Usage: ./java-exec.sh '<java-code>'
#        ./java-exec.sh -f <file.java>
#        echo '<java-code>' | ./java-exec.sh

SOCKET="/data/local/tmp/android-bridge"

# Check if running from chroot or direct Android
if [ -S "$SOCKET" ]; then
    # Direct Android access
    USE_NETCAT="nc -U $SOCKET"
else
    # Inside chroot, need to access Android socket
    # Assuming socket is accessible via /data/local/tmp/ from chroot
    if [ -S "/data/local/tmp/android-bridge" ]; then
        USE_NETCAT="nc -U /data/local/tmp/android-bridge"
    else
        echo "Error: Cannot find android-bridge socket"
        echo "Make sure the ChrootBridge daemon is running"
        exit 1
    fi
fi

# Function to send code
send_code() {
    local code="$1"
    # Base64 encode the code to handle special characters
    local b64=$(echo -n "$code" | base64 -w 0)
    echo "java $b64" | $USE_NETCAT
}

# Function to send raw code (for simple commands)
send_raw() {
    local code="$1"
    echo "java-raw $code" | $USE_NETCAT
}

# Function to send multi-line code
send_multi() {
    local code="$1"
    {
        echo "java-multi"
        echo "$code"
        echo "__END__"
    } | $USE_NETCAT
}

# Parse arguments
if [ "$1" = "-f" ]; then
    # Read from file
    if [ -z "$2" ]; then
        echo "Usage: $0 -f <file.java>"
        exit 1
    fi
    if [ ! -f "$2" ]; then
        echo "Error: File not found: $2"
        exit 1
    fi
    CODE=$(cat "$2")
    send_multi "$CODE"
elif [ "$1" = "-r" ] || [ "$1" = "--raw" ]; then
    # Raw mode (no base64)
    shift
    send_raw "$*"
elif [ -n "$1" ]; then
    # Single argument - use base64
    send_code "$*"
else
    # Read from stdin
    CODE=$(cat)
    if [ -n "$CODE" ]; then
        send_multi "$CODE"
    else
        echo "Usage: $0 '<java-code>'"
        echo "       $0 -f <file.java>"
        echo "       $0 -r <raw-java-code>"
        echo "       echo '<java-code>' | $0"
        exit 1
    fi
fi
