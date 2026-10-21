package app.autocam

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** Keeps the preview the same shape as the camera, so the picture is not stretched. */
class AspectFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    var ratio: Float = 3f / 4f
        set(value) {
            field = value.coerceIn(0.3f, 3f)
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
