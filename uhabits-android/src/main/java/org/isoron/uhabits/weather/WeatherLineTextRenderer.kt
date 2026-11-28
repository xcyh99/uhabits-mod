package org.isoron.uhabits.weather

import kotlin.math.roundToInt

/**
 * Rasterizes seven shared temperature nodes into one continuous monospace text
 * grid. Eight character cells represent each day interval, and node X positions
 * coincide with the centers of the seven forecast-day columns.
 */
internal object WeatherLineTextRenderer {
    const val ROW_COUNT = 7
    const val COLUMN_COUNT = 56
    const val CELLS_PER_INTERVAL = 8
    const val FIRST_NODE_COLUMN = 4
    const val LAST_NODE_COLUMN = 52

    fun render(chart: WeatherLineGeometry.Chart): String {
        require(chart.nodes.size == 7) { "Weather line requires seven nodes" }
        val grid = Array(ROW_COUNT) { CharArray(COLUMN_COUNT) { ' ' } }
        for (interval in 0 until 6) {
            val startLevel = levelFor(chart.nodes[interval].y)
            val endLevel = levelFor(chart.nodes[interval + 1].y)
            for (step in 0 until CELLS_PER_INTERVAL) {
                val leftValue = startLevel + (endLevel - startLevel) * (step / CELLS_PER_INTERVAL.toFloat())
                val rightValue = startLevel + (endLevel - startLevel) * ((step + 1) / CELLS_PER_INTERVAL.toFloat())
                val leftRow = leftValue.roundToInt().coerceIn(0, ROW_COUNT - 1)
                val rightRow = rightValue.roundToInt().coerceIn(0, ROW_COUNT - 1)
                val glyphRow: Int
                val glyph: Char
                when {
                    leftRow == rightRow -> {
                        glyphRow = leftRow
                        glyph = '─'
                    }
                    rightRow < leftRow -> {
                        glyphRow = rightRow
                        glyph = '╱'
                    }
                    else -> {
                        glyphRow = leftRow
                        glyph = '╲'
                    }
                }
                val column = FIRST_NODE_COLUMN + interval * CELLS_PER_INTERVAL + step
                grid[glyphRow][column] = glyph
            }
        }
        return grid.joinToString("\n") { String(it) }
    }

    private fun levelFor(y: Float): Int =
        ((y / 27f) * (ROW_COUNT - 1)).roundToInt().coerceIn(0, ROW_COUNT - 1)
}
