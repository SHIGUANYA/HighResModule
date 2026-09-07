package com.hook.highres;

import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import java.lang.reflect.Method;

public class HighResModule implements IXposedHookLoadPackage {
    private static final String TAG = "HighResModule";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!"com.tencent.tmgp.gnyx".equals(lpparam.packageName)) return;

        Log.i(TAG, "=== Loaded in " + lpparam.packageName + " ===");
        XposedBridge.log("HighResModule: loaded in " + lpparam.packageName);

        hookViaReflection();
    }

    private void hookViaReflection() {
        try {
            Class<?> xbClass = XposedBridge.class;
            Method hookMethod = null;
            for (Method m : xbClass.getDeclaredMethods()) {
                if ("hookMethod".equals(m.getName()) && m.getParameterCount() == 2) {
                    hookMethod = m;
                    break;
                }
            }
            if (hookMethod == null) {
                Log.w(TAG, "hookMethod not found in XposedBridge");
                return;
            }
            hookMethod.setAccessible(true);

            Method loadLibMethod = System.class.getDeclaredMethod("loadLibrary", String.class);

            final Method finalHook = hookMethod;

            XC_MethodHook callback = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String libName = (String) param.args[0];
                    Log.i(TAG, "System.loadLibrary: " + libName);
                    if ("UE4".equals(libName) || "gn_game".equals(libName)) {
                        Log.i(TAG, "Native lib loaded: " + libName);
                        Thread.sleep(3000);
                        trySetRenderLevel();
                    }
                }
            };

            finalHook.invoke(null, loadLibMethod, callback);
            Log.i(TAG, "Hooked via reflection OK");
        } catch (Throwable t) {
            Log.w(TAG, "Reflection hook failed: " + t.getClass().getName() + " - " + t.getMessage());
            XposedBridge.log("HighResModule: hook failed - " + t.getMessage());
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
