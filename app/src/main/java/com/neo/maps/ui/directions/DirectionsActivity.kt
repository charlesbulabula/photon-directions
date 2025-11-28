package com.neo.maps.ui.directions

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neo.maps.BuildConfig
import com.neo.maps.R
import com.neo.maps.core.crypto.DeviceCertificateManager
import com.neo.maps.core.crypto.Ed25519KeyManager
import com.neo.maps.core.net.DeviceRegistrationClient
import com.neo.maps.core.remote.RemoteConfigManager
import com.neo.maps.core.settings.SettingsStore
import com.neo.maps.databinding.ActivityDirectionsBinding
import com.neo.maps.ui.directions.capture.CaptureScheduler
import com.neo.maps.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

/**
 * Écran principal :
 * - Gère la config distante (Firebase Remote Config)
 * - Prépare les clés Ed25519 et certificat mTLS
 * - Fait l’enregistrement one-shot du device
 * - Pilote le CaptureScheduler (CameraX + upload Lambda)
 */
class DirectionsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityDirectionsBinding

    private lateinit var remoteConfigManager: RemoteConfigManager
    private lateinit var ed25519KeyManager: Ed25519KeyManager
    private lateinit var deviceCertificateManager: DeviceCertificateManager
    private lateinit var registrationClient: DeviceRegistrationClient

    private var captureScheduler: CaptureScheduler? = null
    private var googleMap: GoogleMap? = null

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    private val permissionRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDirectionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request core permissions (camera & fine location) on first launch.
        if (!hasAllPermissions()) {
            requestPermissions()
        }

        // Map fragment
        val existing = supportFragmentManager.findFragmentById(com.neo.maps.R.id.map_container)
            as? SupportMapFragment
        val mapFragment = existing ?: SupportMapFragment.newInstance().also { fragment ->
            supportFragmentManager
                .beginTransaction()
                .replace(com.neo.maps.R.id.map_container, fragment)
                .commit()
        }
        mapFragment.getMapAsync(this)

        // Core managers
        remoteConfigManager = RemoteConfigManager(this)
        ed25519KeyManager = Ed25519KeyManager(this)
        deviceCertificateManager = DeviceCertificateManager()
        registrationClient = DeviceRegistrationClient(
            context = this,
            registerUrl = BuildConfig.PHOTON_REGISTER_URL,
            env = BuildConfig.PHOTON_ENV
        )

        setupUiListeners()
        initRemoteConfigAndRegistration()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCaptureInternal()
    }

    override fun onStop() {
        super.onStop()
        // Ensure capture is not running while the activity is not visible.
        stopCaptureInternal()
        binding.captureToggle.isChecked = false
    }

    override fun onResume() {
        super.onResume()
        // If permissions were revoked while app was backgrounded, gracefully stop capture.
        if (!hasAllPermissions()) {
            stopCaptureInternal()
            binding.captureToggle.isChecked = false
        }
        updateNetworkStatusIndicator()
    }

    /**
     * Listeners des boutons / switch UI.
     */
    private fun setupUiListeners() {
        // Toggle start/stop capture
        binding.captureToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.captureToggle.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                startCaptureIfAllowed()
            } else {
                binding.captureToggle.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                stopCaptureInternal()
            }
        }

        // Rafraîchissement manuel de Remote Config
        binding.btnRefreshConfig.setOnClickListener {
            lifecycleScope.launch {
                binding.captureStatus.text = "Refreshing remote config…"
                remoteConfigManager.refresh()
                val interval = remoteConfigManager.captureIntervalSeconds()
                updateIntervalLabel(interval)

                if (!remoteConfigManager.uploadsEnabled()) {
                    binding.captureStatus.text = "Uploads disabled by remote config."
                    stopCaptureInternal()
                    binding.captureToggle.isChecked = false
                } else {
                    binding.captureStatus.text = "Remote config refreshed. Uploads enabled."
                    // Si le toggle est resté ON, on relance avec le nouvel intervalle
                    if (binding.captureToggle.isChecked) {
                        restartCaptureWithNewInterval()
                    }
                }
            }
        }

        // Search button for simple marker drop in current camera position with query as title.
        binding.searchButton.setOnClickListener {
            val query = binding.searchInput.text?.toString().orEmpty().trim()
            if (query.isNotEmpty()) {
                searchLocation(query)
            }
        }
    }

    /**
     * Synchronise la Remote Config + prépare les clés + enregistre le device.
     */
    private fun initRemoteConfigAndRegistration() {
        lifecycleScope.launch {
            binding.captureStatus.text = "Syncing remote config…"
            remoteConfigManager.refresh()

            val interval = remoteConfigManager.captureIntervalSeconds()
            updateIntervalLabel(interval)

            // Clés Ed25519
            ed25519KeyManager.getOrCreateKeyPair()
            val edPub = ed25519KeyManager.publicKeyBytes()

            // Certificat mTLS
            val privateEntry = deviceCertificateManager.getPrivateKeyEntry()
            val mtlsCertDer = privateEntry?.certificate?.encoded

            // Enregistrement best-effort
            registrationClient.registerIfNeeded(edPub, mtlsCertDer)

            if (remoteConfigManager.uploadsEnabled()) {
                binding.captureStatus.text = "Uploads enabled – ready."
            } else {
                binding.captureStatus.text = "Uploads currently disabled by remote config."
            }
        }
    }

    /**
     * Vérifie le drapeau Remote Config et démarre la capture si autorisée.
     */
    private fun startCaptureIfAllowed() {
        if (!hasAllPermissions()) {
            showPermissionExplanation()
            return
        }

        if (!isNetworkAvailable()) {
            showSimpleDialog(
                title = getString(R.string.error_title),
                message = getString(R.string.network_unavailable)
            )
            binding.captureToggle.isChecked = false
            return
        }

        if (!remoteConfigManager.uploadsEnabled()) {
            toastShort("Uploads are disabled by remote config.")
            binding.captureStatus.text = "Uploads disabled (remote config)."
            binding.captureToggle.isChecked = false
            return
        }

        val intervalSeconds = remoteConfigManager.captureIntervalSeconds()
        updateIntervalLabel(intervalSeconds)

        if (captureScheduler == null) {
            captureScheduler = buildCaptureScheduler(intervalSeconds)
        }

        captureScheduler?.start()
        binding.captureStatus.text = "Capture running every $intervalSeconds s."
    }

    private fun restartCaptureWithNewInterval() {
        stopCaptureInternal()
        val intervalSeconds = remoteConfigManager.captureIntervalSeconds()
        updateIntervalLabel(intervalSeconds)
        captureScheduler = buildCaptureScheduler(intervalSeconds)
        captureScheduler?.start()
        binding.captureStatus.text = "Capture restarted every $intervalSeconds s."
    }

    private fun stopCaptureInternal() {
        captureScheduler?.stop()
        captureScheduler = null
        binding.captureStatus.text = "Capture stopped."
    }

    /**
     * Construit le CaptureScheduler branché sur la Preview Camera + callbacks de statut.
     */
    private fun buildCaptureScheduler(intervalSeconds: Long): CaptureScheduler {
        val previewView: PreviewView? = binding.cameraPreview

        return CaptureScheduler(
            activity = this,
            intervalSeconds = intervalSeconds,
            previewView = previewView,
            onStatusChange = { statusText ->
                runOnUiThread {
                    binding.captureStatus.text = statusText
                }
            },
            onError = { msg ->
                runOnUiThread {
                    binding.captureStatus.text = "Error – capture stopped."
                    showErrorDialog(msg)
                    binding.captureToggle.isChecked = false
                }
            },
            onUploadProgress = { progress ->
                runOnUiThread {
                    if (progress in 0..99) {
                        binding.uploadProgress.visibility = View.VISIBLE
                        binding.uploadProgress.isIndeterminate = false
                        binding.uploadProgress.progress = progress
                    } else {
                        binding.uploadProgress.visibility = View.GONE
                    }
                }
            }
        )
    }

    private fun updateIntervalLabel(intervalSeconds: Long) {
        binding.intervalValue.text = "$intervalSeconds s"
    }

    private fun hasAllPermissions(): Boolean =
        requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            requiredPermissions,
            permissionRequestCode
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            if (hasAllPermissions()) {
                startCaptureIfAllowed()
            } else {
                toastShort(getString(R.string.permissions_required))
                binding.captureToggle.isChecked = false
            }
        }
    }

    private fun showPermissionExplanation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.permissions_required))
            .setMessage(
                getString(R.string.permission_camera_rationale) + "\n\n" +
                    getString(R.string.permission_location_rationale)
            )
            .setPositiveButton(android.R.string.ok) { _, _ ->
                requestPermissions()
            }
            .setNegativeButton(R.string.error_dismiss) { _, _ ->
                binding.captureToggle.isChecked = false
            }
            .show()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(nw) ?: return false
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) return false

        val wifiOnly = SettingsStore.isWifiOnlyUploads(this)
        return if (!wifiOnly) {
            true
        } else {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
    }

    private fun showSimpleDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun updateNetworkStatusIndicator() {
        val online = isNetworkAvailable()
        if (online) {
            // Hide any offline indicator
            // (you can extend this to show Wi‑Fi vs cellular if desired).
        } else {
            binding.captureStatus.text = getString(R.string.network_unavailable)
        }
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.error_title))
            .setMessage(message)
            .setPositiveButton(R.string.error_retry) { _, _ ->
                startCaptureIfAllowed()
            }
            .setNegativeButton(R.string.error_dismiss) { _, _ ->
                binding.captureToggle.isChecked = false
            }
            .show()
    }

    // ---- Google Maps ----
    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isCompassEnabled = true
            uiSettings.isMyLocationButtonEnabled = true
        }
    }

    private fun searchLocation(query: String) {
        val map = googleMap ?: return
        val target = map.cameraPosition.target
        map.addMarker(MarkerOptions().position(target).title(query))
        map.animateCamera(CameraUpdateFactory.newLatLng(target))
    }

    // ---- App bar menu / settings ----
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_directions, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(SettingsActivity.newIntent(this))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toastShort(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
