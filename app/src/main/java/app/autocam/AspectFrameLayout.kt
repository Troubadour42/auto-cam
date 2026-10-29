package app.autocam

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** Frame that keeps the same shape as the picture, so a border can sit on its edge. */
class AspectFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    var ratio: Float = 16f / 9f
        set(value) {
            val next = value.coerceIn(0.25f, 4f)
            if (field == next) return
            field = next
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        var width = if (widthMode == MeasureSpec.UNSPECIFIED) (heightSize * ratio).toInt() else widthSize
        var height = (width / ratio).toInt()
        if (heightMode == MeasureSpec.AT_MOST && heightSize > 0 && height > heightSize) {
            height = heightSize
            width = (height * ratio).toInt()
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }
}
