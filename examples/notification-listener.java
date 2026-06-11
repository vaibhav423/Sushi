// Example: Check for Notification Listener access
//
// Settings.Secure.getString() requires ContentResolver, which requires
// the process to be registered with ActivityManagerService as a live app.
// app_process daemons are not registered, so this always fails.
//
// Use instead:
//   asu -c 'settings get secure enabled_notification_listeners'

import android.content.*;

// Workaround: read via reflection-based IPC to settings provider directly
// (This also fails — AMS ProcessRecord check blocks unregistered processes)

bridge.toast("Use: asu -c 'settings get secure enabled_notification_listeners'");
return "ContentResolver not available in app_process (process not registered with AMS)";