package com.amarchaud.forecast.model.openweathermap.fusion

import com.amarchaud.forecast.model.openweathermap.current.Current
import com.amarchaud.forecast.model.openweathermap.oneCall.OneCall


data class Fusion(
    var Current : Current? = null,
    var OneCall : OneCall? = null
)