package org.isoron.uhabits.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherLineGeometryTest {
    @Test
    fun createsSevenNodesAndSixSegmentsSharingTheirBoundaryNodes() {
        val chart = WeatherLineGeometry.calculate(
            listOf(12, 16, 16, 20, 18, 22, 19), 600f, 100f
        )
        assertEquals(7, chart.nodes.size)
        assertEquals(6, chart.segments.size)
        for (i in 0 until 5) assertSame(chart.nodes[i + 1], chart.segments[i].end)
        for (i in 1 until 6) assertSame(chart.nodes[i], chart.segments[i].start)
    }

    @Test
    fun equalTemperaturesHaveExactlyTheSameHeight() {
        val chart = WeatherLineGeometry.calculate(
            listOf(12, 16, 16, 20, 18, 22, 19), 600f, 100f
        )
        assertEquals(chart.nodes[1].y, chart.nodes[2].y, 0.0001f)
    }

    @Test
    fun highestAndLowestTemperaturesMapToPlotEdges() {
        val chart = WeatherLineGeometry.calculate(
            listOf(12, 16, 16, 20, 18, 22, 19), 600f, 100f, 5f, 7f
        )
        assertEquals(5f, chart.nodes[5].y, 0.0001f)
        assertEquals(93f, chart.nodes[0].y, 0.0001f)
    }

    @Test
    fun flatForecastUsesCenterWithoutDivisionByZero() {
        val chart = WeatherLineGeometry.calculate(List(7) { 16 }, 600f, 100f)
        assertTrue(chart.nodes.all { it.y == 50f })
        assertTrue(chart.segments.all { it.start.y == it.end.y })
    }
}
