package com.grloepr.pushtrack.exercise

class MeasurementSmoother(private val windowSize: Int) {
    private val samples = ArrayDeque<Double>()

    fun add(value: Double?): Double? {
        if (value == null || value.isNaN()) return current()
        if (samples.size == windowSize) samples.removeFirst()
        samples.addLast(value)
        return current()
    }

    fun current(): Double? = if (samples.isEmpty()) null else samples.sumOf { it } / samples.size

    fun clear() = samples.clear()
}
