# Makefile for ChrootBridge

# Configuration
ANDROID_JAR = /opt/android-sdk/platforms/android-34/android.jar
D8 = /opt/android-sdk/build-tools/37.0.0/d8
OUTPUT_JAR = build/ChrootBridge.jar
SOURCE_FILE = ChrootBridge.java
BSH_JAR = libs/bsh-2.1.1.jar
DROID_BIN = build/droid
RUNAS2000 = build/runas2000

# Detect if we're in Android chroot
ifeq ($(shell test -d /data/local/tmp/archl && echo 1 || echo 0),1)
    IN_CHROOT = 1
else
    IN_CHROOT = 0
endif

.PHONY: all clean deploy test

all: $(OUTPUT_JAR) $(DROID_BIN) $(RUNAS2000)

# Build ChrootBridge
$(OUTPUT_JAR): $(SOURCE_FILE) $(BSH_JAR) | build
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

build:
	@mkdir -p build

# Build droid client
$(DROID_BIN): droid.c | build
	@echo "Building droid client..."
	$(CC) -o $@ droid.c
	@echo "Done: $@"

# Build runas2000 (UID drop wrapper)
$(RUNAS2000): runas2000.c | build
	@echo "Building runas2000..."
	$(CC) -O2 -static -o $@ runas2000.c
	@echo "Done: $@"

# Download BeanShell if not present
$(BSH_JAR):
	@echo "Downloading BeanShell..."
	@mkdir -p libs
	@curl -L -o $@ "https://github.com/beanshell/beanshell/releases/download/2.1.1/bsh-2.1.1.jar" || \
		(echo "Failed to download BeanShell. Please download manually to libs/bsh-2.1.1.jar" && exit 1)

clean:
	@echo "Cleaning build files..."
	rm -f *.class classes.dex $(OUTPUT_JAR) $(DROID_BIN) $(RUNAS2000)
	@echo "Done"

deploy: all
ifeq ($(IN_CHROOT),1)
	@echo "Deploying to Android from chroot..."
	@sudo cp $(OUTPUT_JAR) /data/local/tmp/
	@sudo cp $(BSH_JAR) /data/local/tmp/
	@sudo cp $(DROID_BIN) /data/local/tmp/
	@sudo cp $(RUNAS2000) /data/local/tmp/
	@echo "Copied files to /data/local/tmp/"
	@echo ""
	@echo "To start the daemon:"
	@echo "  bridge start"
else
	@echo "Files built. To deploy to Android:"
	@echo "  adb push $(OUTPUT_JAR) /data/local/tmp/"
	@echo "  adb push $(BSH_JAR) /data/local/tmp/"
	@echo "  adb push $(DROID_BIN) /data/local/tmp/"
	@echo "  adb push $(RUNAS2000) /data/local/tmp/"
endif

test: deploy
	@echo ""
	@echo "Running tests..."
	@echo "================"
	@echo "Testing basic commands:"
	@sleep 2
	@$(DROID_BIN) hello || echo "Test failed - make sure daemon is running"
	@echo ""
	@echo "Testing Java execution:"
	@$(DROID_BIN) java-raw 'bridge.toast("Hello from Java!")' || echo "Java test failed"

help:
	@echo "ChrootBridge Makefile"
	@echo ""
	@echo "Targets:"
	@echo "  all      - Build ChrootBridge.jar, droid, and runas2000 (default)"
	@echo "  clean    - Remove build files"
	@echo "  deploy   - Copy files to /data/local/tmp/"
	@echo "  test     - Run basic tests"
	@echo ""
	@echo "Usage after deployment:"
	@echo "  1. Start daemon: bridge start"
	@echo "  2. Test: droid hello"
	@echo "  3. Java: droid java-raw 'bridge.toast(\"Hi\")'"
