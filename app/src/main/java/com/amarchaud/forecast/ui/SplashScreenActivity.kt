package com.amarchaud.forecast.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.amarchaud.forecast.R
import com.amarchaud.forecast.ui.weather.WeatherActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class SplashScreenActivity : Activity() {

    companion object {
        val TAG = SplashScreenActivity::class.simpleName
        const val TIME_FOR_NEXT_SCREEN = 1000L
        enum class STATE { STOP, NOT_NOW, YOU_CAN_CONTINUE }
    }

    private suspend fun waitForNextScreen() = flow {
        emit(STATE.STOP)
        kotlinx.coroutines.delay(10L)
        emit(STATE.NOT_NOW)
        kotlinx.coroutines.delay(TIME_FOR_NEXT_SCREEN);
        emit(STATE.YOU_CAN_CONTINUE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        GlobalScope.launch {
            waitForNextScreen().collect {
                when (it) {
                    STATE.STOP -> println("$TAG STOP")
                    STATE.NOT_NOW -> println("$TAG NOT NOW")
                    STATE.YOU_CAN_CONTINUE -> {
                        println("$TAG CONTINUE")

                        val i = Intent(this@SplashScreenActivity, WeatherActivity::class.java)
                        startActivity(i)

                        // close this activity
                        finish()
                    }
                }

            }
        }
    }
}