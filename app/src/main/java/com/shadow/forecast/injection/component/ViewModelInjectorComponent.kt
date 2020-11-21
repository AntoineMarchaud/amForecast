package com.shadow.forecast.injection.component

import com.shadow.forecast.injection.module.AppModule
import com.shadow.forecast.injection.module.NetworkModule
import com.shadow.forecast.ui.weather.WeatherViewModel
import dagger.Component
import javax.inject.Singleton

//Connecting @Modules With @Inject
// interface


@Singleton
@Component(modules = [(AppModule::class), (NetworkModule::class)])
interface ViewModelInjectorComponent {

    // This tells Dagger that requests injection so the graph nee   ds to
    // satisfy all the dependencies of the fields that LoginActivity is requesting.
    fun inject(weatherViewModel: WeatherViewModel)

    @Component.Builder
    interface Builder {
        fun build(): ViewModelInjectorComponent
        fun appModule(appModule:AppModule) : Builder
        fun networkModule(networkModule: NetworkModule): Builder
    }
}