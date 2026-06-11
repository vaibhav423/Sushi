// Example: Inject text input by injecting key events
import android.hardware.input.*;
import android.os.*;
import android.view.KeyEvent;

String text = "Hello World";

InputManager im = (InputManager) context.getSystemService(Context.INPUT_SERVICE);

for (char c : text.toCharArray()) {
    int keyCode = -1;
    if (c >= 'a' && c <= 'z') keyCode = KeyEvent.KEYCODE_A + (c - 'a');
    else if (c >= 'A' && c <= 'Z') keyCode = KeyEvent.KEYCODE_A + (c - 'A');
    else if (c >= '0' && c <= '9') keyCode = KeyEvent.KEYCODE_0 + (c - '0');
    else if (c == ' ') keyCode = KeyEvent.KEYCODE_SPACE;
    else if (c == '.') keyCode = KeyEvent.KEYCODE_PERIOD;

    if (keyCode > 0) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
        im.injectInputEvent(down, 0);
        im.injectInputEvent(up, 0);
        Thread.sleep(50);
    }
}

long now = SystemClock.uptimeMillis();
KeyEvent enterDown = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0);
KeyEvent enterUp = new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0);
im.injectInputEvent(enterDown, 0);
im.injectInputEvent(enterUp, 0);

bridge.toast("Injected: " + text);
return "Injected: " + text;