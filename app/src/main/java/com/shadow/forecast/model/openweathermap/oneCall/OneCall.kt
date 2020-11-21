package com.shadow.forecast.model.openweathermap.oneCall

import com.shadow.forecast.model.openweathermap.current.Weather

// CURRENT

data class Weather(
    var id: Int = 0,
    var main: String? = null,
    var description: String? = null,
    var icon: String? = null
)

data class CurrentDay(
    var dt: Int = 0,
    var sunrise: Int = 0,
    var sunset: Int = 0,
    var temp: Double = 0.0,
    var feels_like: Double = 0.0,
    var pressure: Int = 0,
    var humidity: Int = 0,
    var dew_point: Double = 0.0,
    var uvi: Double = 0.0,
    var clouds: Int = 0,
    var visibility: Int = 0,
    var wind_speed: Double = 0.0,
    var wind_deg: Int = 0,
    var weather: List<Weather>? = null
)


// NEXT

data class Temp(
    var day: Double = 0.0,
    var min: Double = 0.0,
    var max: Double = 0.0,
    var night: Double = 0.0,
    var eve: Double = 0.0,
    var morn: Double = 0.0
)

data class FeelsLike(
    var day: Double = 0.0,
    var night: Double = 0.0,
    var eve: Double = 0.0,
    var morn: Double = 0.0
)

data class WeatherInfo(
    var id: Int = 0,
    var main: String? = null,
    var description: String? = null,
    var icon: String? = null
)

data class Daily( // each day
    var dt: Int = 0,
    var sunrise: Int = 0,
    var sunset: Int = 0,
    var temp: Temp? = null,
    var feels_like: FeelsLike? = null,
    var pressure: Int = 0,
    var humidity: Int = 0,
    var dew_point: Double = 0.0,
    var wind_speed: Double = 0.0,
    var wind_deg: Int = 0,
    var weather: List<WeatherInfo>? = null,
    var clouds: Int = 0,
    var pop: Double = 0.0,
    var uvi: Double = 0.0,
    var rain: Double = 0.0
)

data class OneCall( // global info
    var lat: Double = 0.0,
    var lon: Double = 0.0,
    var timezone: String? = null,
    var timezone_offset: Int = 0,
    var current: CurrentDay? = null,
    var daily: MutableList<Daily>? = null
)