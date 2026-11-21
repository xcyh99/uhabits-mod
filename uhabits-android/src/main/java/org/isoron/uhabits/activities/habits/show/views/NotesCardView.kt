/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.isoron.uhabits.activities.habits.show.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import org.isoron.uhabits.R
import org.isoron.uhabits.core.ui.screens.habits.show.views.NotesCardState
import org.isoron.uhabits.core.ui.screens.habits.show.views.HistoryNote
import org.isoron.uhabits.databinding.ShowHabitNotesBinding

class NotesCardView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    private var onDescriptionClickListener: (() -> Unit)? = null
    private var onHistoryNoteClickListener: ((HistoryNote) -> Unit)? = null

    fun setEditListeners(
        onDescriptionClick: () -> Unit,
        onHistoryNoteClick: (HistoryNote) -> Unit
    ) {
        onDescriptionClickListener = onDescriptionClick
        onHistoryNoteClickListener = onHistoryNoteClick
        binding.habitNotes.setOnClickListener { onDescriptionClickListener?.invoke() }
        binding.habitNotes.isClickable = true
        binding.habitNotes.isFocusable = true
    }

    private val binding = ShowHabitNotesBinding.inflate(LayoutInflater.from(context), this)
    fun setState(state: NotesCardState) {
        val hasDescription = state.description.isNotBlank()
        val hasHistoryNotes = state.historyNotes.isNotEmpty()
        visibility = if (hasDescription || hasHistoryNotes) VISIBLE else GONE

        binding.descriptionTitle.visibility = if (hasDescription) VISIBLE else GONE
        binding.descriptionTitle.setTextColor(state.habitColor)
        binding.habitNotes.visibility = if (hasDescription) VISIBLE else GONE
        binding.habitNotes.text = state.description
        binding.historyNotesTitle.visibility =
            if (hasHistoryNotes) VISIBLE else GONE
        binding.historyNotesTitle.text = context.getString(R.string.history_notes)
        binding.historyNotesTitle.setTextColor(state.habitColor)
        binding.historyNotesList.removeAllViews()
        binding.historyNotesScroll.visibility =
            if (hasHistoryNotes) VISIBLE else GONE
        binding.historyNotesScroll.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                    view.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        state.historyNotes.forEach { note ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(0, dp(1), 0, dp(1))
                setOnClickListener {
                    onHistoryNoteClickListener?.invoke(note)
                }
            }
            val dateView = TextView(context).apply {
                text = formatHistoryDate(note.date)
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(binding.habitNotes.currentTextColor)
                maxLines = 1
            }
            val noteView = TextView(context).apply {
                text = note.text
                textSize = 12f
                setTextColor(binding.habitNotes.currentTextColor)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.START
                setPadding(dp(6), 0, 0, 0)
            }
            row.addView(dateView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            row.addView(noteView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            binding.historyNotesList.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, dp(18)))
        }
        // Grow with the number of rows, but stop at 112dp and scroll beyond that.
        binding.historyNotesScroll.layoutParams = binding.historyNotesScroll.layoutParams.apply {
            height = minOf(state.historyNotes.size * dp(18), dp(112))
        }
        requestLayout()
        invalidate()
    }

    private fun formatHistoryDate(date: String): String =
        date.removePrefix("LocalDate(").removeSuffix(")")

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
