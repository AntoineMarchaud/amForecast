package com.shadow.forecast.ui.weather

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
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

    // recycler view
    private lateinit var nextDaysRecyclerViewAdapter: NextDaysRecyclerViewAdapter

    // One Marker stored
    private var marker: Marker? = null

    companion object {
        val TOWN : String = "town";
    }

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
            error?.let {
                Toast.makeText(this@WeatherActivity, error, Toast.LENGTH_SHORT).show()
            }
        })
    }

    public override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    public override fun onPause() {
        super.onPause()
        mapView.onPause()
    }


    public override fun onDestroy() {
        super.onDestroy()
        viewModel.reset()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        //townEntered.setText(savedInstanceState.getString(TOWN), TextView.BufferType.EDITABLE)
    }

    override fun onSaveInstanceState(outState: Bundle) {

        outState.run {
            //putString(TOWN, townEntered.text.toString())
        }
        // call superclass to save any view hierarchy
        super.onSaveInstanceState(outState)
    }
}