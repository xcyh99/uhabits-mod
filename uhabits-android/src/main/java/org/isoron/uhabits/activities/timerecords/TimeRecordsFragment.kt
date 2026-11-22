/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier
 *
 * This file is part of Loop Habit Tracker.
 * Distributed under the GNU General Public License, version 3 or later.
 */
package org.isoron.uhabits.activities.timerecords

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import org.isoron.uhabits.R

class TimeRecordsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val screen = preferenceManager.createPreferenceScreen(requireContext())

        val intro = Preference(requireContext()).apply {
            title = getString(R.string.time_records_intro_title)
            summary = getString(R.string.time_records_intro_summary)
            isSelectable = false
            isIconSpaceReserved = false
        }
        screen.addPreference(intro)

        val category = PreferenceCategory(requireContext()).apply {
            title = getString(R.string.time_records_category)
        }
        screen.addPreference(category)

        category.addPreference(createMode(
            R.string.time_records_checkin,
            R.string.time_records_checkin_summary
        ))
        category.addPreference(createMode(
            R.string.time_records_elapsed,
            R.string.time_records_elapsed_summary
        ))
        category.addPreference(createMode(
            R.string.time_records_countdown,
            R.string.time_records_countdown_summary
        ))

        preferenceScreen = screen
    }

    private fun createMode(titleRes: Int, summaryRes: Int) =
        Preference(requireContext()).apply {
            title = getString(titleRes)
            summary = getString(summaryRes)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                Toast.makeText(
                    requireContext(),
                    R.string.time_records_coming_soon,
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
        }
}
