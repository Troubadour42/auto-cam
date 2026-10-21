package app.autocam

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** 16:9 frame used for the home preview and the playback window. */
class AspectFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        var width = if (widthMode == MeasureSpec.UNSPECIFIED) heightSize * 16 / 9 else widthSize
        var height = width * 9 / 16
        if (heightMode == MeasureSpec.AT_MOST && heightSize > 0 && height > heightSize) {
            height = heightSize
            width = height * 16 / 9
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }
}
