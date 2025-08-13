package com.example.wifi_connection

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.widget.ProgressBar


class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var wifiConnector: WifiConnector
    private var browserOpened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.textViewStatus)
        progressBar = findViewById(R.id.progressBar)

        wifiConnector = WifiConnector(
            context = this,
            tvStatus = tvStatus,
            progressBar = progressBar
        ) {
            browserOpened = true // Mark that browser was opened
        }

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val alreadyConnected = prefs.getBoolean("wifi_connected_once", false)

        if (!alreadyConnected) {
            wifiConnector.checkPermissionsAndConnect()
        } else {
            tvStatus.text = "Wi-Fi already set up"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        wifiConnector.handlePermissionsResult(requestCode, grantResults)

    }

    override fun onResume() {
        super.onResume()
        if (browserOpened) {
            tvStatus.text = "Returned from browser. Connection flow complete."
            browserOpened = false // Reset flag
        }
    }
}
