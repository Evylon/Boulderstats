package de.evylon.boulderstats.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import de.evylon.boulderstats.R
import de.evylon.boulderstats.databinding.ActivityMainBinding
import de.evylon.boulderstats.models.CSVFields
import de.evylon.boulderstats.models.ClimbingGym
import de.evylon.boulderstats.models.ParsedCSVData
import de.evylon.boulderstats.models.VisitorData
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants.MINUTES_PER_HOUR
import java.io.InputStream
import java.lang.Exception
import java.net.URL

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel = MainViewModel()
    private lateinit var binding: ActivityMainBinding
    private val baseURL = "https://evylon.de"
    private lateinit var lineChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initLayout()
        observerViewModel()
        lineChart = findViewById(R.id.chart_boulderstats)

        val arrayAdapter = ArrayAdapter(
            this, R.layout.support_simple_spinner_dropdown_item,
            listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        )
        arrayAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item)
        val spinner = findViewById<Spinner>(R.id.spinner_weekdays)
        spinner.adapter = arrayAdapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p0: AdapterView<*>?) {
                vm.displayedWeekday.postValue(null)
            }

            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                vm.displayedWeekday.postValue(p2)
            }
        }

        GlobalScope.launch {
            loadGyms()
        }
    }

    private fun initLayout() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.vm = vm
        binding.lifecycleOwner = this
        setContentView(binding.root)
    }

    private fun observerViewModel() {
        vm.visitorData.observe(this, {
            updateChart()
        })
        vm.displayedWeekday.observe(this, {
            updateChart()
        })
        vm.gyms.observe(this, {
            updateGymsSpinner()
        })
    }

    fun onClickDownload(@Suppress("UNUSED_PARAMETER") view: View) {
        GlobalScope.launch {
            download()
        }
    }

    private fun updateGymsSpinner() {
        val gyms = vm.gyms.value ?: return // TODO show error
        val arrayAdapter =
            ArrayAdapter(this, R.layout.support_simple_spinner_dropdown_item, gyms.map { it.name })
        arrayAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item)
        val spinner = findViewById<Spinner>(R.id.spinner_gyms)
        spinner.adapter = arrayAdapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p0: AdapterView<*>?) {
                vm.selectedGym.postValue(null)
            }

            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                vm.selectedGym.postValue(gyms[p2])
            }
        }
    }

    private fun loadGyms() {
        val url = "${baseURL}/gyms.txt"
        vm.gyms.postValue(URL(url).readText().lines()
            .filter { line -> line.isNotEmpty() && line.filter { it == ',' }.count() == 1 }
            .map { line ->
                val data = line.split(',')
                ClimbingGym(data[0], data[1].toInt())
            })
    }

    private fun download() {
        val gym = vm.selectedGym.value ?: return // TODO show ui error
        val filename = "${gym.name}-counter.csv"
        val link = "${baseURL}/${filename}"
        URL(link).openStream().use { input ->
            // TODO Caching
//            FileOutputStream(File(cacheDir, filename)).use { output ->
//                input.copyTo(output)
//            }
            vm.filename.postValue(filename)

            val visitorData = createUniformData(parseCSV(input))
            val visitorDataAverages = createAverages(visitorData)
            vm.visitorData.postValue(visitorDataAverages)
        }
    }

    private fun updateChart() {
        val displayedWeekday = vm.displayedWeekday.value ?: return
        val visitorData = vm.visitorData.value ?: return
        val entries = visitorData
            .filter { it.dayOfWeek == displayedWeekday + 1 }
            .map {
                Entry(
                    it.hour.toFloat() * MINUTES_PER_HOUR + it.minutes,
                    it.visitorCount.toFloat()
                )
            }
        val lineData = LineData(LineDataSet(entries, "Boulderstats"))
        lineChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val minuteOfDay = value.toInt()
                return "${minuteOfDay / MINUTES_PER_HOUR}:${minuteOfDay % MINUTES_PER_HOUR}"
            }
        }
        val maxVisitors = vm.selectedGym.value?.maxVisitors?.toFloat()
        maxVisitors?.let { lineChart.axisLeft.axisMaximum = it}
        lineChart.data = lineData
        lineChart.invalidate()
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

    private val minMinuteOfDay = 9 * MINUTES_PER_HOUR
    private val maxMinuteOfDay = 23 * MINUTES_PER_HOUR
    private val minuteInterval = 5
    private val defaultVisitorCountAtBeginningOfDay = 0

    private fun createUniformData(inputVisitorData: List<ParsedCSVData>): List<VisitorData> {
        val sortedVisitorData = inputVisitorData.sortedBy { it.dateTime }
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
                            minuteOfDay / MINUTES_PER_HOUR,
                            minuteOfDay % MINUTES_PER_HOUR,
                            defaultVisitorCountAtBeginningOfDay
                        )
                    )
                } else {
                    uniformVisitorData.add(
                        VisitorData(
                            previousVisitorData.dateTime.dayOfWeek,
                            minuteOfDay / MINUTES_PER_HOUR,
                            minuteOfDay % MINUTES_PER_HOUR,
                            previousVisitorData.visitorCount
                        )
                    )
                }
            }
        }

        return uniformVisitorData
    }

    private fun createAverages(visitorData: List<VisitorData>): List<VisitorData> =
        visitorData.groupBy { it.dayOfWeek }.flatMap { dataPerWeekday ->
            dataPerWeekday.value.groupBy { it.hour }.flatMap { dataPerHour ->
                dataPerHour.value.groupBy { it.minutes }.map { dataPerMinute ->
                    val average =
                        dataPerMinute.value.sumOf { it.visitorCount } / dataPerMinute.value.size
                    VisitorData(dataPerWeekday.key, dataPerHour.key, dataPerMinute.key, average)
                }
            }
        }
}