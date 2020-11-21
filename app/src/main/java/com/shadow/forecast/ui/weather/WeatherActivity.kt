package com.shadow.forecast.ui.weather

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.shadow.forecast.BuildConfig
import com.shadow.forecast.R
import com.shadow.forecast.adapter.NextDaysRecyclerViewAdapter
import com.shadow.forecast.databinding.ActivityWeatherBinding
import com.shadow.forecast.injection.ViewModelFactory
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_weather.*
import kotlinx.android.synthetic.main.item_weather.view.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker

class WeatherActivity : AppCompatActivity() {

    private lateinit var viewModel: WeatherViewModel
    private lateinit var binding: ActivityWeatherBinding

    private var locationManager: LocationManager? = null

    // recycler view
    private lateinit var nextDaysRecyclerViewAdapter: NextDaysRecyclerViewAdapter

    // One Marker stored
    private var marker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather)

        // init viewModel
        viewModel =
            ViewModelProviders.of(this, ViewModelFactory(this)).get(WeatherViewModel::class.java)

        // Obtain binding for MVVM
        binding =
            DataBindingUtil.setContentView<ActivityWeatherBinding>(this, R.layout.activity_weather)
        binding.weatherViewModel = viewModel
        binding.lifecycleOwner = this

        // force the recycler view to be in line mode
        nextDayRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // other
        townEntered.setImeActionLabel("Weather !", KeyEvent.KEYCODE_ENTER)

        // map default config
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID // VERY IMPORTANT !
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
        mapView.setMultiTouchControls(true)
        val mapController = mapView.controller
        mapController.setZoom(10.0)
        marker = Marker(mapView)

        viewModel.currentInfo.observe(this, { currentInfo ->

            currentWeatherItem.textViewDate.text = currentInfo.date
            currentWeatherItem.textViewTemperature.text = currentInfo.temperature
            currentWeatherItem.textViewWindSpeed.text = currentInfo.windSpeed
            currentWeatherItem.textViewDetailedForecast.text = currentInfo.details

            try {
                Picasso.get()
                    .load(Uri.parse("http://openweathermap.org/img/wn/${currentInfo.logo}.png"))
                    .into(
                        currentWeatherItem.imageViewWeather
                    )
            } catch (e: IllegalArgumentException) {
                currentWeatherItem.imageViewWeather.setImageResource(R.drawable.app_logo)
            }
        })
        viewModel.nextInfo.observe(this, { nextInfo ->
            // populate recycler view
            nextDaysRecyclerViewAdapter = NextDaysRecyclerViewAdapter(nextInfo)
            binding.nextDayRecyclerView.adapter = nextDaysRecyclerViewAdapter
            nextDaysRecyclerViewAdapter.notifyDataSetChanged()
        })
        // update current / choosen position on mapview
        viewModel.myPosition.observe(this, { p ->
            mapView.controller.setCenter(p)
            mapView.controller.animateTo(p)
            mapView.overlays.remove(marker)
            marker?.position = p
            marker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            mapView.overlays.add(marker)
        })
        // error message
        viewModel.errorMessage.observe(this, { error ->
            Toast.makeText(this@WeatherActivity, error, Toast.LENGTH_SHORT).show()
        })

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        checkPermissions()
    }

    /**
     * Ask permission to user (for Map)
     */
    private fun checkPermissions() {
        Dexter
            .withActivity(this)
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
                            viewModel
                        )
                    }

                    if (report.isAnyPermissionPermanentlyDenied) {
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
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
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
        val uri: Uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivityForResult(intent, 101)
    }

    public override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    public override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}