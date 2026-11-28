package org.isoron.uhabits.weather

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Rasterizes one seven-node temperature curve into a single line of Braille
 * characters. Each character contains a 2x4 dot cell, so the whole curve is
 * one connected raster path rather than seven independent text rows.
 */
internal object WeatherLineTextRenderer {
    const val COLUMN_COUNT = 56
    const val FIRST_NODE_COLUMN = 4
    const val LAST_NODE_COLUMN = 52
    private const val DOT_COLUMNS = COLUMN_COUNT * 2
    private const val DOT_ROWS = 4
    private const val CELLS_PER_INTERVAL = 8
    private const val DOTS_PER_CELL = 2
    private const val BRAILLE_BASE = 0x2800

    fun render(chart: WeatherLineGeometry.Chart): String {
        require(chart.nodes.size == 7) { "Weather line requires seven nodes" }

        val pixels = Array(DOT_ROWS) { BooleanArray(DOT_COLUMNS) }
        val minY = chart.nodes.minOf { it.y }
        val maxY = chart.nodes.maxOf { it.y }

        for (interval in 0 until 6) {
            val start = chart.nodes[interval]
            val end = chart.nodes[interval + 1]
            val x0 = FIRST_NODE_COLUMN * DOTS_PER_CELL +
                interval * CELLS_PER_INTERVAL * DOTS_PER_CELL
            val x1 = x0 + CELLS_PER_INTERVAL * DOTS_PER_CELL
            val y0 = levelFor(start.y, minY, maxY)
            val y1 = levelFor(end.y, minY, maxY)
            drawLine(pixels, x0, y0, x1, y1)
        }

        return buildString(COLUMN_COUNT) {
            for (column in 0 until COLUMN_COUNT) {
                var mask = 0
                for (row in 0 until DOT_ROWS) {
                    for (side in 0..1) {
                        if (pixels[row][column * DOTS_PER_CELL + side]) {
                            mask = mask or brailleBit(row, side)
                        }
                    }
                }
                append((BRAILLE_BASE + mask).toChar())
            }
        }
    }

    private fun levelFor(y: Float, minY: Float, maxY: Float): Int {
        val range = maxY - minY
        if (range == 0f) return (DOT_ROWS - 1) / 2
        return (((y - minY) / range) * (DOT_ROWS - 1))
            .roundToInt().coerceIn(0, DOT_ROWS - 1)
    }

    private fun drawLine(
        pixels: Array<BooleanArray>,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int
    ) {
        var x = startX
        var y = startY
        val dx = abs(endX - startX)
        val dy = abs(endY - startY)
        val stepX = if (startX < endX) 1 else -1
        val stepY = if (startY < endY) 1 else -1
        var error = dx - dy

        while (true) {
            pixels[y.coerceIn(0, DOT_ROWS - 1)][x.coerceIn(0, DOT_COLUMNS - 1)] = true
            if (x == endX && y == endY) break
            val doubledError = error * 2
            if (doubledError > -dy) {
                error -= dy
                x += stepX
            }
            if (doubledError < dx) {
                error += dx
                y += stepY
            }
        }
    }

    private fun brailleBit(row: Int, side: Int): Int = when (row) {
        0 -> if (side == 0) 1 shl 0 else 1 shl 3
        1 -> if (side == 0) 1 shl 1 else 1 shl 4
        2 -> if (side == 0) 1 shl 2 else 1 shl 5
        else -> if (side == 0) 1 shl 6 else 1 shl 7
    }
}
