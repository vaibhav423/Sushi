// Example: Check for Notification Listener access
//
// NOTE: Settings.Secure.getString() checks calling UID against the
// package name reported by ContentResolver, which bypasses the
// ContextWrapper.getSystemService() patch. This works only when
// running as UID 1000 (no runas2000).
//
// Workaround: call from shell directly:
//   asu -c 'settings get secure enabled_notification_listeners'

bridge.toast("Use: asu -c 'settings get secure enabled_notification_listeners'");
return "Use asu for settings queries (UID 2000 cannot access ContentProvider as android package)";