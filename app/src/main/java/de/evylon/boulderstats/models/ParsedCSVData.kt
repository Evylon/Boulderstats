package de.evylon.boulderstats.models

import org.joda.time.DateTime

data class ParsedCSVData(
    val dateTime: DateTime,
    val visitorCount: Int
)