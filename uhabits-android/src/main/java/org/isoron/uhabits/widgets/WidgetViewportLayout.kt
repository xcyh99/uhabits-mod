package org.isoron.uhabits.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.RemotableViewMethod
import android.widget.FrameLayout
import android.widget.RemoteViews

/** Fixed-height clipping window for oversized native TextClock children. */
@RemoteViews.RemoteView
class WidgetViewportLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    init {
        clipChildren = true
        clipToPadding = true
    }

    @RemotableViewMethod
    fun setViewportHeightPx(heightPx: Int) {
        val params = layoutParams ?: return
        params.height = heightPx.coerceAtLeast(0)
        layoutParams = params
        clipChildren = true
        clipToPadding = true
        requestLayout()
    }
}
