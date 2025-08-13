package com.example.wifi_connection

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.*
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.provider.Settings
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class WifiConnector(
    private val context: Context,
    private val tvStatus: TextView,
    private val progressBar: ProgressBar,
    private val onSuccess: () -> Unit
) {
    private val PERMISSIONS_REQUEST_CODE = 100

    fun checkPermissionsAndConnect() {
        // Show loading spinner
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Checking permissions..."

        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )

        val hasPermissions = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermissions) {
            ActivityCompat.requestPermissions(
                context as android.app.Activity,
                permissions,
                PERMISSIONS_REQUEST_CODE
            )
        } else {
            suggestNetwork()
        }
    }

    fun handlePermissionsResult(
        requestCode: Int,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                suggestNetwork()
            } else {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Permissions required to connect to Wi-Fi", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun suggestNetwork() {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val networkSSID = "ESP32_Config" // TODO: Change to your SSID
        val networkPass = "12345678" // TODO: Change to your password

        tvStatus.text = "Adding Wi-Fi suggestion..."

        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(networkSSID)
            .setWpa2Passphrase(networkPass)
            .build()

        val status = wifiManager.addNetworkSuggestions(listOf(suggestion))

        if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            tvStatus.text = "Suggestion added. Waiting for connection..."
        } else {
            tvStatus.text = "Failed to add suggestion. Status code: $status"
            progressBar.visibility = View.GONE
            return
        }

        // Listen for connectivity changes
        val connectivityFilter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val cm = this@WifiConnector.context
                    .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(activeNetwork)

                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val gatewayIp = getGatewayIpAddress()
                    val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

                    tvStatus.text = if (hasInternet) {
                        "Connected to $networkSSID with internet, IP: $gatewayIp"
                    } else {
                        "Connected to $networkSSID (local only), IP: $gatewayIp"
                    }

                    // Inside suggestNetwork(), after we get gatewayIp:
                    val localUrl = "http://$gatewayIp/login"
                    val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(localUrl))
                    browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    this@WifiConnector.context.startActivity(browserIntent)

                    // Do NOT update tvStatus here.
                    // We'll let MainActivity handle it after returning from browser.

                    markWifiSetupDone()
                    progressBar.visibility = View.GONE

                    // Trigger callback
                    onSuccess()

                    // Unregister receiver
                    context.unregisterReceiver(this)
                }
            }
        }, connectivityFilter)
    }

    private fun getGatewayIpAddress(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
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

    private fun markWifiSetupDone() {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("wifi_connected_once", true).apply()
    }
}
