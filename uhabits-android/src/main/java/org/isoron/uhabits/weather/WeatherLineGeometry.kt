package org.isoron.uhabits.weather

/**
 * Shared seven-node geometry for the six-segment weather chart.
 * Each day is calculated once; adjacent segments reuse the same node.
 */
internal object WeatherLineGeometry {
    data class Point(val dayIndex: Int, val temperature: Int, val x: Float, val y: Float)
    data class Segment(val index: Int, val start: Point, val end: Point)
    data class Chart(val nodes: List<Point>, val segments: List<Segment>)

    fun calculate(
        temperatures: List<Int>,
        width: Float,
        height: Float,
        topInset: Float = 0f,
        bottomInset: Float = 0f
    ): Chart {
        require(temperatures.size == 7) { "Weather line requires exactly seven daily temperatures" }
        require(width > 0f && height > 0f) { "Chart dimensions must be positive" }
        require(topInset >= 0f && bottomInset >= 0f && topInset + bottomInset < height) {
            "Chart insets must fit inside chart height"
        }

        val minTemperature = temperatures.minOrNull()!!
        val maxTemperature = temperatures.maxOrNull()!!
        val range = (maxTemperature - minTemperature).toFloat()
        val plotHeight = height - topInset - bottomInset

        val nodes = temperatures.mapIndexed { index, temperature ->
            val normalized = if (range == 0f) 0.5f
            else (maxTemperature - temperature).toFloat() / range
            Point(
                dayIndex = index,
                temperature = temperature,
                x = width * (index / 6f),
                y = topInset + normalized * plotHeight
            )
        }
        val segments = (0 until 6).map { index ->
            Segment(index, nodes[index], nodes[index + 1])
        }
        return Chart(nodes, segments)
    }
}
