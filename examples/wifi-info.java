// Example: Get WiFi and Bluetooth information
// Note: These require the `android` package context to properly match UID 1000.
// When running as shell (UID 2000), WiFiManager.getConnectionInfo() is
// blocked by AppOps checkPackage. Use the built-in `input` command instead.
// This example shows what WOULD work with proper UID.

bridge.toast("WiFi info requires system UID (1000)");
return "WiFi/Bluetooth APIs require UID 1000 (system) - run without runas2000";