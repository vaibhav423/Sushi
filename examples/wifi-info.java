// Example: Get WiFi information
import android.content.*;
import android.net.wifi.*;
import java.util.*;

WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
if (wifi != null && wifi.isWifiEnabled()) {
    WifiInfo info = wifi.getConnectionInfo();
    String ssid = info.getSSID();
    if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
        ssid = ssid.substring(1, ssid.length() - 1);
    }
    
    int ip = info.getIpAddress();
    String ipString = String.format("%d.%d.%d.%d", 
        (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
    
    bridge.toast("Connected to: " + ssid);
    bridge.log("WiFi SSID: " + ssid + ", IP: " + ipString);
    return "SSID: " + ssid + ", IP: " + ipString;
} else {
    bridge.toast("WiFi not connected");
    return "WiFi not connected";
}
