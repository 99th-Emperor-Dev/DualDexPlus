package com.enrpau.dualscreendex

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable

/**
 * A round "liquid glass" layer drawn over a button: a very light frosted body, a bright rim that is
 * strongest at the top-left and fades around the edge, a curved specular highlight across the top,
 * and a soft darker inner edge at the bottom.
 */
class LiquidGlassDrawable(private val density: Float, private val dark: Boolean) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = minOf(b.width(), b.height()) / 2f

        // frosted body: brighter in the middle-top, clearer at the edges
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy - r * 0.35f, r * 1.25f,
            intArrayOf(Color.argb(if (dark) 70 else 110, 255, 255, 255), Color.argb(if (dark) 18 else 40, 255, 255, 255)),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)

        // inner shade at the bottom edge, so the glass looks thick
        paint.shader = LinearGradient(cx, cy + r * 0.3f, cx, cy + r,
            Color.TRANSPARENT, Color.argb(if (dark) 60 else 35, 0, 0, 0), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)

        // specular highlight: a soft lens of light across the top
        rect.set(cx - r * 0.72f, cy - r * 0.9f, cx + r * 0.72f, cy - r * 0.05f)
        paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
            Color.argb(150, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        canvas.drawOval(rect, paint)

        // rim: bright at the top-left, fading around, a second glint at the bottom-right
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * density
        paint.shader = SweepGradient(cx, cy,
            intArrayOf(
                Color.argb(70, 255, 255, 255),   // right
                Color.argb(150, 255, 255, 255),  // bottom-right glint
                Color.argb(40, 255, 255, 255),   // bottom-left
                Color.argb(230, 255, 255, 255),  // top-left, brightest
                Color.argb(70, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.12f, 0.4f, 0.62f, 1f))
        canvas.drawCircle(cx, cy, r - paint.strokeWidth / 2f, paint)
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
