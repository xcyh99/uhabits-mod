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
package org.isoron.uhabits.activities.settings

import android.os.Bundle
import android.content.Intent
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.databinding.SettingsActivityBinding
import org.isoron.uhabits.utils.applyRootViewInsets
import org.isoron.uhabits.utils.setupToolbar

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val component = (application as HabitsApplication).component
        val themeSwitcher = AndroidThemeSwitcher(this, component.preferences)
        themeSwitcher.apply()

        val binding = SettingsActivityBinding.inflate(LayoutInflater.from(this))
        binding.root.setupToolbar(
            toolbar = binding.toolbar,
            title = resources.getString(R.string.settings),
            color = PaletteColor(11),
            theme = themeSwitcher.currentTheme
        )
        binding.root.applyRootViewInsets()
        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putString("startup_page_last", "settings").apply()
    }

    override fun onPause() {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putString("startup_page_last", "settings").apply()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onPostResume() {
        super.onPostResume()
        val launchIntent = intent
        supportFragmentManager.executePendingTransactions()
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment) as? SettingsFragment

        val widgetParameter = launchIntent.getStringExtra("open_widget_parameter")
        if (widgetParameter != null) {
            launchIntent.removeExtra("open_widget_parameter")
            if (fragment?.isAdded == true && fragment.view != null) {
                fragment.openWidgetParameterSettings(widgetParameter)
            }
            return
        }
        if (launchIntent.getBooleanExtra("manage_parameter_preview", false)) {
            // Consume the request before showing the dialog to prevent duplicate prompts.
            launchIntent.removeExtra("manage_parameter_preview")
            if (fragment?.isAdded == true && fragment.view != null) {
                fragment.openParameterPreviewManager()
            }
            return
        }

        val preferenceKey = launchIntent.getStringExtra("open_preference_key") ?: return
        launchIntent.removeExtra("open_preference_key")
        if (preferenceKey == "widgetManager") {
            // Keep legacy shortcuts working, but route them to the unified component catalog.
            startActivity(Intent(this, ComponentAddActivity::class.java))
            return
        }
        if (fragment?.isAdded == true && fragment.view != null) {
            fragment.openPreferenceByKey(preferenceKey)
        }
    }
}
