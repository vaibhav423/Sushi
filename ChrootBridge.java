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
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Base64;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.NameSpace;

public class ChrootBridge {
    private static final String PACKAGE_NAME = "android";
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
        sContext = (Context) getSystemContextMethod.invoke(activityThread);

        try {
            Application app = Instrumentation.newApplication(Application.class, sContext);
            Field mInitialApplicationField = atClass.getDeclaredField("mInitialApplication");
            mInitialApplicationField.setAccessible(true);
            mInitialApplicationField.set(activityThread, app);
        } catch (Throwable t) {}

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
                case "java":
                    byte[] decoded = Base64.getDecoder().decode(args[1]);
                    Object res = sBshInterpreter.eval(new String(decoded, "UTF-8"));
                    return res != null ? res.toString() : "OK";
                case "java-raw":
                    StringBuilder sb = new StringBuilder();
                    for (int i=1; i<args.length; i++) sb.append(args[i]).append(" ");
                    Object res2 = sBshInterpreter.eval(sb.toString().trim());
                    return res2 != null ? res2.toString() : "OK";
                default: return "Unknown module: " + args[0];
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter(); e.printStackTrace(new PrintWriter(sw)); return "ERROR: " + e.getMessage() + "\n" + sw.toString();
        }
    }
}
