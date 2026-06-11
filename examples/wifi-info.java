// Example: Get WiFi connection info
import android.net.wifi.*;
import android.content.*;

WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
WifiInfo info = wifi.getConnectionInfo();

String result = "SSID: " + info.getSSID()
    + "\nBSSID: " + info.getBSSID()
    + "\nIP: " + android.text.format.Formatter.formatIpAddress(info.getIpAddress())
    + "\nRSSI: " + info.getRssi() + " dBm"
    + "\nLink speed: " + info.getLinkSpeed() + " Mbps";

bridge.log(result);
bridge.toast("WiFi: " + info.getSSID());
return result;