package de.evylon.boulderstats.models

import org.joda.time.DateTime

data class VisitorData(
    val dayOfWeek: Int,
    val hour: Int,
    val minutes: Int,
    val visitorCount: Int,
    val dateTime: DateTime
)