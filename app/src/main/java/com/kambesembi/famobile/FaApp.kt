package com.kambesembi.famobile

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.kambesembi.famobile.data.Words
import com.kambesembi.famobile.data.AppLanguage

class FaApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    // Notifications and background work speak the chosen language too.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        Words.context = this
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = container.imageLoader
}
