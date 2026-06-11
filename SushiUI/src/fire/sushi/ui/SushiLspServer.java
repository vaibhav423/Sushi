package fire.sushi.ui;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.app.AlertDialog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;

public class SushiLspServer {
    private static final String TAG = "SushiUI";
    private static final String SOCKET_NAME = "sushi-ui";
    private static volatile boolean sStarted = false;

    public static void start(Context context) {
        if (sStarted) return;
        sStarted = true;
        new Thread(() -> run(context)).start();
    }

    private static void run(Context context) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        try (LocalServerSocket server = new LocalServerSocket(SOCKET_NAME)) {
            Log.i(TAG, "Listening on @" + SOCKET_NAME);
            while (true) {
                LocalSocket client = server.accept();
                new Thread(() -> handle(client, context, mainHandler)).start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Server died", e);
        }
    }

    private static void handle(LocalSocket client, Context context, Handler h) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true);
             LocalSocket c = client) {
            String line = in.readLine();
            if (line == null) return;
            String[] parts = line.split("\\|");
            if (!parts[0].equals("dialog")) { out.println("error"); return; }
            final String title = parts.length > 1 ? parts[1] : "";
            final String msg  = parts.length > 2 ? parts[2] : "";
            final String[] btns = parts.length > 3 ? parts[3].split(",") : new String[]{"OK"};
            final String[] res = new String[1];
            final Object lock = new Object();
            h.post(() -> {
                try {
                    AlertDialog.Builder b = new AlertDialog.Builder(context,
                        android.R.style.Theme_DeviceDefault_Dialog_Alert);
                    b.setTitle(title).setMessage(msg).setCancelable(false);
                    if (btns.length > 0) b.setPositiveButton(btns[0], (d,w) -> { res[0]=btns[0]; synchronized(lock){lock.notify();} });
                    if (btns.length > 1) b.setNegativeButton(btns[1], (d,w) -> { res[0]=btns[1]; synchronized(lock){lock.notify();} });
                    if (btns.length > 2) b.setNeutralButton(btns[2], (d,w) -> { res[0]=btns[2]; synchronized(lock){lock.notify();} });
                    AlertDialog d = b.create();
                    d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                    d.show();
                } catch (Exception e) {
                    res[0] = "err:" + e.getMessage();
                    synchronized(lock){lock.notify();}
                }
            });
            synchronized (lock) { lock.wait(30000); }
            out.println(res[0] != null ? res[0] : "timeout");
        } catch (Exception e) {
            Log.e(TAG, "handle error", e);
        }
    }
}
