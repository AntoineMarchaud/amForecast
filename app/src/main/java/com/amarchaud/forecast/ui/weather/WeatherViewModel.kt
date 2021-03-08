package com.amarchaud.forecast.ui.weather

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.*
import android.location.LocationListener
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.amarchaud.forecast.R
import com.amarchaud.forecast.adapter.OnTextViewKeyboardListener
import com.amarchaud.forecast.extensions.hideKeyboard
import com.amarchaud.forecast.injection.component.DaggerViewModelInjectorComponent
import com.amarchaud.forecast.injection.component.ViewModelInjectorComponent
import com.amarchaud.forecast.injection.module.AppModule
import com.amarchaud.forecast.injection.module.NetworkModule
import com.amarchaud.forecast.model.openweathermap.WeatherDisplayed
import com.amarchaud.forecast.model.openweathermap.current.Current
import com.amarchaud.forecast.model.openweathermap.fusion.Fusion
import com.amarchaud.forecast.model.openweathermap.oneCall.OneCall
import com.amarchaud.forecast.network.WeatherApi
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject


class WeatherViewModel(private val app: Application) : AndroidViewModel(app),
    OnTextViewKeyboardListener {

    @Inject
    lateinit var myWeatherApi: WeatherApi // Dagger2 !

    private val keyMap: String = app.resources.getString(R.string.key_map)

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

    private var _loadingMutableLiveData = MutableLiveData<Boolean>()
    val loadingLiveData: LiveData<Boolean>
        get() = _loadingMutableLiveData


    // two way binding
    val town = MutableLiveData<String>("")

    // LiveData
    private var _currentInfo = MutableLiveData<WeatherDisplayed>()
    val currentInfo: LiveData<WeatherDisplayed>
        get() = _currentInfo

    private var _nextInfo = MutableLiveData<List<WeatherDisplayed>>()
    val nextInfo: LiveData<List<WeatherDisplayed>>
        get() = _nextInfo

    private var _myPosition = MutableLiveData<GeoPoint>()
    val myPosition: LiveData<GeoPoint>
        get() = _myPosition

    private var _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?>
        get() = _errorMessage


    /**
     * Dagger2 management
     */
    // call Dagger2
    // convention is Dagger[Name of @Component]
    // DaggerViewModelInjector is generated at compile
    private val component: ViewModelInjectorComponent = DaggerViewModelInjectorComponent
        .builder()
        .appModule(AppModule(app))
        .networkModule(NetworkModule())
        .build()

    // for autoloc
    var currentLocation: Location? = null
    private var mLocationRequest: LocationRequest = LocationRequest.create()
    private val locationProviderClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(app)
    }
    private val locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                currentLocation = location

                /**
                 * Once my position is found, call getWeatherResultByPositionWithRxJava
                 */
                _loadingMutableLiveData.postValue(false)

                myPersonnalLat = location.latitude
                myPersonnalLong = location.longitude

                onStopGpsLocalize() // no need anymore

                val geocoder = Geocoder(app, Locale.getDefault())
                val addresses: List<Address> = geocoder.getFromLocation(
                    myPersonnalLat ?: 0.0,
                    myPersonnalLong ?: 0.0,
                    1
                )
                town.value = addresses[0].locality

                updateWeatherCity()
            }
        }

        override fun onLocationAvailability(locationAvailability: LocationAvailability) {
            if (!locationAvailability.isLocationAvailable) {
                currentLocation = null
            }
        }
    }

    init {
        component.inject(this)

        mLocationRequest.interval = 1000
        mLocationRequest.fastestInterval = 1000
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    /**
     * Ask permission to user, and start autolocalizer if it is possible
     */
    fun onAutoLocalize(v: View) {
        Dexter
            .withContext(app)
            .withPermissions(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            .withListener(object : MultiplePermissionsListener {
                @SuppressLint("MissingPermission")
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {

                    if (report.areAllPermissionsGranted()) {

                        _loadingMutableLiveData.postValue(true)

                        locationProviderClient.requestLocationUpdates(
                            mLocationRequest,
                            locationCallback,
                            Looper.getMainLooper()
                        )
                    }

                    if (report.isAnyPermissionPermanentlyDenied) {
                        showSettingsDialog()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<com.karumi.dexter.listener.PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    token?.continuePermissionRequest()
                }
            }).check()
    }

    /**
     * Stop auto localizer
     */
    private fun onStopGpsLocalize() {
        locationProviderClient.flushLocations()
        locationProviderClient.removeLocationUpdates(locationCallback)
    }

    override fun onKeyboardGo(v: TextView) {
        app.hideKeyboard(v)
        updateWeatherCity()
    }

    override fun onKeyboardSearch(v: TextView) {
        app.hideKeyboard(v)
        updateWeatherCity()
    }

    override fun onKeyboardDone(v: TextView) {
        app.hideKeyboard(v)
        updateWeatherCity()
    }


    private fun updateWeatherCity() {

        subscription = myWeatherApi.getWeatherResultByTownWithRxJava(town.value, keyMap)
            .concatMap { result: Current ->

                // zip results current / OneCall to one struct

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
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { onRetrieveForecastStart() }
            .doOnTerminate { onRetrieveForecastFinish() }
            .subscribe(
                { fusion: Fusion -> onWeatherSuccess(fusion) },
                { onError -> onWeatherError(onError) }
            )
    }


    // manage weather info
    private fun onRetrieveForecastStart() {
        _loadingMutableLiveData.postValue(true)
    }

    private fun onRetrieveForecastFinish() {
        _loadingMutableLiveData.postValue(false)
    }

    private fun onWeatherSuccess(result: Fusion) {

        // maybe clean nextDays anterior to current day
        result.OneCall.let { oneCall ->
            oneCall?.daily?.removeAll { daily -> daily.dt < (oneCall.current?.dt ?: 0) }
        }

        // save result of WS
        savedFusion = result

        // update map position
        _myPosition.value =
            GeoPoint(result.Current?.coord?.lat ?: 0.0, result.Current?.coord?.lon ?: 0.0)
    }

    private fun updateWeatherInfo() {

        val dateFormat = SimpleDateFormat("EEE dd/MM HH:mm", Locale("fr", "Fr"))

        // display current info from OneCall
        val current = savedFusion?.Current
        current?.let {

            _currentInfo.value = WeatherDisplayed(
                it.weather?.get(0)?.icon,
                dateFormat.format(it.dt.times(1000L)),
                "${it.main?.temp?.toInt() ?: 0} °C",
                "${it.wind?.speed?.times(3.6f)?.toInt() ?: 0} km/h",
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
                "${it.temp?.day?.toInt() ?: 0} °C",
                "${it.wind_speed.times(3.6f).toInt()} km/h",
                "${it.weather?.get(0)?.main} /  ${
                    it.weather?.get(0)?.description
                }"
            )
            m.add(oneDay)
        }
        _nextInfo.value = m
    }

    private fun onWeatherError(t: Throwable) {
        _errorMessage.value = t.message
    }

    // do not forget to clean
    override fun onCleared() {
        super.onCleared()
        if (this::subscription.isInitialized)
            subscription.dispose()

        onStopGpsLocalize()
    }

    /**
     * Showing Alert Dialog with Settings option
     * Navigates user to app settings
     */
    fun showSettingsDialog() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(app)
        builder.setTitle(R.string.permissionGpsTitle)
        builder.setMessage(R.string.permissionGpsMessage)
        builder.setPositiveButton(R.string.permissionGpsOk) { dialog, which ->
            dialog.cancel()
            openSettings()
        }
        builder.setNegativeButton(R.string.permissionGpsCancel) { dialog, which -> dialog.cancel() }
        builder.show()
    }

    // navigating user to app settings
    private fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri: Uri = Uri.fromParts("package", app.packageName, null)
        intent.data = uri
        app.startActivity(intent)
    }

    fun reset() {
        _errorMessage.value = null
    }
}