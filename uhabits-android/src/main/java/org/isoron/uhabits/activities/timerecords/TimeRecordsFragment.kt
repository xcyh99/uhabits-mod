/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier
 *
 * This file is part of Loop Habit Tracker.
 * Distributed under the GNU General Public License, version 3 or later.
 */
package org.isoron.uhabits.activities.timerecords

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import org.isoron.uhabits.R
import org.isoron.uhabits.widgets.TimeRecordWidgetProvider
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.UUID

class TimeRecordsFragment : PreferenceFragmentCompat() {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        refreshPreferences()
    }

    override fun onResume() {
        super.onResume()
        refreshPreferences()
    }

    private fun refreshPreferences() {
        val context = requireContext()
        val screen = preferenceManager.createPreferenceScreen(context)

        val intro = Preference(context).apply {
            title = getString(R.string.time_records_intro_title)
            summary = getString(R.string.time_records_intro_summary)
            isSelectable = false
            isIconSpaceReserved = false
        }
        screen.addPreference(intro)

        val addCategory = PreferenceCategory(context).apply {
            title = getString(R.string.time_records_add_category)
        }
        screen.addPreference(addCategory)
        addCategory.addPreference(createAction(
            getString(R.string.time_records_checkin),
            getString(R.string.time_records_checkin_summary)
        ) { pinCheckinWidget() })
        addCategory.addPreference(createAction(
            getString(R.string.time_records_elapsed),
            getString(R.string.time_records_elapsed_summary)
        ) { showEditor(TimeRecord.ELAPSED) })
        addCategory.addPreference(createAction(
            getString(R.string.time_records_countdown),
            getString(R.string.time_records_countdown_summary)
        ) { showEditor(TimeRecord.COUNTDOWN) })

        val recordsCategory = PreferenceCategory(context).apply {
            title = getString(R.string.time_records_saved_category)
        }
        screen.addPreference(recordsCategory)
        val records = TimeRecordStore.getAll(context)
        if (records.isEmpty()) {
            recordsCategory.addPreference(Preference(context).apply {
                title = getString(R.string.time_records_empty_title)
                summary = getString(R.string.time_records_empty_summary)
                isSelectable = false
                isIconSpaceReserved = false
            })
        } else {
            records.forEach { record ->
                val startOrTarget = LocalDate.parse(record.date)
                val today = LocalDate.now()
                val days = if (record.type == TimeRecord.ELAPSED) {
                    java.time.temporal.ChronoUnit.DAYS.between(startOrTarget, today)
                } else {
                    java.time.temporal.ChronoUnit.DAYS.between(today, startOrTarget)
                }
                val status = if (record.type == TimeRecord.ELAPSED) {
                    getString(R.string.time_records_elapsed_days, days)
                } else if (days >= 0) {
                    getString(R.string.time_records_remaining_days, days)
                } else {
                    getString(R.string.time_records_overdue_days, -days)
                }
                recordsCategory.addPreference(Preference(context).apply {
                    title = record.title
                    summary = "${startOrTarget.format(dateFormatter)}  ·  $status"
                    isIconSpaceReserved = false
                    setOnPreferenceClickListener {
                        showEditor(record.type, record)
                        true
                    }
                })
            }
        }

        val widgetCategory = PreferenceCategory(context).apply {
            title = getString(R.string.time_records_widget_category)
        }
        screen.addPreference(widgetCategory)
        widgetCategory.addPreference(Preference(context).apply {
            title = getString(R.string.time_records_widget_title)
            summary = getString(R.string.time_records_widget_summary)
            isSelectable = false
            isIconSpaceReserved = false
        })
        preferenceScreen = screen
    }

    private fun pinCheckinWidget() {
        val manager = AppWidgetManager.getInstance(requireContext())
        val provider = ComponentName(
            requireContext(),
            org.isoron.uhabits.widgets.DaysSinceLastCheckinWidgetProvider::class.java
        )
        val pinned = manager.requestPinAppWidget(provider, null, null)
        if (!pinned) {
            Toast.makeText(requireContext(), R.string.time_records_pin_not_supported, Toast.LENGTH_LONG).show()
        }
    }

    private fun createAction(title: String, summary: String, action: () -> Unit) =
        Preference(requireContext()).apply {
            this.title = title
            this.summary = summary
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                action()
                true
            }
        }

    private fun showEditor(type: String, existing: TimeRecord? = null) {
        val context = requireContext()
        var selectedDate = existing?.date?.let(LocalDate::parse)
            ?: if (type == TimeRecord.ELAPSED) LocalDate.now() else LocalDate.now().plusDays(30)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val titleInput = EditText(context).apply {
            hint = getString(R.string.time_records_title_hint)
            setSingleLine(true)
            setText(existing?.title.orEmpty())
        }
        val dateButton = TextView(context).apply {
            textSize = 16f
            setPadding(0, dp(18), 0, dp(18))
        }
        fun updateDateLabel() {
            dateButton.text = getString(
                if (type == TimeRecord.ELAPSED) R.string.time_records_start_date else R.string.time_records_target_date,
                selectedDate.format(dateFormatter)
            )
        }
        updateDateLabel()
        dateButton.setOnClickListener {
            val initial = selectedDate
            val picker = DatePickerDialog(
                context,
                { _, year, month, day ->
                    selectedDate = LocalDate.of(year, month + 1, day)
                    updateDateLabel()
                },
                initial.year,
                initial.monthValue - 1,
                initial.dayOfMonth
            )
            val boundary = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (type == TimeRecord.COUNTDOWN) add(Calendar.DAY_OF_MONTH, 1)
            }
            if (type == TimeRecord.ELAPSED) {
                picker.datePicker.maxDate = boundary.timeInMillis + 24L * 60L * 60L * 1000L - 1L
            } else {
                picker.datePicker.minDate = boundary.timeInMillis
            }
            picker.show()
        }
        container.addView(titleInput)
        container.addView(dateButton)

        val dialog = AlertDialog.Builder(context)
            .setTitle(
                existing?.let { getString(R.string.time_records_edit_title) }
                    ?: getString(if (type == TimeRecord.ELAPSED) R.string.time_records_add_elapsed else R.string.time_records_add_countdown)
            )
            .setView(container)
            .setNegativeButton(R.string.clear_label, null)
            .setPositiveButton(R.string.save, null)
            .apply {
                if (existing != null) {
                    setNeutralButton(R.string.delete, null)
                }
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text.toString().trim()
                if (title.isBlank()) {
                    titleInput.error = getString(R.string.time_records_title_required)
                    return@setOnClickListener
                }
                val today = LocalDate.now()
                if (type == TimeRecord.ELAPSED && selectedDate.isAfter(today)) {
                    Toast.makeText(context, R.string.time_records_start_must_be_past, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (type == TimeRecord.COUNTDOWN && !selectedDate.isAfter(today)) {
                    Toast.makeText(context, R.string.time_records_target_must_be_future, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                TimeRecordStore.save(
                    context,
                    TimeRecord(existing?.id ?: UUID.randomUUID().toString(), title, type, selectedDate.toString())
                )
                TimeRecordWidgetProvider.updateAll(context)
                dialog.dismiss()
                refreshPreferences()
            }
            if (existing != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle(R.string.time_records_delete_title)
                        .setMessage(getString(R.string.time_records_delete_message, existing.title))
                        .setNegativeButton(R.string.clear_label, null)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            TimeRecordStore.delete(context, existing.id)
                            TimeRecordWidgetProvider.updateAll(context)
                            refreshPreferences()
                        }
                        .show()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
