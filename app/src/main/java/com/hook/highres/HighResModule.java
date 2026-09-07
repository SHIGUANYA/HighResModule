package com.hook.highres;

import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class HighResModule implements IXposedHookLoadPackage {
    private static final String TAG = "HighResModule";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!"com.tencent.tmgp.gnyx".equals(lpparam.packageName)) return;

        Log.i(TAG, "=== Loaded in " + lpparam.packageName + " ===");
        XposedBridge.log("HighResModule: loaded in " + lpparam.packageName);

        new Thread(() -> {
            for (int i = 0; i < 15; i++) {
                try {
                    Thread.sleep(2000);
                    Log.i(TAG, "Attempt " + (i + 1) + "/15 to set render level");
                    boolean ok = NativeHelper.setRenderLevel(4);
                    if (ok) {
                        Log.i(TAG, "SUCCESS! Render level set to 4 in main process");
                        XposedBridge.log("HighResModule: render level 4 set in main process!");
                        return;
                    }
                    Log.i(TAG, "Attempt " + (i + 1) + " returned false");
                } catch (Throwable t) {
                    Log.w(TAG, "Attempt " + (i + 1) + " failed: " + t.getClass().getSimpleName() + " " + t.getMessage());
                }
            }
            Log.w(TAG, "All 15 attempts failed");
            XposedBridge.log("HighResModule: all attempts failed");
        }, "HighResThread").start();
    }
}
