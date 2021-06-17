package de.evylon.boulderstats.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.evylon.boulderstats.models.ClimbingGym
import de.evylon.boulderstats.models.VisitorData

class MainViewModel : ViewModel() {
    var filename = MutableLiveData("No File Downloaded yet")
    var displayedWeekday = MutableLiveData<Int>()
    var visitorData = MutableLiveData(listOf<VisitorData>())
    var selectedGym = MutableLiveData<ClimbingGym>()
    var gyms = MutableLiveData(listOf<ClimbingGym>())
}

