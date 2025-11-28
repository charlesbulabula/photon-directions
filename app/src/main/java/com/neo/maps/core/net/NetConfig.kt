package com.neo.maps.core.net

/**
 * Central network configuration.
 *
 * 🔴 Replace the placeholder values with your real ones.
 */
object NetConfig {
    // Device registration endpoint (your API Gateway / backend URL)
    const val PHOTON_REGISTER_URL: String =
        "https://YOUR-REGISTER-ENDPOINT.example.com/v1/register" // TODO: replace

    // Lambda upload endpoint (API Gateway in front of Lambda)
    const val LAMBDA_UPLOAD_URL: String =
        "https://api.photo-directions.proton.me/v1/upload" // or your custom URL

    /**
     * SHA-256 fingerprint of the **leaf** TLS certificate in base16.
     *
     * Example format:
     * "12AB34CD56EF...." (no colons, uppercase or lowercase both fine if your code normalizes)
     *
     * 🔴 Replace with the real pin from your production cert.
     */
    const val PINNED_CERT_SHA256: String =
        "REPLACE_ME_WITH_REAL_CERT_SHA256"
}
