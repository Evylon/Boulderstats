package de.evylon.boulderstats.models

data class VisitorData(
    val dayOfWeek: Int,
    val hour: Int,
    val minutes: Int,
    val visitorCount: Int
)