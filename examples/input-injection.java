// Example: Inject text input and press enter
import android.hardware.input.*;
import android.os.*;

// Type "Hello World" by injecting key events
String text = "Hello World";

// Get InputManager
InputManager im = (InputManager) context.getSystemService(Context.INPUT_SERVICE);

// For each character, inject key down and key up events
long now = SystemClock.uptimeMillis();

for (char c : text.toCharArray()) {
    int keyCode = KeyEvent.keyCodeFromString("KEYCODE_" + Character.toUpperCase(c));
    if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
        
        im.injectInputEvent(down, 0);
        im.injectInputEvent(up, 0);
        
        Thread.sleep(50); // Small delay between keys
    }
}

// Press Enter
bridge.injectEnter();

bridge.toast("Text injected: " + text);
return "Injected: " + text;
