// Example: Get clipboard content and show toast
import android.content.*;
import android.widget.*;

String content = bridge.clipboardGet();
bridge.toast("Clipboard: " + content);
return content;
