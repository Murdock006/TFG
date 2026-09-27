package es.sintaxys.teamtask

import android.app.Application
import android.content.Context
import com.google.android.gms.ads.MobileAds
import es.sintaxys.teamtask.service.firebase.FirebaseComposition

class TFGApplication : Application() {
    companion object {
        var appContext: Context? = null
            private set
    }
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        FirebaseComposition.init(this, BuildConfig.FIREBASE_MODE, BuildConfig.FIREBASE_HOST)
        // Inicializa el SDK de AdMob una sola vez con el contexto de aplicación.
        // El App ID se toma del manifest (meta-data), que alterna TEST/REAL por buildType.
        MobileAds.initialize(this)
    }
}
