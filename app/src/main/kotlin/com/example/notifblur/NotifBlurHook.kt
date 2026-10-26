package com.example.notifblur

import android.view.WindowManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class NotifBlurHook : IXposedHookLoadPackage {

    private val SHADE_BLUR = 80
    private val TYPE_NOTIFICATION_SHADE = 2040
    private val seen = HashSet<String>()

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return

        val wmg = XposedHelpers.findClassIfExists(
            "android.view.WindowManagerGlobal", lpparam.classLoader
        )
        if (wmg == null) {
            XposedBridge.log("NotifBlur: WindowManagerGlobal not found")
            return
        }

        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                for (a in param.args) {
                    if (a is WindowManager.LayoutParams) {
                        handle(a)
                    }
                }
            }
        }
        XposedBridge.hookAllMethods(wmg, "addView", hook)
        XposedBridge.hookAllMethods(wmg, "updateViewLayout", hook)
        XposedBridge.log("NotifBlur: v3 hooks installed")
    }

    private fun handle(lp: WindowManager.LayoutParams) {
        val key = "type=${lp.type} title=${lp.title} h=${lp.height}"
        synchronized(seen) {
            if (seen.size < 60 && seen.add(key)) {
                XposedBridge.log("NotifBlur: window $key")
            }
        }
        if (lp.type == TYPE_NOTIFICATION_SHADE) {
            if (lp.height == WindowManager.LayoutParams.MATCH_PARENT) {
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                lp.blurBehindRadius = SHADE_BLUR
            } else {
                lp.blurBehindRadius = 0
            }
        }
    }
}
