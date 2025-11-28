package com.neo.maps.ui.directions.capture

import androidx.camera.view.PreviewView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.neo.maps.core.crypto.Ed25519Signer
import com.neo.maps.core.metadata.MetadataBuilder
import com.neo.maps.core.metrics.UploadHistoryStore
import com.neo.maps.core.net.LambdaUploadClient
import com.neo.maps.service.CaptureForegroundService
import com.neo.maps.ui.directions.camera.CameraController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Orchestration de la capture périodique :
 * - démarre/arrête la caméra (CameraController)
 * - construit les métadonnées
 * - signe les métadonnées
 * - envoie à Lambda via LambdaUploadClient
 */
class CaptureScheduler(
    private val activity: FragmentActivity,
    private val intervalSeconds: Long,
    private val previewView: PreviewView?,
    private val onStatusChange: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onUploadProgress: (Int) -> Unit
) {

    private val cameraController = CameraController(
        activity = activity,
        previewView = previewView
    )

    private val signer = Ed25519Signer(activity)
    private val uploader = LambdaUploadClient(activity)

    private var job: Job? = null

    fun start() {
        if (job != null) return

        job = activity.lifecycleScope.launch {
            try {
                cameraController.startCamera()
                onStatusChange("Capture running…")
                CaptureForegroundService.start(
                    activity,
                    status = "Secure capture running every $intervalSeconds s"
                )
            } catch (e: Exception) {
                onError("Unable to start camera: ${e.message}")
                return@launch
            }

            while (true) {
                var jpegBytes: ByteArray? = null
                try {
                    // 1) Capture JPEG brut
                    jpegBytes = cameraController.captureJpegFrame()

                    // 2) GPS (si dispo)
                    val gps = cameraController.lastKnownLocation()

                    var attempt = 0
                    val maxAttempts = 3
                    var lastResult: com.neo.maps.core.net.LambdaUploadResult? = null

                    // 3) Boucle de tentative avec renouvellement du nonce en cas de 409 (replay)
                    while (attempt < maxAttempts) {
                        attempt++

                        val metadataJsonString: String = MetadataBuilder.buildMetadataJson(
                            gps = gps,
                            advertiserId = null // à connecter plus tard si besoin
                        )

                        val metadataBytes = metadataJsonString.toByteArray()

                        // 4) Signature Ed25519 (Base64) sur le JSON
                        val signatureBase64: String = signer.sign(metadataBytes)

                        // Efface le buffer temporaire de métadonnées
                        metadataBytes.fill(0)

                        // 5) Upload vers Lambda + S3 avec suivi de progression
                        onStatusChange("Uploading frame…")
                        onUploadProgress(0)

                        val result = uploader.uploadFrame(
                            frameBytes = jpegBytes,
                            metadataJsonString = metadataJsonString,
                            signatureBase64 = signatureBase64,
                            onProgress = { pct -> onUploadProgress(pct) }
                        )

                        lastResult = result
                        UploadHistoryStore.record(activity, result)

                        if (result.success) {
                            onStatusChange("Last upload: OK (code ${result.code})")
                            onUploadProgress(100)
                            break
                        }

                        if (result.code == 409) {
                            onStatusChange("Nonce conflict (409), retrying… ($attempt/$maxAttempts)")
                            continue
                        }

                        // Advanced error categorisation + exponential backoff for retries
                        val category = when {
                            result.code == 401 || result.code == 403 -> "security"
                            result.code in 500..599 -> "server"
                            else -> "network"
                        }

                        if (category == "security") {
                            onError("Security error: HTTP ${result.code}")
                            stop()
                            break
                        } else {
                            val backoffMs = (1000.0 * 2.0.pow((attempt - 1).toDouble()))
                                .toLong()
                                .coerceAtMost(10_000L)
                            onStatusChange(
                                "Upload failed (HTTP ${result.code}) – retrying in ${backoffMs / 1000}s…"
                            )
                            kotlinx.coroutines.delay(backoffMs)
                            continue
                        }
                    }

                    if (lastResult?.success != true) {
                        // Si après les tentatives la requête n'est toujours pas un succès, on sort.
                        break
                    }
                } catch (ex: Exception) {
                    onError("Error: ${ex.message}")
                    stop()
                    break
                } finally {
                    // Nettoyage de la mémoire brute image
                    jpegBytes?.fill(0)
                    onUploadProgress(0)
                }

                delay(intervalSeconds * 1000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        cameraController.stopCamera()
        CaptureForegroundService.stop(activity)
    }
}
