package com.shadow.forecast.ui.weather

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.databinding.Bindable
import androidx.lifecycle.MutableLiveData
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.multi.DialogOnAnyDeniedMultiplePermissionsListener
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


class WeatherViewModel(activity: AppCompatActivity) : BaseViewModel(activity.application),
    LocationListener, OnTextViewKeyboardListener {

    @Inject
    lateinit var myWeatherApi: WeatherApi // Dagger2 !

    var myActivity = activity

    val keyMap: String by lazy {
        activity.resources.getString(R.string.key_map)
    }

    // RxAndroid
    private lateinit var subscription: Disposable

    // information about my position (bonus)
    private var locationManager: LocationManager? = null
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

    init {
        locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        checkPermissions()
    }

    /**
     * Ask permission to user (for Map)
     */
    private fun checkPermissions() {
        Dexter
            .withActivity(myActivity)
            .withPermissions(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
            .withListener(object : MultiplePermissionsListener {
                @SuppressLint("MissingPermission")
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {

                    if (report.areAllPermissionsGranted()) {
                        // launch locationManager
                        locationManager?.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            1000,// in milliseconds
                            10f, // in meters
                            this@WeatherViewModel
                        )
                    }

                    if (report.isAnyPermissionPermanentlyDenied()) {
                        showSettingsDialog();
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<com.karumi.dexter.listener.PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    token?.continuePermissionRequest();
                }
            }).check()
    }

    /**
     * Showing Alert Dialog with Settings option
     * Navigates user to app settings
     */
    private fun showSettingsDialog() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(myActivity)
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
        val uri: Uri = Uri.fromParts("package", myActivity.packageName, null)
        intent.data = uri
        myActivity.startActivityForResult(intent, 101)
    }

    /**
     * Once my position is found, call getWeatherResultByPositionWithRxJava
     */
    override fun onLocationChanged(location: Location?) {

        myPersonnalLat = location?.latitude
        myPersonnalLong = location?.longitude

        // launch webWs for my position
        if (myPersonnalLat != null && myPersonnalLong != null) {
            subscription = myWeatherApi.getWeatherResultByPositionWithRxJava(
                myPersonnalLat!!,
                myPersonnalLong!!,
                keyMap
            )
                .concatMap { current: Current ->

                    Observable.zip(
                        Observable.just(current),
                        myWeatherApi.getOneCallWeatherResultByLatLongWithRxJava(
                            myPersonnalLat!!,
                            myPersonnalLong!!,
                            "minutely,hourly,alerts",
                            keyMap
                        ),
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
                    { fusion: Fusion ->

                        // change my town
                        town = fusion.Current?.name
                            ?: "" // the name of the town is only in the first WS (Current)
                        notifyPropertyChanged(BR.town)

                        onWeatherSuccess(fusion)
                    },
                    { onError -> println(onError) }
                )
        }
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

    override fun onKeyboardGo() {
        myActivity.hideKeyboard()
        searchCity()
    }

    override fun onKeyboardSearch() {
        myActivity.hideKeyboard()
        searchCity()
    }

    override fun onKeyboardDone() {
        myActivity.hideKeyboard()
        searchCity()
    }


    private fun searchCity() {
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