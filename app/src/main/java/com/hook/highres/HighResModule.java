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

    private void extractAndRun() {
        String exePath = "/data/local/tmp/set_render_level";

        try {
            Runtime.getRuntime().exec(new String[]{"chmod", "755", exePath}).waitFor();
            runExe(exePath);
        } catch (Throwable t) {
            Log.w(TAG, "Standalone exe not found or failed: " + t.getMessage());
        }
    }

    private void runExe(String exePath) {
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", exePath + " 4"});
            BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            BufferedReader stderr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            String line;
            while ((line = stdout.readLine()) != null) {
                Log.i(TAG, "EXE: " + line);
                XposedBridge.log("HighResModule: " + line);
            }
            while ((line = stderr.readLine()) != null) Log.w(TAG, "EXE_ERR: " + line);
            int exitCode = proc.waitFor();
            Log.i(TAG, "Standalone exe exited with code: " + exitCode);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to run exe: " + t.getMessage());
        }
    }
}
