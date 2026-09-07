package com.hook.highres;

import android.util.Log;
import v.Bsgm.HDFKz.IXposedHookLoadPackage;
import v.Bsgm.HDFKz.XC_MethodHook;
import v.Bsgm.HDFKz.XposedBridge;
import v.Bsgm.HDFKz.callbacks.XC_LoadPackage;
import v.Bsgm.HDFKz.callbacks.XC_LoadPackage.LoadPackageParam;

public class HighResModule implements IXposedHookLoadPackage {
    private static final String TAG = "HighResModule";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!"com.tencent.tmgp.gnyx".equals(lpparam.packageName)) return;

        Log.i(TAG, "=== Loaded in " + lpparam.packageName + " ===");
        XposedBridge.log("HighResModule: loaded in " + lpparam.packageName);

        hookSystemLoadLibrary(lpparam);
    }

    private void hookSystemLoadLibrary(LoadPackageParam lpparam) {
        try {
            java.lang.reflect.Method loadLib = System.class.getDeclaredMethod("loadLibrary", String.class);
            Object unhook = XposedBridge.hookMethod(loadLib, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String libName = (String) param.args[0];
                    Log.i(TAG, "System.loadLibrary: " + libName);
                    if ("UE4".equals(libName) || "gn_game".equals(libName)) {
                        Log.i(TAG, "Native lib loaded: " + libName);
                        XposedBridge.log("HighResModule: native lib loaded: " + libName);
                        Thread.sleep(3000);
                        trySetRenderLevel();
                    }
                }
            });
            Log.i(TAG, "Hooked System.loadLibrary");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook System.loadLibrary: " + t.getMessage());
        }
    }

    private void trySetRenderLevel() {
        try {
            NativeHelper.setRenderLevel(4);
            Log.i(TAG, "NativeHelper.setRenderLevel(4) success");
            XposedBridge.log("HighResModule: set render level 4 via native");
        } catch (Throwable t) {
            Log.w(TAG, "NativeHelper failed: " + t.getMessage());
            XposedBridge.log("HighResModule: native failed - " + t.getMessage());
        }
    }
}
