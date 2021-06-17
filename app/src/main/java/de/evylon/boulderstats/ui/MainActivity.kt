package de.evylon.boulderstats.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import de.evylon.boulderstats.R
import de.evylon.boulderstats.business.VisitorDataRepository
import de.evylon.boulderstats.databinding.ActivityMainBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.joda.time.DateTimeConstants.MINUTES_PER_HOUR

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel = MainViewModel()
    private val visitorDataRepository = VisitorDataRepository()
    private lateinit var binding: ActivityMainBinding
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
            val gyms = visitorDataRepository.loadGyms()
            vm.gyms.postValue(gyms)
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
            updateAverages()
        })
        vm.visitorDataAverages.observe(this, {
            updateChart()
        })
        vm.displayedWeekday.observe(this, {
            updateChart()
        })
        vm.gyms.observe(this, {
            updateGymsSpinner()
        })
        vm.numberOfConsideredWeeks.observe(this, {
            updateAverages()
        })
    }

    fun onClickDownload(@Suppress("UNUSED_PARAMETER") view: View) {
        val gym = vm.selectedGym.value ?: return // TODO show ui error
        GlobalScope.launch {
            val visitorData = visitorDataRepository.downloadData(gym)
            vm.visitorData.postValue(visitorData)
            updateAverages()
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

    private fun updateAverages() {
        val numberOfConsideredWeeks = vm.numberOfConsideredWeeks.value ?: return
        val visitorData = vm.visitorData.value ?: return
        val latestDate = visitorData.maxByOrNull { it.dateTime } ?: return
        val oldestAllowedDate = latestDate.dateTime.minusWeeks(numberOfConsideredWeeks)
        val visitorDataAverages = visitorDataRepository.createAverages(visitorData, oldestAllowedDate)
        vm.visitorDataAverages.postValue(visitorDataAverages)
    }

    private fun updateChart() {
        val displayedWeekday = vm.displayedWeekday.value ?: return
        val visitorDataAverages = vm.visitorDataAverages.value ?: return
        val entries = visitorDataAverages
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

}