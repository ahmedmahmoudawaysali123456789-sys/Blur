package com.example.notifblur

import android.view.View
import android.view.WindowManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class NotifBlurHook : IXposedHookLoadPackage {

    private val SHADE_BLUR = 80
    private val CARD_ALPHA = 0.55f
    private val TYPE_NOTIFICATION_SHADE = 2040

    @Volatile private var panelVisible = false
    @Volatile private var haveSignal = false
    private val seen = HashSet<String>()

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return
        val cl = lpparam.classLoader

        hookShadeSignal(cl)
        hookWindowBlur(cl)
        hookCards(cl)
        XposedBridge.log("NotifBlur: v5 hooks installed")
    }

    private fun hookShadeSignal(cl: ClassLoader) {
        val ctrl = listOf(
            "com.android.systemui.shade.NotificationShadeWindowControllerImpl",
            "com.android.systemui.statusbar.phone.NotificationShadeWindowControllerImpl"
        ).firstNotNullOfOrNull { XposedHelpers.findClassIfExists(it, cl) }

        if (ctrl == null) {
            XposedBridge.log("NotifBlur: shade controller class not found")
            return
        }
        XposedBridge.log("NotifBlur: controller = ${ctrl.name}")
        val unhooks = XposedBridge.hookAllMethods(ctrl, "setPanelVisible", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val v = param.args.firstOrNull() as? Boolean ?: return
                panelVisible = v
                XposedBridge.log("NotifBlur: panelVisible=$v")
            }
        })
        haveSignal = unhooks.isNotEmpty()
        XposedBridge.log("NotifBlur: haveSignal=$haveSignal")
    }

    private fun hookWindowBlur(cl: ClassLoader) {
        val wmg = XposedHelpers.findClassIfExists("android.view.WindowManagerGlobal", cl)
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
    }

    private fun handle(lp: WindowManager.LayoutParams) {
        if (lp.type != TYPE_NOTIFICATION_SHADE) return

        val on = if (haveSignal) {
            panelVisible
        } else {
            lp.height == WindowManager.LayoutParams.MATCH_PARENT
        }

        val key = "on=$on h=${lp.height}"
        synchronized(seen) {
            if (seen.size < 30 && seen.add(key)) {
                XposedBridge.log("NotifBlur: shade window $key")
            }
        }

        if (on) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = SHADE_BLUR
        } else {
            lp.blurBehindRadius = 0
        }
    }

    private fun hookCards(cl: ClassLoader) {
        val bg = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.notification.row.NotificationBackgroundView", cl
        )
        if (bg == null) {
            XposedBridge.log("NotifBlur: NotificationBackgroundView not found")
            return
        }

        XposedHelpers.findAndHookMethod(
            View::class.java, "setAlpha", Float::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!bg.isInstance(param.thisObject)) return
                    val a = param.args[0] as? Float ?: return
                    param.args[0] = a * CARD_ALPHA
                }
            }
        )

        XposedBridge.hookAllConstructors(bg, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                (param.thisObject as? View)?.alpha = 1f
            }
        })
        XposedBridge.log("NotifBlur: card hook installed")
    }
}
