package com.example.tfg.vista

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.tfg.R

class EliminacionCuentaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eliminacion_cuenta)

        val toolbar = findViewById<Toolbar>(R.id.toolbar_eliminacion)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.info_eliminacion_titulo)

        val webView = findViewById<WebView>(R.id.webview_eliminacion)
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.loadUrl("file:///android_asset/eliminacion-cuenta.html")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
