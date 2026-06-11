package fire.sushi.ui;

import android.content.Context;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SushiLspModule implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui")) return;
        XposedBridge.log("SushiUI: hooked SystemUI, waiting for onCreate...");
        XposedHelpers.findAndHookMethod(
            "com.android.systemui.SystemUIApplication",
            lpparam.classLoader,
            "onCreate",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Context ctx = (Context) param.thisObject;
                    XposedBridge.log("SushiUI: got context, starting server...");
                    SushiLspServer.start(ctx);
                }
            }
        );
    }
}
