package de.robv.android.xposed;
public class XposedBridge {
    public static void log(String s) { android.util.Log.i("Xposed", s); }
    public static void log(Throwable t) { android.util.Log.e("Xposed", "error", t); }
}
