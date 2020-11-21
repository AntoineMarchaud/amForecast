package com.shadow.forecast.model.openweathermap.fusion

import com.shadow.forecast.model.openweathermap.current.Current
import com.shadow.forecast.model.openweathermap.oneCall.OneCall


data class Fusion(
    var Current : Current? = null,
    var OneCall : OneCall? = null
)