import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.neo.maps"
    compileSdk = 34

    // Load secure config (production URLs, pins, etc.) from the root-level file if present.
    val secureProps = Properties().apply {
        val secureFile = rootProject.file("secure-config.properties")
        if (secureFile.exists()) {
            secureFile.inputStream().use { input ->
                this.load(input)
            }
        }
    }

    defaultConfig {
        applicationId = "com.neo.maps"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Environment label
        val photonEnv = secureProps.getProperty("PHOTON_ENV", "prod")
        buildConfigField("String", "PHOTON_ENV", "\"$photonEnv\"")

        // Device registration backend (optional, can be overridden in secure-config.properties)
        val registerUrl =
            secureProps.getProperty("PHOTON_REGISTER_URL", "https://your-backend.example.com/register")
        buildConfigField("String", "PHOTON_REGISTER_URL", "\"$registerUrl\"")

        // Lambda / API Gateway upload endpoint – default to the production URL requested.
        val lambdaUrl = secureProps.getProperty(
            "PHOTON_LAMBDA_URL",
            "https://api.photo-directions.proton.me/v1/upload"
        )
        buildConfigField("String", "PHOTON_LAMBDA_URL", "\"$lambdaUrl\"")

        // Host used for certificate pinning.
        val lambdaHost = secureProps.getProperty(
            "PHOTON_LAMBDA_HOST",
            "api.photo-directions.proton.me"
        )
        buildConfigField("String", "PHOTON_LAMBDA_HOST", "\"$lambdaHost\"")

        // SHA-256 pin of the Lambda/API cert in the OkHttp format: "sha256/BASE64_PIN"
        // This *must* be overridden in secure-config.properties for real production builds.
        val certPin = secureProps.getProperty(
            "PHOTON_CERT_PIN",
            "sha256/REPLACE_WITH_REAL_CERT_PIN"
        )
        buildConfigField("String", "PHOTON_CERT_PIN", "\"$certPin\"")
    }

    // ✅ Aligner Java sur 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        viewBinding = true      // pour Activity*Binding
        buildConfig = true      // pour BuildConfig
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // ---- Jetpack Compose ----
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material:material-icons-extended")

    // ---- ViewBinding / AppCompat ----
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // ---- AndroidX Security (EncryptedSharedPreferences) ----
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")

    // ---- BouncyCastle (Ed25519 etc.) ----
    implementation("org.bouncycastle:bcprov-jdk15to18:1.78.1")

    // ---- OkHttp ----
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ---- Firebase Remote Config + Analytics ----
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // ---- CameraX ----
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // ---- Google Location Services ----
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // ---- Google Maps ----
    implementation("com.google.android.gms:play-services-maps:18.2.0")

    // ---- Coroutines ----
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
