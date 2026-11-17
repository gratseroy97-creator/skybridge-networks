
package com.skybridge.networks

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var wifiManager: WifiManager
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
            startWifiScan()
        }
    }

    private fun startWifiScan() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                permissionCode
            )
            return
        }

        val locationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (Build.VERSION.SDK_INT >= 28 &&
            !locationManager.isLocationEnabled) {

            statusText.text = "LOCATION SERVICES REQUIRED"
            resultsText.text =
                "Please enable your phone's Location service, then scan again."
            return
        }

        if (!wifiManager.isWifiEnabled) {
            statusText.text = "WI-FI IS TURNED OFF"
            resultsText.text = "Turn on Wi-Fi and try again."
            return
        }

        try {
            statusText.text = "SCANNING..."
            resultsText.text = "Searching for nearby Wi-Fi networks..."

            val started = wifiManager.startScan()

            if (!started) {
                statusText.text = "SCAN NOT STARTED"
                resultsText.text =
                    "Android may be limiting scans. Try again later."
            }

        } catch (e: SecurityException) {
            statusText.text = "PERMISSION REQUIRED"
            resultsText.text = "Allow location permission and try again."
        }
    }

    private fun displayNetworks() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            val networks = wifiManager.scanResults

            if (networks.isEmpty()) {
                statusText.text = "NO NETWORKS FOUND"
                resultsText.text =
                    "No Wi-Fi results. Check Wi-Fi and Location settings."
                return
            }

            val output = StringBuilder()

            networks.forEachIndexed { index, network ->
                val level = WifiManager.calculateSignalLevel(
                    network.level, 100
                )

                output.append("NETWORK ${index + 1}\n")
                output.append("Name: ${network.SSID.ifBlank { "Hidden network" }}\n")
                output.append("Signal: ${network.level} dBm\n")
                output.append("Strength: $level%\n")
                output.append("Frequency: ${network.frequency} MHz\n")
                output.append("-------------------------\n")
            }

            statusText.text = "${networks.size} NETWORK(S) FOUND"
            resultsText.text = output.toString()

        } catch (e: SecurityException) {
            statusText.text = "SCAN PERMISSION ERROR"
            resultsText.text = "Please check app permissions."
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
            resultsText.text =
                "Location permission is needed to read nearby Wi-Fi scan results."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wifiReceiver)
    }
}
