package com.amarchaud.forecast.base

import android.app.Application
import android.content.Context
import androidx.databinding.Observable
import androidx.databinding.PropertyChangeRegistry
import androidx.lifecycle.ViewModel
import com.amarchaud.forecast.injection.component.DaggerViewModelInjectorComponent
import com.amarchaud.forecast.injection.component.ViewModelInjectorComponent
import com.amarchaud.forecast.injection.module.AppModule
import com.amarchaud.forecast.injection.module.NetworkModule
import com.amarchaud.forecast.ui.weather.WeatherViewModel

/**
 * Every ViewModel must derived from this class
 */
abstract class BaseViewModel(context: Context) : ViewModel(), Observable {

    // *****************************************
    //        Manage NotifyPropertyChanged
    // *****************************************

    private val callbacks: PropertyChangeRegistry by lazy { PropertyChangeRegistry() }

    override fun addOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback) {
        callbacks.add(callback)
    }

    override fun removeOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback) {
        callbacks.remove(callback)
    }

    /**
     * Notifies listeners that all properties of this instance have changed.
     */
    @Suppress("unused")
    fun notifyChange() {
        callbacks.notifyCallbacks(this, 0, null)
    }

    /**
     * Notifies listeners that a specific property has changed. The getter for the property
     * that changes should be marked with [Bindable] to generate a field in
     * `BR` to be used as `fieldId`.
     *
     * @param fieldId The generated BR id for the Bindable field.
     */
    fun notifyPropertyChanged(fieldId: Int) {
        callbacks.notifyCallbacks(this, fieldId, null)
    }

    // *****************************************
    //        Manage Dagger injection
    // *****************************************

    // call Dagger2
    // convention is Dagger[Name of @Component]
    // DaggerViewModelInjector is generated at compile
    private val component: ViewModelInjectorComponent = DaggerViewModelInjectorComponent
        .builder()
        .appModule(AppModule(context.applicationContext as Application))
        .networkModule(NetworkModule())
        .build()

    init {
        requestInjection()
    }

    /**
     * Injects the required dependencies
     */
    private fun requestInjection() {
        when (this) {
            is WeatherViewModel -> {
                component.inject(this)
            }
        }
    }
}