import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
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
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Base64;

import bsh.Interpreter;

public class ChrootBridge {
    private static final String PACKAGE_NAME = "com.android.shell";
    private static final String SOCKET_NAME = "android-bridge";

    private static Context sContext;
    private static Handler sMainHandler;
    private static final Map<String, Object> sGlobalVariables = new HashMap<>();
    private static Interpreter sBshInterpreter;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) System.exit(1);

        Looper.prepareMainLooper();
        sMainHandler = new Handler(Looper.getMainLooper());

        Class<?> atClass = Class.forName("android.app.ActivityThread");
        Constructor<?> atCtor = atClass.getDeclaredConstructor();
        atCtor.setAccessible(true);
        Object activityThread = atCtor.newInstance();

        Field sCurrentField = atClass.getDeclaredField("sCurrentActivityThread");
        sCurrentField.setAccessible(true);
        sCurrentField.set(null, activityThread);

        Field mSystemThreadField = atClass.getDeclaredField("mSystemThread");
        mSystemThreadField.setAccessible(true);
        mSystemThreadField.setBoolean(activityThread, true);

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
                try {
                    Field f = service.getClass().getDeclaredField("mContext");
                    f.setAccessible(true);
                    f.set(service, this);
                } catch (Exception e) {}
                return service;
            }
        };

        initBeanShell();

        if ("daemon".equals(args[0])) {
            new Thread(ChrootBridge::startDaemon).start();
            Looper.loop();
        } else {
            System.out.println(handleCommand(args));
        }
    }

    private static void initBeanShell() {
        try {
            sBshInterpreter = new Interpreter();
            sBshInterpreter.set("context", sContext);
            sBshInterpreter.set("handler", sMainHandler);
            sBshInterpreter.set("vars", sGlobalVariables);
            sBshInterpreter.set("bridge", new BridgeHelper());
        } catch (Exception e) {}
    }

    public static class BridgeHelper {
        public void log(String s) { System.out.println(s); }
        public void setVar(String k, Object v) { sGlobalVariables.put(k, v); }
        public Object getVar(String k) { return sGlobalVariables.get(k); }
        public void toast(String text) {
            sMainHandler.post(() -> Toast.makeText(sContext, text, Toast.LENGTH_SHORT).show());
        }
        public String clipboardGet() {
            ClipboardManager cm = (ClipboardManager) sContext.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return null;
            ClipData cd = cm.getPrimaryClip();
            if (cd == null || cd.getItemCount() == 0) return null;
            return cd.getItemAt(0).coerceToText(sContext).toString();
        }
        public void clipboardSet(String text) {
            ClipboardManager cm = (ClipboardManager) sContext.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return;
            cm.setPrimaryClip(ClipData.newPlainText("label", text));
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
                        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                        PrintWriter out = new PrintWriter(client.getOutputStream(), true)
                    ) {
                        String line = in.readLine();
                        if (line != null) {
                            String response = handleCommand(line.split(" "));
                            if (response != null) out.println(response);
                        }
                    } catch (Exception e) {} finally {
                        try { client.close(); } catch (Exception e) {}
                    }
                });
            }
        } catch (Exception e) {}
    }

    private static String handleCommand(String[] args) {
        if (args == null || args.length == 0) return "Error";
        try {
            switch (args[0]) {
                case "hello": return "Hello from ChrootBridge!";
                case "toast": {
                    StringBuilder sb = new StringBuilder();
                    for (int i=1; i<args.length; i++) sb.append(args[i]).append(" ");
                    final String msg = sb.toString().trim();
                    sMainHandler.post(() -> Toast.makeText(sContext, msg, Toast.LENGTH_SHORT).show());
                    return "OK";
                }
                case "input": {
                    if (args.length > 2 && "text".equals(args[1])) {
                        injectText(args[2]);
                        return "OK";
                    }
                    return "Usage: input text <string>";
                }
                case "clipboard": {
                    ClipboardManager cm = (ClipboardManager) sContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (args.length > 1 && "get".equals(args[1])) {
                        ClipData clip = cm.getPrimaryClip();
                        if (clip != null && clip.getItemCount() > 0) {
                            CharSequence text = clip.getItemAt(0).getText();
                            return text != null ? text.toString() : "";
                        }
                        return "";
                    } else if (args.length > 2 && "set".equals(args[1])) {
                        StringBuilder sb = new StringBuilder();
                        for (int i=2; i<args.length; i++) sb.append(args[i]).append(" ");
                        cm.setPrimaryClip(ClipData.newPlainText("label", sb.toString().trim()));
                        return "OK";
                    }
                    return "Usage: clipboard get|set <text>";
                }
                case "clipboard-set": {
                    byte[] d = Base64.getDecoder().decode(args[1]);
                    String text = new String(d, "UTF-8");
                    ClipboardManager cm = (ClipboardManager) sContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("label", text));
                    return "OK";
                }
                case "java":
                    byte[] decoded = Base64.getDecoder().decode(args[1]);
                    Object res = sBshInterpreter.eval(new String(decoded, "UTF-8"));
                    return res != null ? res.toString() : "OK";
                case "java-raw": {
                    StringBuilder sb = new StringBuilder();
                    for (int i=1; i<args.length; i++) sb.append(args[i]).append(" ");
                    Object res2 = sBshInterpreter.eval(sb.toString().trim());
                    return res2 != null ? res2.toString() : "OK";
                }
                default: return "Unknown module: " + args[0];
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter(); e.printStackTrace(new PrintWriter(sw)); return "ERROR: " + e.getMessage() + "\n" + sw.toString();
        }
    }

    private static void injectText(String text) {
        try {
            InputManager im = (InputManager) sContext.getSystemService(Context.INPUT_SERVICE);
            Method method = InputManager.class.getMethod("injectInputEvent", InputEvent.class, int.class);
            for (char c : text.toCharArray()) {
                int keyCode = keyCodeOf(c);
                if (keyCode > 0) {
                    long now = SystemClock.uptimeMillis();
                    KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
                    KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
                    method.invoke(im, down, 0);
                    method.invoke(im, up, 0);
                }
            }
        } catch (Exception e) {}
    }

    private static int keyCodeOf(char c) {
        if (c >= 'a' && c <= 'z') return KeyEvent.keyCodeFromString("KEYCODE_" + Character.toUpperCase(c));
        if (c >= '0' && c <= '9') return KeyEvent.keyCodeFromString("KEYCODE_" + c);
        if (c == '\n') return KeyEvent.KEYCODE_ENTER;
        if (c == ' ') return KeyEvent.KEYCODE_SPACE;
        if (c == '.') return KeyEvent.KEYCODE_PERIOD;
        if (c == ',') return KeyEvent.KEYCODE_COMMA;
        if (c == '/') return KeyEvent.KEYCODE_SLASH;
        return -1;
    }
}
