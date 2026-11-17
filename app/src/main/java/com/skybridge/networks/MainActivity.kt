
package com.skybridge.networks

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var statusText: TextView
    private lateinit var resultsText: TextView
    private lateinit var scanButton: Button

    private val permissionCode = 101

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                displayNetworks()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        resultsText = findViewById(R.id.resultsText)
        scanButton = findViewById(R.id.scanButton)

        wifiManager = applicationContext.getSystemService(
            Context.WIFI_SERVICE
        ) as WifiManager

        connectivityManager = getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val filter = IntentFilter(
            WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
        )

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                wifiReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(wifiReceiver, filter)
        }

        scanButton.setOnClickListener {
            if (hasLocationPermission()) {
                startWifiScan()
            } else {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    permissionCode
                )
            }
        }

        showConnectionStatus()
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showConnectionStatus() {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        if (network == null || capabilities == null ||
            !capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) || !capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )) {

            statusText.text = "INTERNET NOT VERIFIED"
            resultsText.text =
                "No validated internet connection detected."
            return
        }

        when {
            capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_CELLULAR
            ) -> {
                statusText.text = "MOBILE DATA CONNECTED"
                resultsText.text =
                    "Provider profile: Smart (selected)\n" +
                    "Connection: Cellular\n" +
                    "Internet: Verified\n\n" +
                    "SkyBridge is using your phone's active mobile connection."
            }

            capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
            ) -> {
                statusText.text = "WI-FI CONNECTED"
                resultsText.text =
                    "Connection: Wi-Fi\n" +
                    "Internet: Verified\n\n" +
                    "The active connection is Wi-Fi, not necessarily Smart."
            }

            else -> {
                statusText.text = "NETWORK CONNECTED"
                resultsText.text =
                    "Internet connection verified.\n" +
                    "Transport type not identified."
            }
        }
    }

    private fun startWifiScan() {
        val locationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (Build.VERSION.SDK_INT >= 28 &&
            !locationManager.isLocationEnabled) {
            statusText.text = "LOCATION SERVICES REQUIRED"
            resultsText.text = "Enable Location and try again."
            return
        }

        if (!wifiManager.isWifiEnabled) {
            statusText.text = "WI-FI IS TURNED OFF"
            resultsText.text = "Turn on Wi-Fi to scan nearby networks."
            return
        }

        try {
            statusText.text = "SCANNING..."
            resultsText.text = "Searching for nearby Wi-Fi networks..."

            if (!wifiManager.startScan()) {
                statusText.text = "SCAN NOT STARTED"
                resultsText.text =
                    "Android may be limiting Wi-Fi scans. Try again later."
            }
        } catch (e: SecurityException) {
            statusText.text = "PERMISSION ERROR"
            resultsText.text = "Check location permission."
        }
    }

    private fun displayNetworks() {
        if (!hasLocationPermission()) return

        try {
            val networks = wifiManager.scanResults
            val output = StringBuilder()

            if (networks.isEmpty()) {
                statusText.text = "NO NETWORKS FOUND"
                resultsText.text = "No Wi-Fi scan results available."
                return
            }

            networks.forEachIndexed { index, network ->
                val level = WifiManager.calculateSignalLevel(
                    network.level, 100
                )

                output.append("NETWORK ${index + 1}\n")
                output.append(
                    "Name: ${network.SSID.ifBlank { "Hidden network" }}\n"
                )
                output.append("Signal: ${network.level} dBm\n")
                output.append("Strength: $level%\n")
                output.append("Frequency: ${network.frequency} MHz\n")
                output.append("-------------------------\n")
            }

            statusText.text = "${networks.size} NETWORK(S) FOUND"
            resultsText.text = output.toString()

        } catch (e: SecurityException) {
            statusText.text = "SCAN PERMISSION ERROR"
            resultsText.text = "Check app permissions."
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

        if (requestCode == permissionCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startWifiScan()
        } else {
            statusText.text = "PERMISSION DENIED"
            resultsText.text = "Location permission is required for Wi-Fi scanning."
        }
    }

    override fun onResume() {
        super.onResume()
        if (::connectivityManager.isInitialized) {
            showConnectionStatus()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wifiReceiver)
    }
}
