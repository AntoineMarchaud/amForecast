package com.amarchaud.forecast.model.openweathermap

data class WeatherDisplayed(
    val logo: String? = null,
    val date: String = "No Date",
    val temperature: String = "0°",
    val windSpeed: String = "0 km/h",
    val details: String? = "No details"
)