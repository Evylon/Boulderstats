package de.evylon.boulderstats.ui

import org.joda.time.DateTime

data class ParsedCSVData(
    val dateTime: DateTime,
    val visitorCount: Int
)