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
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.isoron.uhabits.activities.common.dialogs

import android.app.Dialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.ui.callbacks.OnColorPickedCallback

/** Color picker with a continuous HSV field and a HEX input. */
class ColorPickerDialog : DialogFragment() {
    private var initialColor: Int = Color.RED
    private var callback: OnColorPickedCallback? = null

    fun setInitialColor(color: Int) {
        initialColor = color
    }

    fun setListener(callback: OnColorPickedCallback) {
        this.callback = callback
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        var selectedColor = initialColor
        lateinit var hex: EditText
        lateinit var preview: View

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(8))
        }
        val field = SaturationValueView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(190)
            )
            setHue(hsv[0])
            setSelection(hsv[1], hsv[2])
            onColorChanged = { saturation, value ->
                hsv[1] = saturation
                hsv[2] = value
                selectedColor = Color.HSVToColor(hsv)
                hex.setText(String.format("#%06X", 0xFFFFFF and selectedColor))
                preview.setBackgroundColor(selectedColor)
            }
        }
        val hue = SeekBar(requireContext()).apply {
            max = 360
            progress = hsv[0].toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    hsv[0] = progress.toFloat()
                    field.setHue(hsv[0])
                    selectedColor = Color.HSVToColor(hsv)
                    hex.setText(String.format("#%06X", 0xFFFFFF and selectedColor))
                    preview.setBackgroundColor(selectedColor)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        hex = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
            setText(String.format("#%06X", 0xFFFFFF and initialColor))
            hint = "#RRGGBB"
        }
        preview = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(24)
            ).apply { topMargin = dp(8) }
            setBackgroundColor(initialColor)
        }
        hex.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                PaletteColor.fromHex(hex.text.toString())?.let {
                    selectedColor = it.argb
                    Color.colorToHSV(selectedColor, hsv)
                    field.setHue(hsv[0])
                    field.setSelection(hsv[1], hsv[2])
                    hue.progress = hsv[0].toInt()
                    preview.setBackgroundColor(selectedColor)
                }
            }
        }
        root.addView(field)
        root.addView(hue)
        root.addView(hex)
        root.addView(preview)

        return AlertDialog.Builder(requireContext())
            .setTitle("Choose habit color")
            .setView(root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                PaletteColor.fromHex(hex.text.toString())?.let { selectedColor = it.argb }
                callback?.onColorPicked(PaletteColor.fromArgb(selectedColor))
            }
            .create()
    }
}

private class SaturationValueView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var hue = 0f
    private var saturation = 1f
    private var value = 1f
    var onColorChanged: ((Float, Float) -> Unit)? = null

    fun setHue(value: Float) {
        hue = value
        invalidate()
    }

    fun setSelection(saturation: Float, value: Float) {
        this.saturation = saturation
        this.value = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        paint.shader = null
        paint.color = color
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        val x = saturation * width
        val y = (1f - value) * height
        canvas.drawCircle(x, y, 8f, paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE ||
            event.action == MotionEvent.ACTION_UP
        ) {
            saturation = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
            value = (1f - event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
            invalidate()
            onColorChanged?.invoke(saturation, value)
            return true
        }
        return true
    }
}
