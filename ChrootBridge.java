import android.app.Application;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.hardware.input.InputManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChrootBridge {
    private static final String PACKAGE_NAME = "com.android.shell";
    private static final String SOCKET_NAME = "android-bridge";

    private static Context sContext;
    private static Handler sMainHandler;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: ChrootBridge <module> [args...]");
            System.exit(1);
        }

        // Must prepare Looper before ActivityThread constructor (it creates a Handler internally)
        Looper.prepareMainLooper();
        sMainHandler = new Handler(Looper.getMainLooper());

        // scrcpy-style init: construct ActivityThread directly, no systemMain()
        Class<?> atClass = Class.forName("android.app.ActivityThread");
        Constructor<?> atCtor = atClass.getDeclaredConstructor();
        atCtor.setAccessible(true);
        Object activityThread = atCtor.newInstance();

        // sCurrentActivityThread = activityThread
        Field sCurrentField = atClass.getDeclaredField("sCurrentActivityThread");
        sCurrentField.setAccessible(true);
        sCurrentField.set(null, activityThread);

        // mSystemThread = true
        Field mSystemThreadField = atClass.getDeclaredField("mSystemThread");
        mSystemThreadField.setAccessible(true);
        mSystemThreadField.setBoolean(activityThread, true);

        // Fill AppBindData so getPackageName() works
        try {
            Class<?> appBindDataClass = Class.forName("android.app.ActivityThread$AppBindData");
            Constructor<?> abdCtor = appBindDataClass.getDeclaredConstructor();
            abdCtor.setAccessible(true);
            Object appBindData = abdCtor.newInstance();

            ApplicationInfo appInfo = new ApplicationInfo();
            appInfo.packageName = PACKAGE_NAME;
            Field appInfoField = appBindDataClass.getDeclaredField("appInfo");
            appInfoField.setAccessible(true);
            appInfoField.set(appBindData, appInfo);

            Field mBoundApplicationField = atClass.getDeclaredField("mBoundApplication");
            mBoundApplicationField.setAccessible(true);
            mBoundApplicationField.set(activityThread, appBindData);
        } catch (Throwable t) {
            System.err.println("WARN: fillAppInfo failed: " + t.getMessage());
        }

        // Build FakeContext from getSystemContext() via the new activityThread
        // getSystemContext() on a fresh ActivityThread returns a ContextImpl backed
        // by the system, but without calling systemMain() so it won't block.
        Method getSystemContextMethod = atClass.getDeclaredMethod("getSystemContext");
        getSystemContextMethod.setAccessible(true);
        Context baseContext = (Context) getSystemContextMethod.invoke(activityThread);

        sContext = new ContextWrapper(baseContext) {
            @Override public String getPackageName()   { return PACKAGE_NAME; }
            @Override public String getOpPackageName() { return PACKAGE_NAME; }
            @Override public Context getApplicationContext() { return this; }

            @Override
            public Object getSystemService(String name) {
                Object service = super.getSystemService(name);
                if (service == null) return null;
                // Patch mContext on clipboard and activity managers so they use our package name
                if (Context.CLIPBOARD_SERVICE.equals(name) || Context.ACTIVITY_SERVICE.equals(name)) {
                    try {
                        Field f = service.getClass().getDeclaredField("mContext");
                        f.setAccessible(true);
                        f.set(service, this);
                    } catch (Exception e) { /* ignore */ }
                }
                return service;
            }
        };

        // Fill mInitialApplication so things like Toast don't NPE
        try {
            Application app = Instrumentation.newApplication(Application.class, sContext);
            Field mInitialApplicationField = atClass.getDeclaredField("mInitialApplication");
            mInitialApplicationField.setAccessible(true);
            mInitialApplicationField.set(activityThread, app);
        } catch (Throwable t) {
            System.err.println("WARN: fillAppContext failed: " + t.getMessage());
        }

        if ("daemon".equals(args[0])) {
            new Thread(ChrootBridge::startDaemon).start();
            Looper.loop();
        } else {
            String response = handleCommand(args);
            if (response != null && !response.isEmpty()) {
                System.out.println(response);
            }
        }
    }

    private static void startDaemon() {
        System.out.println("ChrootBridge daemon listening on @" + SOCKET_NAME);
        ExecutorService executor = Executors.newCachedThreadPool();
        try (LocalServerSocket server = new LocalServerSocket(SOCKET_NAME)) {
            while (true) {
                final LocalSocket client = server.accept();
                executor.submit(() -> {
                    try (
                        BufferedReader in  = new BufferedReader(new InputStreamReader(client.getInputStream()));
                        PrintWriter   out  = new PrintWriter(client.getOutputStream(), true)
                    ) {
                        String line = in.readLine();
                        if (line != null) {
                            String[] cmdArgs = line.split(" ");
                            String response = handleCommand(cmdArgs);
                            if (response != null) out.println(response);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        try { client.close(); } catch (Exception ignore) {}
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String handleCommand(String[] args) {
        if (args == null || args.length == 0) return "Error: no command";
        String module = args[0];
        try {
            switch (module) {
                case "hello":
                    return "Hello from ChrootBridge!";

                case "toast": {
                    if (args.length < 2) return "Usage: toast <message>";
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i < args.length; i++) sb.append(args[i]).append(" ");
                    final String msg = sb.toString().trim();
                    sMainHandler.post(() -> {
                        try {
                            Toast.makeText(sContext, msg, Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    return "Toast shown!";
                }

                case "input": {
                    if (args.length > 2 && "text".equals(args[1])) {
                        injectText(args[2]);
                        return "Text injected!";
                    }
                    return "Usage: input text <string>";
                }

                case "clipboard": {
                    if (args.length > 1 && "get".equals(args[1])) {
                        ClipboardManager cm = (ClipboardManager) sContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = cm.getPrimaryClip();
                        if (clip != null && clip.getItemCount() > 0) {
                            CharSequence text = clip.getItemAt(0).getText();
                            return text != null ? text.toString() : "";
                        }
                        return "";
                    } else if (args.length > 2 && "set".equals(args[1])) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 2; i < args.length; i++) sb.append(args[i]).append(" ");
                        ClipboardManager cm = (ClipboardManager) sContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText(PACKAGE_NAME, sb.toString().trim()));
                        return "Clipboard set!";
                    }
                    return "Usage: clipboard <get|set> [text]";
                }

                default:
                    return "Unknown module: " + module;
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private static void injectText(String text) {
        try {
            InputManager im = (InputManager) sContext.getSystemService(Context.INPUT_SERVICE);
            Method injectInputEvent = im.getClass().getMethod("injectInputEvent", InputEvent.class, int.class);
            if ("enter".equals(text)) {
                long now = SystemClock.uptimeMillis();
                injectInputEvent.invoke(im, new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0), 0);
                injectInputEvent.invoke(im, new KeyEvent(now, now, KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_ENTER, 0), 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
