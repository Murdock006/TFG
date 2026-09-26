package es.sintaxys.teamtask

import android.app.Application
import android.content.Context
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
    }
}
