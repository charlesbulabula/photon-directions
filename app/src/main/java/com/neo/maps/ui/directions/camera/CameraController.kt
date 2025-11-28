package com.neo.maps.ui.directions.camera

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.location.Location
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Handles CameraX setup and JPEG frame capture.
 */
class CameraController(
    private val activity: FragmentActivity,
    private val previewView: PreviewView?
) {

    private var imageCapture: ImageCapture? = null
    private var lastLocation: Location? = null

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(activity)
    }

    private val mainExecutor: Executor by lazy {
        ContextCompat.getMainExecutor(activity)
    }

    suspend fun startCamera() {
        val cameraProvider = ProcessCameraProvider.getInstance(activity).get()

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView?.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(CAPTURE_MODE_MINIMIZE_LATENCY)
            // Use the PreviewView display rotation when available to avoid deprecated APIs
            .setTargetRotation(previewView?.display?.rotation ?: Surface.ROTATION_0)
            .setBufferFormat(ImageFormat.JPEG)
            .build()

        val selector = CameraSelector.DEFAULT_BACK_CAMERA

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            activity,
            selector,
            preview,
            imageCapture
        )

        fetchLastLocation()
    }

    fun stopCamera() {
        runCatching {
            val provider = ProcessCameraProvider.getInstance(activity).get()
            provider.unbindAll()
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLastLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { loc ->
                lastLocation = loc
            }
            .addOnFailureListener {
                Log.w("CameraController", "Unable to get location: ${it.message}")
            }
    }

    fun lastKnownLocation(): Location? = lastLocation

    suspend fun captureJpegFrame(): ByteArray =
        suspendCancellableCoroutine { cont ->
            val capture = imageCapture
            if (capture == null) {
                cont.resumeWithException(IllegalStateException("ImageCapture not started"))
                return@suspendCancellableCoroutine
            }

            capture.takePicture(
                mainExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        try {
                            val buffer = imageProxy.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            imageProxy.close()
                            cont.resume(bytes)
                        } catch (e: Exception) {
                            imageProxy.close()
                            cont.resumeWithException(e)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        cont.resumeWithException(exception)
                    }
                }
            )
        }
}
