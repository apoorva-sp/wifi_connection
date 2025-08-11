package com.example.wifi_connection

import android.content.Context
import android.net.*
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.text.format.Formatter

class WifiConnector(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // For API 29 and above — use NetworkSpecifier to connect
    fun connectUsingNetworkSpecifier(
        ssid: String,
        password: String,
        callback: (Boolean, String?) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            callback(false, null)
            return
        }

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                connectivityManager.bindProcessToNetwork(network)
                val gatewayIp = getGatewayIp()
                callback(true, gatewayIp)
            }

            override fun onUnavailable() {
                super.onUnavailable()
                callback(false, null)
            }
        }

        connectivityManager.requestNetwork(request, networkCallback)
    }

    // For API below 29 — use WifiManager to connect
    fun connectUsingWifiManager(ssid: String, password: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // This method is deprecated on API 29+
            return false
        }

        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val wifiConfig = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            preSharedKey = "\"$password\""
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
        }

        val networkId = wifiManager.addNetwork(wifiConfig)
        if (networkId == -1) return false

        val enabled = wifiManager.enableNetwork(networkId, true)
        val reconnected = wifiManager.reconnect()

        return enabled && reconnected
    }

    // Get gateway IP address of current Wi-Fi connection
    fun getGatewayIp(): String? {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo ?: return null
        return Formatter.formatIpAddress(dhcpInfo.gateway)
    }
}
