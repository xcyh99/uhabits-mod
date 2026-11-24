package org.isoron.uhabits.widgets

import android.content.Context
import android.util.AttributeSet
import android.widget.RemoteViews
import android.widget.TextClock

/** TextClock exposing one RemoteViews-safe control for its line-spacing extra. */
@RemoteViews.RemoteView
class AdjustableTextClock @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextClock(context, attrs, defStyleAttr) {

    @android.view.RemotableViewMethod
    fun setLineSpacingExtraDp(value: Int) {
        val px = value.coerceIn(-40, 40) * resources.displayMetrics.density
        setLineSpacing(px, 1f)
    }
}