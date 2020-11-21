package com.shadow.forecast.adapter

import android.net.Uri
import android.widget.ImageView
import androidx.databinding.BindingAdapter
import com.shadow.forecast.R
import com.squareup.picasso.Picasso

object ImageViewBindingAdapter {
    @JvmStatic
    @BindingAdapter("onWeatherImageLoad")
    fun setOnImageLoad(view: ImageView, logo: String?) {
        logo?.let {
            try {
                Picasso.get()
                    .load(Uri.parse("http://openweathermap.org/img/wn/${it}.png"))
                    .into(
                        view
                    )
            } catch (e: IllegalArgumentException) {
                view.setImageResource(R.drawable.app_logo)
            }
        }
    }
}