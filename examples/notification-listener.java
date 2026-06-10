// Example: Check for Notification Listener access
import android.service.notification.*;
import android.content.*;

// Get NotificationListenerService if available
// Note: This requires the Notification Listener to be enabled in system settings

ComponentName cn = new ComponentName(context, "com.android.shell.NotificationListener");
bridge.log("Component: " + cn.flattenToString());

// Check if notification access is enabled
String enabledListeners = android.provider.Settings.Secure.getString(
    context.getContentResolver(), 
    "enabled_notification_listeners"
);

bridge.log("Enabled listeners: " + enabledListeners);

bridge.toast("Check logs for notification listener status");
return enabledListeners;
