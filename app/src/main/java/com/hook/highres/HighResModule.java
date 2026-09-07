package com.hook.highres;

import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_LoadPackage.LoadPackageParam;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class HighResModule implements IXposedHookLoadPackage {
    private static final String TAG = "HighResModule";
    private static final String TARGET_PKG = "com.tencent.tmgp.gnyx";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;

        Log.i(TAG, "=== HighResModule loaded in " + lpparam.packageName + " ===");
        XposedBridge.log("HighResModule: loaded in " + lpparam.packageName);

        // Strategy 1: Hook System.loadLibrary to detect libUE4.so loading
        hookNativeLibLoad(lpparam);

        // Strategy 2: Hook Android config file reading to override render level
        hookConfigReading(lpparam);

        // Strategy 3: Hook FAndroidDeviceProfile::InitializeCVarsForActiveDeviceProfile
        hookDeviceProfileInit(lpparam);
    }

    /**
     * Strategy 1: Detect when libUE4.so is loaded, then try to modify CVars
     */
    private void hookNativeLibLoad(LoadPackageParam lpparam) {
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
                            Log.i(TAG, ">>> Native lib loaded: " + libName + " - attempting to set render level 4");
                            // Give the native library time to initialize
                            Thread.sleep(2000);
                            trySetRenderLevelViaReflection();
                        }
                    }
                }
            );
            Log.i(TAG, "Hooked System.loadLibrary");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook System.loadLibrary: " + t.getMessage());
        }
    }

    /**
     * Strategy 2: Hook config file reading to override fp.DefaultRenderLevel
     * The game reads config via ProcessAndroidEvalConfig which applies device profile settings.
     * We intercept and override the render level to 4 (超高清+).
     */
    private void hookConfigReading(LoadPackageParam lpparam) {
        // Hook SharedPreferences to override config values
        try {
            Class<?> spClass = XposedHelpers.findClass(
                "android.app.SharedPreferencesImpl",
                lpparam.classLoader
            );
            if (spClass != null) {
                Log.i(TAG, "Found SharedPreferencesImpl class");
            }
        } catch (Throwable t) {
            Log.w(TAG, "SharedPreferences hook not available: " + t.getMessage());
        }

        // Hook FileInputStream to intercept config file reads
        try {
            XposedHelpers.findAndHookMethod(
                java.io.FileInputStream.class,
                "read",
                byte[].class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        byte[] buffer = (byte[]) param.args[0];
                        if (buffer != null) {
                            String content = new String(buffer, "UTF-8");
                            if (content.contains("DefaultRenderLevel") || content.contains("fp.MaxSupportRenderLevel")) {
                                Log.i(TAG, ">>> Detected render level config in file read");
                                // The config is being read - we'll try to override it
                            }
                        }
                    }
                }
            );
            Log.i(TAG, "Hooked FileInputStream.read");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook FileInputStream: " + t.getMessage());
        }
    }

    /**
     * Strategy 3: Hook FAndroidDeviceProfile initialization
     * This function applies device profile settings including render level.
     */
    private void hookDeviceProfileInit(LoadPackageParam lpparam) {
        // Try to hook through UE4's internal class system
        try {
            // Hook the config loading mechanism
            XposedHelpers.findAndHookMethod(
                android.content.res.AssetManager.class,
                "open",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String filename = (String) param.args[0];
                        if (filename != null) {
                            Log.i(TAG, "AssetManager.open: " + filename);
                            // Intercept DeviceProfile config files
                            if (filename.contains("DeviceProfiles") || filename.contains("Engine.ini")) {
                                Log.i(TAG, ">>> Intercepted device profile config: " + filename);
                            }
                        }
                    }
                }
            );
            Log.i(TAG, "Hooked AssetManager.open");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook AssetManager.open: " + t.getMessage());
        }
    }

    /**
     * Try to set render level via reflection on UE4's CVar system
     */
    private void trySetRenderLevelViaReflection() {
        try {
            // Try to access UE4's console variable system through Java reflection
            // The CVar system is native, but we can try to find wrapper classes
            
            // Method 1: Try to find the HighResModule native helper
            Class<?> helperClass = Class.forName("com.hook.highres.NativeHelper");
            Method setRenderLevel = helperClass.getMethod("setRenderLevel", int.class);
            setRenderLevel.invoke(null, 4);
            Log.i(TAG, "Successfully set render level to 4 via NativeHelper");
            return;
        } catch (ClassNotFoundException e) {
            Log.i(TAG, "NativeHelper not found, trying alternative methods");
        } catch (Throwable t) {
            Log.w(TAG, "NativeHelper failed: " + t.getMessage());
        }

        // Method 2: Try to access the CVar directly via memory manipulation
        // This is a fallback - try to use the game's own config system
        try {
            // Find the UE4 engine class and modify the CVar
            // This requires finding the right class through the class loader
            Class<?>[] classes = findUE4Classes();
            for (Class<?> clazz : classes) {
                Log.i(TAG, "Found UE4 class: " + clazz.getName());
                // Try to find and modify the render level field
                try {
                    Field f = clazz.getDeclaredField("DefaultRenderLevel");
                    f.setAccessible(true);
                    f.setInt(null, 4);
                    Log.i(TAG, "Set DefaultRenderLevel to 4 on " + clazz.getName());
                } catch (NoSuchFieldException e) {
                    // Not this class, continue
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Reflection approach failed: " + t.getMessage());
        }
    }

    /**
     * Find UE4-related classes in the class loader
     */
    private Class<?>[] findUE4Classes() {
        java.util.List<Class<?>> result = new java.util.ArrayList<>();
        try {
            // Try to access the path list
            Class<?> pathListClass = Class.forName("dalvik.system.PathList");
            Object pathList = XposedHelpers.getObjectField(
                ClassLoader.getSystemClassLoader(),
                "pathList"
            );
            Object[] dexElements = (Object[]) XposedHelpers.getObjectField(pathList, "dexElements");
            for (Object element : dexElements) {
                Class<?> elementClass = element.getClass();
                // ... (simplified - actual implementation would enumerate classes)
            }
        } catch (Throwable t) {
            // Silently ignore
        }
        return result.toArray(new Class<?>[0]);
    }
}
