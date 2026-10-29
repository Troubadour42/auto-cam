package app.autocam

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** A thin line with a soft halo, drawn on top of the picture. */
class GlowBorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var glowColor: Int = 0xFFFF3B30.toInt()
        set(value) {
            field = value
            invalidate()
        }

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val inset = 3f * density
        val rect = RectF(inset, inset, width - inset, height - inset)
        if (rect.width() <= 0f || rect.height() <= 0f) return
        val radius = 18f * density
        glow.color = glowColor
        glow.alpha = 90
        glow.strokeWidth = 8f * density
        glow.maskFilter = BlurMaskFilter(10f * density, BlurMaskFilter.Blur.NORMAL)
        line.color = glowColor
        line.alpha = 210
        line.strokeWidth = 2.5f * density
        canvas.drawRoundRect(rect, radius, radius, glow)
        canvas.drawRoundRect(rect, radius, radius, line)
    }
}
