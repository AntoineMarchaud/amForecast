package com.amarchaud.forecast.ui.weather

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.databinding.Bindable
import androidx.lifecycle.MutableLiveData
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.amarchaud.forecast.BR
import com.amarchaud.forecast.R
import com.amarchaud.forecast.adapter.OnTextViewKeyboardListener
import com.amarchaud.forecast.base.BaseViewModel
import com.amarchaud.forecast.extensions.hideKeyboard
import com.amarchaud.forecast.model.openweathermap.WeatherDisplayed
import com.amarchaud.forecast.model.openweathermap.current.Current
import com.amarchaud.forecast.model.openweathermap.fusion.Fusion
import com.amarchaud.forecast.model.openweathermap.oneCall.OneCall
import com.amarchaud.forecast.network.WeatherApi
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject


class WeatherViewModel(context: Context) : BaseViewModel(context),
    LocationListener, OnTextViewKeyboardListener {

    @Inject
    lateinit var myWeatherApi: WeatherApi // Dagger2 !

    @Inject
    lateinit var myApplication: Application // Dagger2 !

    private var myContext =  context
    private val keyMap: String = myContext.resources.getString(R.string.key_map)

    // for autogeoloc
    private var locationManager: LocationManager? = null

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
     * Ask permission to user, and start autolocalizer if it is possible
     */
    fun onAutoLocalize(v: View) {
        Dexter
            .withActivity(v.context as Activity)
            .withPermissions(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            .withListener(object : MultiplePermissionsListener {
                @SuppressLint("MissingPermission")
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {

                    if (report.areAllPermissionsGranted()) {

                        loadingVisibility = View.VISIBLE;
                        notifyPropertyChanged(BR.loadingVisibility)

                        locationManager =
                            myContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                        locationManager?.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            1000,// in milliseconds
                            10f, // in meters
                            this@WeatherViewModel
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
        locationManager?.removeUpdates(this)
        locationManager = null;
    }

    /**
     * Once my position is found, call getWeatherResultByPositionWithRxJava
     */
    override fun onLocationChanged(location: Location?) {

        loadingVisibility = View.GONE;
        notifyPropertyChanged(BR.loadingVisibility)

        myPersonnalLat = location?.latitude
        myPersonnalLong = location?.longitude

        onStopGpsLocalize() // no need anymore

        val geocoder = Geocoder(myContext, Locale.getDefault())
        val addresses: List<Address> = geocoder.getFromLocation(
            myPersonnalLat ?: 0.0,
            myPersonnalLong ?: 0.0,
            1
        )
        town = addresses[0].locality
        notifyPropertyChanged(BR.town)

        updateWeatherCity()
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {

    }

    override fun onProviderEnabled(provider: String?) {


    }

    override fun onProviderDisabled(provider: String?) {
        // if no gps
        loadingVisibility = View.GONE;
        notifyPropertyChanged(BR.loadingVisibility)

        if(provider == "gps")
            errorMessage.value = myApplication.getString(R.string.pleaseActivateGps)
    }

    override fun onKeyboardGo(v: TextView) {
        myContext.hideKeyboard(v)
        updateWeatherCity()
    }

    override fun onKeyboardSearch(v: TextView) {
        myContext.hideKeyboard(v)
        updateWeatherCity()
    }

    override fun onKeyboardDone(v: TextView) {
        myContext.hideKeyboard(v)
        updateWeatherCity()
    }


    private fun updateWeatherCity() {
        town.let {

            subscription = myWeatherApi.getWeatherResultByTownWithRxJava(town, keyMap)
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
    }


    // manage weather info
    private fun onRetrieveForecastStart() {
        loadingVisibility = View.VISIBLE
        notifyPropertyChanged(BR.loadingVisibility)
    }

    private fun onRetrieveForecastFinish() {
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

        val dateFormat = SimpleDateFormat("EEE dd/MM HH:mm", Locale("fr", "Fr"))

        // display current info from OneCall
        val current = savedFusion?.Current
        current?.let {

            currentInfo.value = WeatherDisplayed(
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

        onStopGpsLocalize()
    }

    /**
     * Showing Alert Dialog with Settings option
     * Navigates user to app settings
     */
    fun showSettingsDialog() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(myContext)
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
        val uri: Uri = Uri.fromParts("package", myContext.packageName, null)
        intent.data = uri
        myContext.startActivity(intent)
    }

    fun reset() {
        errorMessage.value = null
    }
}