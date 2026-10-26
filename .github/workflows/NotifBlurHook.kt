package com.example.notifblur

import android.graphics.Canvas
import android.graphics.Color
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

    // Tunables
    private val FILL_ALPHA = 0          // 0 = fully transparent, 25 = light white tint
    private val STROKE_ALPHA = 110      // border strength (0 - 255)
    private val STROKE_DP = 1.2f        // border thickness
    private val FALLBACK_RADIUS_DP = 22f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val rect = RectF()

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return

        val bg = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.notification.row.NotificationBackgroundView",
            lpparam.classLoader
        )
        if (bg == null) {
            XposedBridge.log("NotifBlur: NotificationBackgroundView not found")
            return
        }

        val unhooks = XposedBridge.hookAllMethods(bg, "onDraw", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    drawGlass(param.thisObject as View, param.args[0] as Canvas)
                    param.result = null
                } catch (t: Throwable) {
                    XposedBridge.log("NotifBlur: draw error $t")
                }
            }
        })
        XposedBridge.log("NotifBlur: v6 card onDraw hooks=${unhooks.size}")
    }

    private fun drawGlass(v: View, canvas: Canvas) {
        val d = v.resources.displayMetrics.density
        val w = v.width.toFloat()
        val bottom = readInt(v, "mActualHeight", v.height).toFloat()
        val top = readInt(v, "mBackgroundTop", 0).toFloat()
        val sw = STROKE_DP * d

        rect.set(sw / 2f, top + sw / 2f, w - sw / 2f, bottom - sw / 2f)
        if (rect.width() <= 0f || rect.height() <= 0f) return

        val raw: FloatArray? = try {
            XposedHelpers.getObjectField(v, "mCornerRadii") as? FloatArray
        } catch (t: Throwable) {
            null
        }
        val fallback = FALLBACK_RADIUS_DP * d
        val radii: FloatArray =
            if (raw == null || raw.size < 8 || raw.all { it <= 0f }) FloatArray(8) { fallback } else raw

        path.reset()
        path.addRoundRect(rect, radii, Path.Direction.CW)

        if (FILL_ALPHA > 0) {
            fill.color = Color.argb(FILL_ALPHA, 255, 255, 255)
            canvas.drawPath(path, fill)
        }
        stroke.strokeWidth = sw
        stroke.color = Color.argb(STROKE_ALPHA, 255, 255, 255)
        canvas.drawPath(path, stroke)
    }

    private fun readInt(o: Any, name: String, def: Int): Int =
        try {
            XposedHelpers.getIntField(o, name)
        } catch (t: Throwable) {
            def
        }
}
