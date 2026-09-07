package com.hook.highres;

import android.util.Log;

public class NativeHelper {
    private static final String TAG = "HighResNative";
    private static boolean nativeLoaded = false;

    static {
        try {
            System.loadLibrary("HighResNative");
            nativeLoaded = true;
            Log.i(TAG, "Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library: " + e.getMessage());
            nativeLoaded = false;
        }
    }

    /**
     * Set the render level CVar value.
     * Level 3 = 超高清 (1080p), Level 4 = 超高清+ (更高分辨率)
     * @param level The render level to set (3 or 4)
     * @return true if successful
     */
    public static native boolean setRenderLevel(int level);

    /**
     * Find and hook the CVar system using UE4's internal functions.
     * @return true if successful
     */
    public static native boolean findAndHookCVar();

    public static boolean isNativeLoaded() {
        return nativeLoaded;
    }
}
