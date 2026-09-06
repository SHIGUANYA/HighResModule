package com.hook.highres

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam

class HighResModule : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.tencent.tmgp.gnyx") return

        XposedBridge.log("[HighResModule] 高能英雄已加载，开始 Hook 分辨率")

        // Hook FastPictureModule.Init
        try {
            XposedHelpers.findAndHookMethod(
                "com.tencent.fastpicture.FastPictureModule",
                lpparam.classLoader,
                "Init",
                XC_LoadPackage.LoadPackageParam::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("[HighResModule] FastPictureModule.Init 被调用，强制分辨率")
                        try {
                            val engineClass = Class.forName("com.epicgames.ue4.GameEngine")
                            val getCvarManager = engineClass.getDeclaredMethod("GetConsoleVariableManager")
                            getCvarManager.isAccessible = true
                            val cvarManager = getCvarManager.invoke(null)
                            if (cvarManager != null) {
                                val setCvar = cvarManager.javaClass.getDeclaredMethod("SetCVar", String::class.java, Any::class.java)
                                setCvar.isAccessible = true
                                setCvar.invoke(cvarManager, "fp.DefaultRenderLevel", 4)
                                setCvar.invoke(cvarManager, "fp.MaxSupportRenderLevel", 6)
                                setCvar.invoke(cvarManager, "r.MobileContentScaleFactor", 2.0f)
                                XposedBridge.log("[HighResModule] 强制设置: Level 4, MaxLevel 6, Scale 2.0")
                            }
                        } catch (e: Throwable) {
                            XposedBridge.log("[HighResModule] 反射异常: ${e.message}")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("[HighResModule] FastPictureModule.Init hook 失败: ${e.message}")
        }

        // 备选：Hook GameEngine.initialize
        try {
            XposedHelpers.findAndHookMethod(
                "com.epicgames.ue4.GameEngine",
                lpparam.classLoader,
                "initialize",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val engineClass = Class.forName("com.epicgames.ue4.GameEngine")
                            val getCvarManager = engineClass.getDeclaredMethod("GetConsoleVariableManager")
                            getCvarManager.isAccessible = true
                            val cvarManager = getCvarManager.invoke(null)
                            if (cvarManager != null) {
                                val setCvar = cvarManager.javaClass.getDeclaredMethod("SetCVar", String::class.java, Any::class.java)
                                setCvar.isAccessible = true
                                setCvar.invoke(cvarManager, "fp.DefaultRenderLevel", 4)
                                setCvar.invoke(cvarManager, "fp.MaxSupportRenderLevel", 6)
                                setCvar.invoke(cvarManager, "r.MobileContentScaleFactor", 2.0f)
                                XposedBridge.log("[HighResModule] GameEngine.initialize 后强制分辨率")
                            }
                        } catch (e: Throwable) {
                            XposedBridge.log("[HighResModule] GameEngine hook 异常: ${e.message}")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("[HighResModule] GameEngine hook 注册失败: ${e.message}")
        }
    }
}
