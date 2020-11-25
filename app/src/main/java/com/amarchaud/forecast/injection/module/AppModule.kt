package com.amarchaud.forecast.injection.module

import android.app.Application
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class AppModule(application: Application) {

    var mApplication: Application = application

    @Provides
    @Singleton
    fun provideApplication(): Application {
        return mApplication
    }
}