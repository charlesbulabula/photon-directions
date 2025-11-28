package com.neo.maps.core.net

import android.content.Context
import com.neo.maps.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import org.json.JSONObject

data class LambdaUploadResult(
    val success: Boolean,
    val code: Int,
    val body: String?
)

/**
 * HTTP client responsible for:
 *  1) Calling the Lambda/API Gateway endpoint (PHOTON_LAMBDA_URL) with metadata + signature
 *  2) Parsing the pre-signed S3 URL from the Lambda response
 *  3) Uploading the JPEG bytes directly to S3 via HTTPS PUT
 */
class LambdaUploadClient(
    context: Context,
    private val client: OkHttpClient = SecureHttpClient.get(context.applicationContext),
    private val uploadUrl: String = BuildConfig.PHOTON_LAMBDA_URL
) {

    /**
     * @param frameBytes          raw JPEG bytes
     * @param metadataJsonString  metadata JSON (already serialized)
     * @param signatureBase64     Ed25519 signature over the metadata JSON, in Base64
     */
    suspend fun uploadFrame(
        frameBytes: ByteArray,
        metadataJsonString: String,
        signatureBase64: String,
        onProgress: (Int) -> Unit = {}
    ): LambdaUploadResult = withContext(Dispatchers.IO) {

        if (uploadUrl.isBlank()) {
            return@withContext LambdaUploadResult(
                success = false,
                code = -1,
                body = "Upload URL not configured"
            )
        }

        // Step 1: Call Lambda to get a pre-signed S3 URL (and optional headers)
        val lambdaPayload = JSONObject().apply {
            put("metadata", JSONObject(metadataJsonString))
            put("signature", signatureBase64)
        }

        val jsonMediaType = "application/json".toMediaType()
        val lambdaRequestBody = lambdaPayload.toString().toRequestBody(jsonMediaType)

        val lambdaRequest = Request.Builder()
            .url(uploadUrl)
            .post(lambdaRequestBody)
            .build()

        val lambdaResponse = client.newCall(lambdaRequest).execute()
        lambdaResponse.use { resp ->
            val code = resp.code
            val body = resp.body?.string()

            if (!resp.isSuccessful) {
                return@withContext LambdaUploadResult(
                    success = false,
                    code = code,
                    body = body
                )
            }

            if (body.isNullOrBlank()) {
                return@withContext LambdaUploadResult(
                    success = false,
                    code = code,
                    body = "Empty Lambda response"
                )
            }

            val json = JSONObject(body)
            val uploadUrl = json.getString("upload_url")
            val headersJson = json.optJSONObject("headers")

            // Step 2: PUT JPEG bytes to S3 using the pre-signed URL
            val jpegRequestBody = frameBytes.toRequestBody("image/jpeg".toMediaType())
            val progressBody = ProgressRequestBody(jpegRequestBody, onProgress)

            val s3RequestBuilder = Request.Builder()
                .url(uploadUrl)
                .put(progressBody)

            if (headersJson != null) {
                val keys = headersJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = headersJson.optString(key, null)
                    if (value != null) {
                        s3RequestBuilder.addHeader(key, value)
                    }
                }
            }

            val s3Request = s3RequestBuilder.build()
            val s3Response = client.newCall(s3Request).execute()
            s3Response.use { s3Resp ->
                val s3Code = s3Resp.code
                val s3Body = s3Resp.body?.string()
                onProgress(100)
                return@withContext LambdaUploadResult(
                    success = s3Resp.isSuccessful,
                    code = s3Code,
                    body = s3Body
                )
            }
        }
    }

    /**
     * Wraps a RequestBody to report upload progress as a percentage.
     */
    private class ProgressRequestBody(
        private val delegate: RequestBody,
        private val onProgress: (Int) -> Unit
    ) : RequestBody() {

        override fun contentType() = delegate.contentType()

        override fun contentLength() = delegate.contentLength()

        override fun writeTo(sink: BufferedSink) {
            val countingSink = object : ForwardingSink(sink) {
                var bytesWritten = 0L
                val totalBytes = contentLength()

                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    bytesWritten += byteCount
                    if (totalBytes > 0) {
                        val pct = ((bytesWritten * 100) / totalBytes).toInt()
                        onProgress(pct.coerceIn(0, 100))
                    }
                }
            }

            val bufferedSink = countingSink.buffer()
            delegate.writeTo(bufferedSink)
            bufferedSink.flush()
        }
    }
}

