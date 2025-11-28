package com.neo.maps

import android.app.Application
import com.google.firebase.FirebaseApp

class PhotonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        // At this point Firebase Remote Config etc. are ready to be used.
    }
}
