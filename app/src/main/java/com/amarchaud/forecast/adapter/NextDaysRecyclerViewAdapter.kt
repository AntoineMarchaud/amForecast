package com.amarchaud.forecast.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.amarchaud.forecast.R
import com.amarchaud.forecast.databinding.ItemWeatherBinding
import com.amarchaud.forecast.model.openweathermap.WeatherDisplayed

class NextDaysRecyclerViewAdapter(private val elements: List<WeatherDisplayed>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        val layoutInflator = LayoutInflater.from(parent.context)
        val binding: ItemWeatherBinding =
            DataBindingUtil.inflate(
                layoutInflator,
                R.layout.item_weather,
                parent,
                false
            )

        return WeatherViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is WeatherViewHolder) {
            val currentItem: WeatherDisplayed = elements.get(position)
            holder.binding.itemWeatherData = currentItem
        }
    }

    override fun getItemCount(): Int = elements.size

    inner class WeatherViewHolder(val binding: ItemWeatherBinding) :
        RecyclerView.ViewHolder(binding.root)
}