package com.shadow.forecast.ui.weather

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.*
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.Bindable
import androidx.lifecycle.MutableLiveData
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.shadow.forecast.BR
import com.shadow.forecast.R
import com.shadow.forecast.adapter.OnTextViewKeyboardListener
import com.shadow.forecast.base.BaseViewModel
import com.shadow.forecast.extensions.hideKeyboard
import com.shadow.forecast.model.openweathermap.WeatherDisplayed
import com.shadow.forecast.model.openweathermap.current.Current
import com.shadow.forecast.model.openweathermap.fusion.Fusion
import com.shadow.forecast.model.openweathermap.oneCall.OneCall
import com.shadow.forecast.network.WeatherApi
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject


class WeatherViewModel(context: Context) : BaseViewModel(context.applicationContext as Application),
    LocationListener, OnTextViewKeyboardListener {

    @Inject
    lateinit var myWeatherApi: WeatherApi // Dagger2 !

    @Inject
    lateinit var myApplication: Application // Dagger2 !

    val keyMap: String by lazy {
        myApplication.resources.getString(R.string.key_map)
    }

    // RxAndroid
    private lateinit var subscription: Disposable

    // information about my position (bonus)
    private var myPersonnalLat: Double? = 0.0
    private var myPersonnalLong: Double? = 0.0

    // information about previous / next day
    private var savedFusion: Fusion? = null
        set(value) {
            field = value
            updateWeatherInfo()
        }

    @Bindable
    var loadingVisibility: Int = View.GONE; // one way binded

    @Bindable
    var town: String = "" // Two way binded

    // LiveData
    val currentInfo: MutableLiveData<WeatherDisplayed> = MutableLiveData()
    val nextInfo: MutableLiveData<List<WeatherDisplayed>> = MutableLiveData()
    val myPosition: MutableLiveData<GeoPoint> = MutableLiveData()
    val errorMessage: MutableLiveData<String?> = MutableLiveData()

    /**
     * Once my position is found, call getWeatherResultByPositionWithRxJava
     */
    override fun onLocationChanged(location: Location?) {

        myPersonnalLat = location?.latitude
        myPersonnalLong = location?.longitude

        val geocoder = Geocoder(myApplication, Locale.getDefault())
        val addresses: List<Address> = geocoder.getFromLocation(myPersonnalLat ?: 0.0, myPersonnalLong ?: 0.0, 1)
        town = addresses[0].featureName
        notifyPropertyChanged(BR.town)

        updateWeatherCity()
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        TODO("Not yet implemented")
    }

    override fun onProviderEnabled(provider: String?) {
        TODO("Not yet implemented")
    }

    override fun onProviderDisabled(provider: String?) {
        TODO("Not yet implemented")
    }

    override fun onKeyboardGo(v: TextView) {
        myApplication.hideKeyboard(v)
        updateWeatherCity()
    }

    override fun onKeyboardSearch(v:TextView) {
        myApplication.hideKeyboard(v)
        updateWeatherCity()
    }

    override fun onKeyboardDone(v:TextView) {
        myApplication.hideKeyboard(v)
        updateWeatherCity()
    }


    private fun updateWeatherCity() {
        town.let {

            subscription = myWeatherApi.getWeatherResultByTownWithRxJava(town, keyMap)
                .concatMap { result: Current ->

                    if (result.coord?.lat ?: 0 != 0 && result.coord?.lon ?: 0 != 0)
                        Observable.zip(
                            Observable.just(result),
                            myWeatherApi.getOneCallWeatherResultByLatLongWithRxJava(
                                result.coord!!.lat,
                                result.coord!!.lon,
                                "minutely,hourly,alerts",
                                keyMap
                            ),
                            object : Function2<Current, OneCall, Fusion> {
                                override fun invoke(p1: Current, p2: OneCall): Fusion {
                                    return Fusion(p1, p2)
                                }
                            })
                    else
                        Observable.zip(Observable.just(result), null,
                            object : Function2<Current, OneCall, Fusion> {
                                override fun invoke(p1: Current, p2: OneCall): Fusion {
                                    return Fusion(p1, p2)
                                }
                            })
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { onRetrieveTanArretListStart() }
                .doOnTerminate { onRetrieveTanArretListFinish() }
                .subscribe(
                    { fusion: Fusion -> onWeatherSuccess(fusion) },
                    { onError -> onWeatherError(onError) }
                )
        }
    }


    // manage weather info
    private fun onRetrieveTanArretListStart() {
        loadingVisibility = View.VISIBLE
        notifyPropertyChanged(BR.loadingVisibility)
    }

    private fun onRetrieveTanArretListFinish() {
        loadingVisibility = View.GONE
        notifyPropertyChanged(BR.loadingVisibility)
    }

    private fun onWeatherSuccess(result: Fusion) {

        // maybe clean nextDays anterior to current day
        result.OneCall.let { oneCall ->
            oneCall?.daily?.removeAll { daily -> daily.dt < (oneCall.current?.dt ?: 0) }
        }

        // save result of WS
        savedFusion = result

        // update map position
        myPosition.value =
            GeoPoint(result.Current?.coord?.lat ?: 0.0, result.Current?.coord?.lon ?: 0.0)
    }

    private fun updateWeatherInfo() {

        val dateFormat = SimpleDateFormat("EEE yyyy HH:mm", Locale("fr", "Fr"))

        // display current info from OneCall
        val current = savedFusion?.Current
        current?.let {

            currentInfo.value = WeatherDisplayed(
                it.weather?.get(0)?.icon,
                dateFormat.format(it.dt.times(1000L)),
                "${it.main?.temp ?: 0} °",
                "${it.wind?.speed ?: 0} km/h",
                "${it.weather?.get(0)?.main} /  ${
                    it.weather?.get(0)?.description
                }"
            )
        }


        val m: MutableList<WeatherDisplayed> = mutableListOf()
        // display all next days
        savedFusion?.OneCall?.daily?.forEach {
            // create list
            val oneDay = WeatherDisplayed(
                it.weather?.get(0)?.icon,
                dateFormat.format(it.dt.times(1000L)),
                "${it.temp?.day ?: 0} °",
                "${it.wind_speed} km/h",
                "${it.weather?.get(0)?.main} /  ${
                    it.weather?.get(0)?.description
                }"
            )
            m.add(oneDay)
        }
        nextInfo.value = m
    }

    private fun onWeatherError(t: Throwable) {
        errorMessage.value = t.message
    }

    // do not forget to clean
    override fun onCleared() {
        super.onCleared()
        if (this::subscription.isInitialized)
            subscription.dispose()
    }
}