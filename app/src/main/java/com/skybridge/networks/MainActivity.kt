package com.skybridge.networks
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var statusText: TextView
    private lateinit var resultsText: TextView
    private lateinit var scanButton: Button
    private lateinit var speedButton: Button
    private lateinit var speedText: TextView
    private lateinit var historyText: TextView

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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 35, 35, 35)
            setBackgroundColor(android.graphics.Color.rgb(7, 19, 33))
        }

        fun addText(value: String, size: Float, color: Int): TextView {
            return TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                setPadding(0, 15, 0, 15)
                root.addView(this)
            }
        }

        val blue = android.graphics.Color.rgb(85, 214, 255)
        val white = android.graphics.Color.WHITE

        addText("🌐 SKYBRIDGE NETWORKS", 24f, blue)
        addText("INTERNET CONNECTION MONITOR", 14f, white)

        statusText = addText("CHECKING CONNECTION...", 18f, blue)
        resultsText = addText("Checking active network...", 15f, white)

        speedButton = Button(this).apply {
            text = "START INTERNET SPEED TEST"
            root.addView(this)
        }

        speedText = addText(
            "Download: Not tested\nUpload: Not tested\nLatency: Not tested",
            16f,
            white
        )
historyText = addText(
    "SPEED TEST HISTORY\nNo tests saved yet.",
    14f,
    white
)
        scanButton = Button(this).apply {
            text = "SCAN NEARBY WI-FI"
            root.addView(this)
        }

        addText("NEARBY WI-FI NETWORKS", 17f, blue)

        setContentView(root)

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
            registerReceiver(wifiReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wifiReceiver, filter)
        }

        speedButton.setOnClickListener {
            runSpeedTest()
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
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {

            statusText.text = "INTERNET NOT VERIFIED"
            resultsText.text = "No validated internet connection detected."
            return
        }

        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                statusText.text = "MOBILE DATA CONNECTED"
                resultsText.text =
                    "Provider profile: Smart (selected)\n" +
                    "Connection: Cellular\nInternet: Verified"
            }

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                statusText.text = "WI-FI CONNECTED"
                resultsText.text = "Connection: Wi-Fi\nInternet: Verified"
            }

            else -> {
                statusText.text = "NETWORK CONNECTED"
                resultsText.text = "Internet connection verified."
            }
        }
    }

    private fun runSpeedTest() {
        speedButton.isEnabled = false
        speedText.text = "Testing download, upload and latency..."

        thread {
            var downloadMbps: Double? = null
            var uploadMbps: Double? = null
            var latencyMs: Long? = null
            val errors = mutableListOf<String>()

            // Latency / HTTP response time
            try {
                val connection = URL(
                    "https://www.gstatic.com/generate_204"
                ).openConnection() as HttpURLConnection

                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val start = System.nanoTime()
                val responseCode = connection.responseCode
                latencyMs = (System.nanoTime() - start) / 1_000_000

                connection.disconnect()

                if (responseCode !in 200..299) {
                    errors.add("Latency server returned HTTP $responseCode")
                }
            } catch (e: Exception) {
                errors.add("Latency test failed")
            }

            // Download test: approximately 5 MB
            try {
                val connection = URL(
                    "https://speed.cloudflare.com/__down?bytes=5000000"
                ).openConnection() as HttpURLConnection

                connection.connectTimeout = 15000
                connection.readTimeout = 20000

                val start = System.nanoTime()
                var totalBytes = 0L
                val buffer = ByteArray(8192)

                connection.inputStream.use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        totalBytes += count
                    }
                }

                val elapsed =
                    (System.nanoTime() - start) / 1_000_000_000.0

                connection.disconnect()

                if (elapsed > 0 && totalBytes > 0) {
                    downloadMbps =
                        totalBytes * 8.0 / elapsed / 1_000_000.0
                } else {
                    errors.add("No download data received")
                }
            } catch (e: Exception) {
                errors.add("Download test failed")
            }

            // Upload test: approximately 1 MB
            try {
                val payload = ByteArray(1_000_000) {
                    (it % 251).toByte()
                }

                val connection = URL(
                    "https://speed.cloudflare.com/__up"
                ).openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.setRequestProperty(
                    "Content-Type",
                    "application/octet-stream"
                )
                connection.setFixedLengthStreamingMode(payload.size)

                val start = System.nanoTime()

                connection.outputStream.use { output ->
                    output.write(payload)
                    output.flush()
                }

                val elapsed =
                    (System.nanoTime() - start) / 1_000_000_000.0

                val responseCode = connection.responseCode
                connection.disconnect()

                if (responseCode in 200..299 && elapsed > 0) {
                    uploadMbps =
                        payload.size * 8.0 / elapsed / 1_000_000.0
                } else {
                    errors.add("Upload server returned HTTP $responseCode")
                }
            } catch (e: Exception) {
                errors.add("Upload test failed")
            }

            runOnUiThread {
                speedButton.isEnabled = true
saveSpeedTest(downloadMbps, uploadMbps, latencyMs)
showSpeedHistory()
                speedText.text =
                    "Download: " +
                    (downloadMbps?.let { "%.2f Mbps".format(it) } ?: "Failed") +
                    "\nUpload: " +
                    (uploadMbps?.let { "%.2f Mbps".format(it) } ?: "Failed") +
                    "\nLatency: " +
                    (latencyMs?.let { "$it ms" } ?: "Failed") +
                    "\n\nData used: approximately 6 MB"

                if (errors.isNotEmpty()) {
                    Toast.makeText(
                        this,
                        errors.joinToString("\n"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
private fun saveSpeedTest(
    download: Double?,
    upload: Double?,
    latency: Long?
) {
    val prefs = getSharedPreferences("speed_history", MODE_PRIVATE)

    val time = SimpleDateFormat(
        "MMM dd, HH:mm",
        Locale.getDefault()
    ).format(Date())

    val record = "$time | Down: ${download?.let { "%.2f".format(it) } ?: "Failed"} Mbps" +
        " | Up: ${upload?.let { "%.2f".format(it) } ?: "Failed"} Mbps" +
        " | Latency: ${latency?.let { "$it ms" } ?: "Failed"}"

    val oldHistory = prefs.getString("records", "") ?: ""

    val updated = (oldHistory.lines().filter { it.isNotBlank() } + record)
        .takeLast(10)
        .joinToString("\n")

    prefs.edit().putString("records", updated).apply()
}

private fun showSpeedHistory() {
    val prefs = getSharedPreferences("speed_history", MODE_PRIVATE)
    val history = prefs.getString("records", "") ?: ""

    historyText.text = if (history.isBlank()) {
        "SPEED TEST HISTORY\nNo tests saved yet."
    } else {
        "SPEED TEST HISTORY\n\n$history"
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
            statusText.text = "SCANNING WI-FI..."
            resultsText.text = "Searching for nearby networks..."

            if (!wifiManager.startScan()) {
                statusText.text = "SCAN NOT STARTED"
                resultsText.text = "Android may be limiting Wi-Fi scans."
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
            resultsText.text = "Location permission is required."
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

