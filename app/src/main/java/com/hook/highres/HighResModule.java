package com.hook.highres;

import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_LoadPackage.LoadPackageParam;

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
            XposedHelpers.findAndHookMethod(
                System.class,
                "loadLibrary",
                String.class,
                new XC_MethodHook() {
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
                }
            );
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
