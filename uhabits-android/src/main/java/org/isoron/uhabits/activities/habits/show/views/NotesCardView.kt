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
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import android.widget.TextView
import org.isoron.uhabits.R
import org.isoron.uhabits.core.ui.screens.habits.show.views.NotesCardState
import org.isoron.uhabits.databinding.ShowHabitNotesBinding

class NotesCardView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    private val binding = ShowHabitNotesBinding.inflate(LayoutInflater.from(context), this)
    fun setState(state: NotesCardState) {
        val hasDescription = state.description.isNotBlank()
        val hasHistoryNotes = state.historyNotes.isNotEmpty()
        visibility = if (hasDescription || hasHistoryNotes) VISIBLE else GONE

        binding.habitNotes.visibility = if (hasDescription) VISIBLE else GONE
        binding.habitNotes.text = state.description
        binding.historyNotesTitle.visibility =
            if (hasHistoryNotes) VISIBLE else GONE
        binding.historyNotesTitle.text = context.getString(R.string.history_notes)
        binding.historyNotesTitle.setTextColor(state.habitColor)
        binding.historyNotesList.removeAllViews()
        binding.historyNotesList.visibility =
            if (hasHistoryNotes) VISIBLE else GONE

        state.historyNotes.forEach { note ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(0, dp(1), 0, dp(1))
                setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle(note.date)
                        .setMessage(note.text)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
            val dateView = TextView(context).apply {
                text = note.date
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
            binding.historyNotesList.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        invalidate()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
