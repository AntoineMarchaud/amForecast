package com.shadow.forecast.injection.module

import dagger.Module
import dagger.Provides
import dagger.Reusable
import io.reactivex.schedulers.Schedulers
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import com.chenxyu.retrofit.adapter.FlowCallAdapterFactory
import com.shadow.forecast.network.WeatherApi

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
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io())) // returns Observable
            .addCallAdapterFactory(FlowCallAdapterFactory()) // returns Kotlin Flow
            .build()
    }
}