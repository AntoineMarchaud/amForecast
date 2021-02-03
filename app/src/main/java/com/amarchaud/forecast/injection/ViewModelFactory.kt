package com.amarchaud.forecast.injection

import android.app.Application
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.amarchaud.forecast.ui.weather.WeatherViewModel

@Suppress("UNCHECKED_CAST")
class ViewModelFactory(private val app: Application): ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {

        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            return WeatherViewModel(app) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}