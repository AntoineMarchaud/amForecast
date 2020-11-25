package com.amarchaud.forecast.injection

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.amarchaud.forecast.ui.weather.WeatherViewModel

@Suppress("UNCHECKED_CAST")
class ViewModelFactory(private val activity: AppCompatActivity): ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {

        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            return WeatherViewModel(activity) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}