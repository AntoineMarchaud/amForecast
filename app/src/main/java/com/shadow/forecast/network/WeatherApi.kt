package com.shadow.forecast.network

import com.shadow.forecast.model.openweathermap.current.Current
import com.shadow.forecast.model.openweathermap.oneCall.OneCall
import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {

    // with RxJava
    @GET("weather?units=metric")
    fun getWeatherResultByTownWithRxJava(
        @Query("q") city: String?,
        @Query("appid") key: String?
    ): Observable<Current>

    // with RxJava
    @GET("weather?units=metric")
    fun getWeatherResultByPositionWithRxJava(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") key: String?
    ): Observable<Current>

    // with RxJava
    @GET("onecall?units=metric")
    fun getOneCallWeatherResultByLatLongWithRxJava(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("exclude") exclude: String,
        @Query("appid") key: String?
    ): Observable<OneCall>
}