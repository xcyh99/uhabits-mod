package org.isoron.uhabits.widgets

import android.content.Context

class DaysSinceLastCheckinWidgetProvider : BaseWidgetProvider() {
    override fun getWidgetFromId(context: Context, id: Int): BaseWidget {
        val habits = getHabitsFromWidgetId(id)
        return DaysSinceLastCheckinWidget(context, id, habits.first())
    }
}
