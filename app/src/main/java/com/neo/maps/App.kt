package com.neo.maps

import android.app.Application
import android.util.Log

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // Point d’entrée global de l’app
        Log.i("App", "Application com.neo.maps.App started")
        // Si tu dois initialiser Firebase, Crashlytics, etc.,
        // tu pourras le faire ici plus tard.
    }
}
