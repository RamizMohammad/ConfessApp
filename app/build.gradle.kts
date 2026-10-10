import java.io.FileInputStream
import java.net.URI
import java.util.Properties

// ── Build-time config from secrets.properties (git-ignored) ─────────────────
// Only values that are SAFE to ship inside the APK belong here. Server-side
// secrets (Telegram bot token, Cloudinary secret, …) must never be added.
val secretProperties = Properties()
val secretPropertiesFile = rootProject.file("secrets.properties")
if (secretPropertiesFile.exists()) {
    FileInputStream(secretPropertiesFile).use { secretProperties.load(it) }
} else {
    println("⚠️ Warning: secrets.properties file not found!")
}

fun secret(key: String): String = secretProperties.getProperty(key, "").trim()

if (secret("CERT_PINS").isEmpty()) {
    println("⚠️ Warning: CERT_PINS is empty — release builds will NOT pin the API certificate.")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "in.mohammad.ramiz.confess"
    compileSdk = 36

    defaultConfig {
        applicationId = "in.mohammad.ramiz.confess"
        minSdk = 31
        targetSdk = 36
        versionCode = 18
        versionName = "5.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google Sign-In (OAuth Web client id — the server verifies tokens against it)
        buildConfigField("String", "WEB_CLIENT_ID", "\"${secret("WEB_CLIENT_ID")}\"")
        // v3 server (Postgres). Must end with "/". Routes live under api/v2/.
        buildConfigField("String", "SERVER_URL", "\"${secret("SERVER_URL")}\"")
        // Reset-email links (https://<server>/reset-password) open in the app.
        manifestPlaceholders["serverHost"] = URI(secret("SERVER_URL").ifEmpty { "https://localhost/" }).host ?: "localhost"
        buildConfigField("String", "BACKUP_URL", "\"${secret("BACKUP_URL")}\"")
        // Sent as x-api-key. Identifies the app; it is NOT a secret (it ships in the APK).
        buildConfigField("String", "CLIENT_API", "\"${secret("CLIENT_API")}\"")

        // ── Security (see SECURITY.md) ──────────────────────────────────────
        // TLS certificate pins for SERVER_URL / BACKUP_URL: "sha256/…=,sha256/…=" (≥ 2, one backup).
        buildConfigField("String", "CERT_PINS", "\"${secret("CERT_PINS")}\"")
        // Debug builds only: skip pinning so you can inspect your own traffic with a proxy.
        buildConfigField("boolean", "DEBUG_ALLOW_PROXY", secret("DEBUG_ALLOW_PROXY").ifEmpty { "false" })
        // Google Cloud project number with the Play Integrity API enabled (0 = off).
        buildConfigField("long", "CLOUD_PROJECT_NUMBER", "${secret("CLOUD_PROJECT_NUMBER").ifEmpty { "0" }}L")
        // SHA-256 of the certificate(s) allowed to sign this app (Play app-signing key, upload key).
        buildConfigField("String", "SIGNING_CERT_SHA256", "\"${secret("SIGNING_CERT_SHA256")}\"")
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // AndroidX + Material
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.core.splashscreen)
    implementation(libs.recyclerview)
    implementation(libs.androidx.browser)
    implementation(libs.swiperefreshlayout)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.material)
    implementation(libs.play.integrity)

    // Sign-in + unlock
    implementation(libs.credentials.core)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.biometric)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)   // attached only in debug builds (ApiClient)

    // Images / loading skeletons
    implementation(libs.glide.core)
    annotationProcessor(libs.glide.compiler)
    implementation(libs.shimmer)

    // Push (Firebase is used ONLY for FCM)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
