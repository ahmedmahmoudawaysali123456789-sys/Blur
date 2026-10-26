package com.example.notifblur

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class NotifBlurHook : IXposedHookLoadPackage {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val rect = RectF()

    override fun handleLoadPackage(lp: XC_LoadPackage.LoadPackageParam) {
        if (lp.packageName != "com.android.systemui") return
        val bg = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.notification.row.NotificationBackgroundView",
            lp.classLoader
        )
        if (bg == null) {
            XposedBridge.log("NotifBlur: card class not found")
            return
        }
        val hooks = XposedBridge.hookAllMethods(bg, "onDraw", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    draw(param.thisObject as View, param.args[0] as Canvas)
                    param.result = null
                } catch (t: Throwable) {
                    XposedBridge.log("NotifBlur: draw error $t")
                }
            }
        })
        XposedBridge.log("NotifBlur: v6 hooks=${hooks.size}")
    }

    private fun draw(v: View, c: Canvas) {
        val d = v.resources.displayMetrics.density
        val sw = 1.2f * d
        val bottom = XposedHelpers.getIntField(v, "mActualHeight").toFloat()
        val top = XposedHelpers.getIntField(v, "mBackgroundTop").toFloat()
        rect.set(sw / 2, top + sw / 2, v.width - sw / 2, bottom - sw / 2)
        if (rect.height() > 1f) {
            val r = 22f * d
            path.reset()
            path.addRoundRect(rect, r, r, Path.Direction.CW)
            paint.strokeWidth = sw
            paint.setARGB(110, 255, 255, 255)
            c.drawPath(path, paint)
        }
    }
}
