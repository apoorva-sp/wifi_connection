package com.example.wifi_connection

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private val PERMISSIONS_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.textViewStatus) // Make sure you have this TextView in layout

        checkPermissionsAndConnect()
    }

    private fun checkPermissionsAndConnect() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )

        val hasPermissions = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermissions) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST_CODE)
        } else {
            suggestNetwork()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                suggestNetwork()
            } else {
                Toast.makeText(this, "Permissions required to connect to Wi-Fi", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun suggestNetwork() {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

        val networkSSID = "ESP32_Config"
        val networkPass = "12345678"

        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(networkSSID)
            .setWpa2Passphrase(networkPass)
            .build()

        val status = wifiManager.addNetworkSuggestions(listOf(suggestion))

        if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            tvStatus.text = "Suggestion added. Waiting for connection..."
        } else {
            tvStatus.text = "Failed to add suggestion. Status code: $status"
        }

        // Listen for suggestion post-connection (may not fire instantly)
        val filter = IntentFilter(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                tvStatus.text = "Connected to $networkSSID (waiting for internet check...)"
                checkInternetAndProceed(networkSSID)
            }
        }, filter)

        // Also listen to network state changes (backup in case suggestion broadcast is slow)
        val connectivityFilter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val activeNetwork = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(activeNetwork)

                if (capabilities != null && capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                    val hasInternet = capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val gatewayIp = getGatewayIpAddress()

                    tvStatus.text = if (hasInternet) {
                        "Connected to $networkSSID with internet with ip $gatewayIp"

                    } else {
                        "Connected to $networkSSID but no internet"
                    }

                    // If connected but no internet, guide user to enable in settings
                    if (!hasInternet) {
                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        Toast.makeText(
                            this@MainActivity,
                            "Select \"$networkSSID\" in Wi-Fi settings to enable internet access",
                            Toast.LENGTH_LONG
                        ).show()
                        openExternalUrl("http://$gatewayIp/login")
                    } else {
                        openExternalUrl("http://$gatewayIp/login")
                    }
                }
            }
        }, connectivityFilter)
    }

    private fun checkInternetAndProceed(ssid: String) {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(activeNetwork)

        if (capabilities != null && capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            tvStatus.text = "Connected to $ssid with internet access "
            openExternalUrl("https://www.google.com")
        } else {
            tvStatus.text = "Connected to $ssid but no internet"
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    private fun openExternalUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
    private fun getGatewayIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo
        val gateway = dhcpInfo.gateway
        return String.format(
            "%d.%d.%d.%d",
            gateway and 0xff,
            gateway shr 8 and 0xff,
            gateway shr 16 and 0xff,
            gateway shr 24 and 0xff
        )
    }


}
