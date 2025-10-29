package com.grloepr.pushtrack.exercise

fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else sumOf { it } / size
