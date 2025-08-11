package com.example.wifi_connection

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.*
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.DhcpInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var wifiManager: WifiManager

    private val PERMISSIONS_REQUEST_CODE = 100
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.textViewStatus)
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        checkPermissionsAndConnect()
    }

    private fun checkPermissionsAndConnect() {
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasPermissions = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermissions) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST_CODE)
        } else {
            connectToWifi("Apoorva5g", "apoorva2003sp@")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToWifi("Apoorva5g", "apoorva2003sp@")
            } else {
                Toast.makeText(
                    this,
                    "Location permission is required for Wi-Fi connection",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun connectToWifi(ssid: String, password: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectUsingNetworkSpecifier(ssid, password)
        } else {
            connectUsingWifiManager(ssid, password)
        }
    }

    private fun connectUsingNetworkSpecifier(ssid: String, password: String) {
        tvStatus.text = "Status: Connecting to $ssid (API 29+) ..."

        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
        }

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                connectivityManager?.bindProcessToNetwork(network)

                val linkProperties = connectivityManager?.getLinkProperties(network)
                val routes = linkProperties?.routes
                val gatewayIp = routes?.firstOrNull { route ->
                    route.destination.prefixLength == 0
                }?.gateway?.hostAddress

                runOnUiThread {
                    if (gatewayIp != null) {
                        tvStatus.text = "Status: Connected to $ssid\nGateway IP: $gatewayIp"
                        openLoginPage(gatewayIp)
                    } else {
                        tvStatus.text = "Status: Connected to $ssid\nGateway IP not found"
                        Toast.makeText(this@MainActivity, "Gateway IP not found", Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }

            override fun onUnavailable() {
                super.onUnavailable()
                runOnUiThread {
                    tvStatus.text = "Status: Failed to connect to $ssid"
                    Toast.makeText(this@MainActivity, "Failed to connect", Toast.LENGTH_LONG).show()
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                runOnUiThread {
                    tvStatus.text = "Status: Connection lost"
                }
            }
        }

        connectivityManager?.requestNetwork(request, networkCallback!!)
    }


    private fun connectUsingWifiManager(ssid: String, password: String) {
        tvStatus.text = "Status: Connecting to $ssid (API <29) ..."

        if (!wifiManager.isWifiEnabled) {
            wifiManager.isWifiEnabled = true
        }

        val existingConfig = wifiManager.configuredNetworks?.find { it.SSID == "\"$ssid\"" }
        existingConfig?.let {
            wifiManager.removeNetwork(it.networkId)
            wifiManager.saveConfiguration()
        }

        val wifiConfig = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            preSharedKey = "\"$password\""
            status = WifiConfiguration.Status.ENABLED
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
        }

        val netId = wifiManager.addNetwork(wifiConfig)
        if (netId == -1) {
            tvStatus.text = "Status: Failed to add Wi-Fi network"
            Toast.makeText(this, "Failed to add network", Toast.LENGTH_LONG).show()
            return
        }

        wifiManager.disconnect()
        wifiManager.enableNetwork(netId, true)
        wifiManager.reconnect()

        tvStatus.postDelayed({
            val dhcp: DhcpInfo = wifiManager.dhcpInfo
            val gatewayIp = intToIp(dhcp.gateway)
            tvStatus.text = "Status: Connected to $ssid\nGateway IP: $gatewayIp"
            openLoginPage(gatewayIp)
        }, 5000)
    }

    private fun openLoginPage(ipAddress: String) {
        val url = "http://$ipAddress/login"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun intToIp(i: Int): String {
        return ((i and 0xFF).toString() + "." +
                ((i shr 8) and 0xFF) + "." +
                ((i shr 16) and 0xFF) + "." +
                ((i shr 24) and 0xFF))
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
        }
    }
}