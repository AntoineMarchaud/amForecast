package com.amarchaud.forecast.injection.module

import com.amarchaud.forecast.network.WeatherApi
import dagger.Module
import dagger.Provides
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
class NetworkModule {

    /**
     * Provides the service implementation.
     * @Provides tell Dagger how to create instances of the type that this functionw
     * @param retrofit the Retrofit object used to instantiate the service
     * @return the Tan service implementation.
     */
    @Provides
    fun provideForecastApi(retrofit: Retrofit): WeatherApi {
        return retrofit.create(WeatherApi::class.java)
    }

    /**
     * Provides the Retrofit object.
     * @Provides tell Dagger how to create instances of the type that this function
     * @return the Retrofit object
     */
    @Provides
    fun provideRetrofitInterface(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://api.openweathermap.org/data/2.5/")
            .addConverterFactory(MoshiConverterFactory.create()) // JSON management
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create()) // returns Observable
            .build()
    }
}