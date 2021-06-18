package de.evylon.boulderstats.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.evylon.boulderstats.models.ClimbingGym
import de.evylon.boulderstats.models.VisitorData
import de.evylon.boulderstats.models.VisitorDataAverage

class MainViewModel : ViewModel() {
    var displayedWeekday = MutableLiveData<Int>()
    var visitorData = MutableLiveData(listOf<VisitorData>())
    var visitorDataAverages = MutableLiveData(listOf<VisitorDataAverage>())
    var selectedGym = MutableLiveData<ClimbingGym>()
    var gyms = MutableLiveData(listOf<ClimbingGym>())
    var numberOfConsideredWeeks = MutableLiveData(DEFAULT_NUMBER_OF_WEEKS)

    companion object {
        private const val DEFAULT_NUMBER_OF_WEEKS = 10
    }
}
