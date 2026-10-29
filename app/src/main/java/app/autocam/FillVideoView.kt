package app.autocam

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

/** Fills the frame we sized to the picture, instead of leaving gaps around it. */
class FillVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : VideoView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
    }
}
