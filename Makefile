# Makefile for ChrootBridge

# Configuration
ANDROID_JAR = /opt/android-sdk/platforms/android-34/android.jar
D8 = /opt/android-sdk/build-tools/37.0.0/d8
OUTPUT_JAR = ChrootBridge.jar
SOURCE_FILE = ChrootBridge.java
BSH_JAR = libs/bsh-2.0b6.jar
DROID_BIN = droid

# Detect if we're in Android chroot
ifeq ($(shell test -d /data/local/tmp/archl && echo 1 || echo 0),1)
    IN_CHROOT = 1
else
    IN_CHROOT = 0
endif

.PHONY: all clean deploy test

all: $(OUTPUT_JAR) $(DROID_BIN)

# Build ChrootBridge
$(OUTPUT_JAR): $(SOURCE_FILE) $(BSH_JAR)
	@echo "Compiling Java code..."
	javac -cp "$(ANDROID_JAR):$(BSH_JAR)" $(SOURCE_FILE)
	@echo "Converting to DEX..."
	$(D8) --release --lib "$(ANDROID_JAR)" --output . *.class $(BSH_JAR)
	@echo "Creating JAR..."
	@if command -v jar >/dev/null 2>&1; then \
		jar cf $@ classes.dex; \
	elif command -v zip >/dev/null 2>&1; then \
		zip -q $@ classes.dex; \
	else \
		cp classes.dex $@; \
	fi
	@echo "Done: $@"

# Build droid client
droid: droid.c
	@echo "Building droid client..."
	$(CC) -o $(DROID_BIN) droid.c
	@echo "Done: $(DROID_BIN)"

# Download BeanShell if not present
$(BSH_JAR):
	@echo "Downloading BeanShell..."
	@mkdir -p libs
	@curl -L -o $@ "https://github.com/beanshell/beanshell/releases/download/2.0b6/bsh-2.0b6.jar" || \
		(echo "Failed to download BeanShell. Please download manually to libs/bsh-2.0b6.jar" && exit 1)

clean:
	@echo "Cleaning build files..."
	rm -f *.class classes.dex $(OUTPUT_JAR) $(DROID_BIN)
	@echo "Done"

deploy: all
ifeq ($(IN_CHROOT),1)
	@echo "Deploying to Android from chroot..."
	@cp $(OUTPUT_JAR) /data/local/tmp/
	@cp $(BSH_JAR) /data/local/tmp/
	@cp $(DROID_BIN) /data/local/tmp/
	@echo "Copied files to /data/local/tmp/"
	@echo ""
	@echo "To start the daemon:"
	@echo "  su -c 'CLASSPATH=/data/local/tmp/ChrootBridge.jar app_process / ChrootBridge daemon &'"
else
	@echo "Files built. To deploy to Android:"
	@echo "  adb push $(OUTPUT_JAR) /data/local/tmp/"
	@echo "  adb push $(BSH_JAR) /data/local/tmp/"
	@echo "  adb push $(DROID_BIN) /data/local/tmp/"
endif

test: deploy
	@echo ""
	@echo "Running tests..."
	@echo "================"
	@echo "Testing basic commands:"
	@sleep 2
	@./$(DROID_BIN) hello || echo "Test failed - make sure daemon is running"
	@echo ""
	@echo "Testing Java execution:"
	@./$(DROID_BIN) java-raw 'bridge.toast("Hello from Java!")' || echo "Java test failed"

help:
	@echo "ChrootBridge Makefile"
	@echo ""
	@echo "Targets:"
	@echo "  all      - Build ChrootBridge.jar and droid client (default)"
	@echo "  clean    - Remove build files"
	@echo "  deploy   - Copy files to /data/local/tmp/"
	@echo "  test     - Run basic tests"
	@echo ""
	@echo "Usage after deployment:"
	@echo "  1. Start daemon: su -c 'app_process...' (see deploy output)"
	@echo "  2. Test: droid hello"
	@echo "  3. Java: droid java-raw 'bridge.toast(\"Hi\")'"
