package de.evylon.boulderstats.business

import android.annotation.SuppressLint
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import de.evylon.boulderstats.models.*
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import java.io.InputStream
import java.lang.Exception
import java.net.URL

class VisitorDataRepository {
    private var filename = "No File Downloaded yet"

    fun loadGyms(): List<ClimbingGym> {
        val url = "${BASE_URL}/gyms.txt"
        return URL(url).readText().lines()
            .filter { line -> line.isNotEmpty() && line.filter { it == ',' }.count() == 1 }
            .map { line ->
                val data = line.split(',')
                ClimbingGym(data[0], data[1].toInt())
            }
    }

    fun downloadData(gym: ClimbingGym): List<VisitorData> {
        filename = "${gym.name}-counter.csv"
        val link = "${BASE_URL}/${filename}"
        URL(link).openStream().use { input ->
            // TODO Caching
//            FileOutputStream(File(cacheDir, filename)).use { output ->
//                input.copyTo(output)
//            }

            return createUniformData(parseCSV(input))
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun parseCSV(inputStream: InputStream): MutableList<ParsedCSVData> =
        csvReader().readAllWithHeader(inputStream).mapNotNull { row ->
            try {
                val time = DateTime(row[CSVFields.TIME.colName])
                val currentVisitorCount = row.getValue(CSVFields.VISITORS.colName).toInt()
                ParsedCSVData(time, currentVisitorCount)
            } catch (ex: Exception) {
                null
            }
        }.toMutableList()

    private val minMinuteOfDay = 9 * DateTimeConstants.MINUTES_PER_HOUR
    private val maxMinuteOfDay = 23 * DateTimeConstants.MINUTES_PER_HOUR
    private val minuteInterval = 5
    private val defaultVisitorCountAtBeginningOfDay = 0

    private fun createUniformData(inputVisitorData: List<ParsedCSVData>): List<VisitorData> {
        val sortedVisitorData = inputVisitorData.sortedBy { it.dateTime.dayOfYear }
        val uniformVisitorData = mutableListOf<VisitorData>()
        val visitorDataIterator = sortedVisitorData.iterator()
        var previousVisitorData: ParsedCSVData? = null
        var currentVisitorData = visitorDataIterator.next()

        // iterate over days from first to last date
        for (dayOfYear in sortedVisitorData.first().dateTime.dayOfYear..sortedVisitorData.last().dateTime.dayOfYear) {
            // iterate over minutes in a day within specified time frame and steps
            for (minuteOfDay in minMinuteOfDay..maxMinuteOfDay step minuteInterval) {
                // advance to next data point if we passed the previous one
                while (currentVisitorData.dateTime.dayOfYear < dayOfYear
                    || (currentVisitorData.dateTime.dayOfYear == dayOfYear && currentVisitorData.dateTime.minuteOfDay <= minuteOfDay)
                ) {
                    val doY = currentVisitorData.dateTime.dayOfYear
                    val moD = currentVisitorData.dateTime.minuteOfDay
                    // reached end of iterator, fill up the data until end of current day
                    if (!visitorDataIterator.hasNext()) break
                    previousVisitorData = currentVisitorData
                    currentVisitorData = visitorDataIterator.next()
                }
                // first element in list or in a day
                if (previousVisitorData == null || previousVisitorData.dateTime.dayOfYear < dayOfYear) {
                    uniformVisitorData.add(
                        VisitorData(
                            currentVisitorData.dateTime.dayOfWeek,
                            minuteOfDay / DateTimeConstants.MINUTES_PER_HOUR,
                            minuteOfDay % DateTimeConstants.MINUTES_PER_HOUR,
                            defaultVisitorCountAtBeginningOfDay,
                            currentVisitorData.dateTime
                        )
                    )
                } else {
                    uniformVisitorData.add(
                        VisitorData(
                            previousVisitorData.dateTime.dayOfWeek,
                            minuteOfDay / DateTimeConstants.MINUTES_PER_HOUR,
                            minuteOfDay % DateTimeConstants.MINUTES_PER_HOUR,
                            previousVisitorData.visitorCount,
                            previousVisitorData.dateTime
                        )
                    )
                }
            }
        }

        return uniformVisitorData
    }

    fun createAverages(visitorData: List<VisitorData>,
                       oldestAllowedDate: DateTime): List<VisitorDataAverage> =
        visitorData
            .filter { it.dateTime.isAfter(oldestAllowedDate) }
            .groupBy { it.dayOfWeek }.flatMap { dataPerWeekday ->
            dataPerWeekday.value.groupBy { it.hour }.flatMap { dataPerHour ->
                dataPerHour.value.groupBy { it.minutes }.map { dataPerMinute ->
                    val average =
                        dataPerMinute.value.sumOf { it.visitorCount } / dataPerMinute.value.size
                    VisitorDataAverage(dataPerWeekday.key, dataPerHour.key, dataPerMinute.key, average)
                }
            }
        }

    companion object {
        const val BASE_URL = "https://evylon.de"
    }
}