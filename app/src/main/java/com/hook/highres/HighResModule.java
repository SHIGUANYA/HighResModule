package com.hook.highres;

import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import java.io.*;

public class HighResModule implements IXposedHookLoadPackage {
    private static final String TAG = "HighResModule";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!"com.tencent.tmgp.gnyx".equals(lpparam.packageName)) return;

        Log.i(TAG, "=== Loaded in " + lpparam.packageName + " ===");
        XposedBridge.log("HighResModule: loaded in " + lpparam.packageName);

        new Thread(() -> {
            try {
                Thread.sleep(5000);
                extractAndRun();
            } catch (Throwable t) {
                Log.w(TAG, "Thread failed: " + t.getMessage());
            }
        }, "HighResThread").start();
    }

    private void extractAndRun() throws Exception {
        String exePath = "/data/local/tmp/set_render_level";

        InputStream is = null;
        try {
            is = XposedBridge.class.getClassLoader()
                .getResourceAsStream("/assets/set_render_level");
        } catch (Throwable t) {
            Log.w(TAG, "Cannot read from assets via classloader: " + t.getMessage());
        }

        if (is == null) {
            try {
                java.lang.reflect.Field appField = android.app.ActivityThread.class
                    .getDeclaredMethod("currentActivityThread")
                    .invoke(null);
                Object app = appField.getClass().getDeclaredField("mBoundApplication")
                    .get(appField);
                Object info = app.getClass().getDeclaredField("info").get(app);
                android.content.res.AssetManager assets = (android.content.res.AssetManager)
                    info.getClass().getMethod("getAssets").invoke(info);
                is = assets.open("set_render_level");
                Log.i(TAG, "Opened exe from AssetManager");
            } catch (Throwable t) {
                Log.w(TAG, "AssetManager approach failed: " + t.getMessage());
            }
        }

        if (is == null) {
            Log.w(TAG, "Cannot extract standalone exe, trying direct path");
            runExe(exePath);
            return;
        }

        FileOutputStream fos = new FileOutputStream(exePath);
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
        fos.close();
        is.close();

        Runtime.getRuntime().exec(new String[]{"chmod", "755", exePath}).waitFor();
        Log.i(TAG, "Extracted standalone exe to " + exePath);

        runExe(exePath);
    }

    private void runExe(String exePath) {
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", exePath + " 4"});
            BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            BufferedReader stderr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            String line;
            while ((line = stdout.readLine()) != null) Log.i(TAG, "EXE: " + line);
            while ((line = stderr.readLine()) != null) Log.w(TAG, "EXE_ERR: " + line);
            int exitCode = proc.waitFor();
            Log.i(TAG, "Standalone exe exited with code: " + exitCode);
            XposedBridge.log("HighResModule: exe exit code=" + exitCode);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to run exe: " + t.getMessage());
        }
    }
}
