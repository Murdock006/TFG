package com.example.tfg

import android.app.Application
import android.content.Context
import com.example.tfg.service.firebase.FirebaseComposition

class TFGApplication : Application() {
    companion object {
        var appContext: Context? = null
            private set
    }
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        FirebaseComposition.init(this, BuildConfig.FIREBASE_MODE, BuildConfig.FIREBASE_HOST)
    }
}
