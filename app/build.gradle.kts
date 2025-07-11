import java.util.Properties
import java.io.FileInputStream

// Load properties safely
val secretProperties = Properties()
val secretPropertiesFile = rootProject.file("secrets.properties")

if (secretPropertiesFile.exists()) {
    FileInputStream(secretPropertiesFile).use { secretProperties.load(it) }
} else {
    println("⚠️ Warning: secrets.properties file not found!")
}

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "in.mohammad.ramiz.confess"
    compileSdk = 36

    defaultConfig {
        applicationId = "in.mohammad.ramiz.confess"
        minSdk = 31
        targetSdk = 36
        versionCode = 11
        versionName = "3.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val webClientId = secretProperties.getProperty("WEB_CLIENT_ID", "")
        val serverUrl = secretProperties.getProperty("SERVER_URL", "")
        val botToken = secretProperties.getProperty("BOT_TOKEN", "")
        val chatId = secretProperties.getProperty("CHAT_ID", "")
        val clientApi = secretProperties.getProperty("CLIENT_API", "")
        val aesMode = secretProperties.getProperty("AES_MODE", "")
        val aesKeySize = secretProperties.getProperty("AES_KEY_SIZE", "")
        val gcmIvLength = secretProperties.getProperty("GCM_IV_LENGTH", "")
        val gcmTagLength = secretProperties.getProperty("GCM_TAG_LENGTH", "")
        val hmacAlgo = secretProperties.getProperty("HMAC_ALGO", "")
        val razerKeyId = secretProperties.getProperty("RAZER_KEY_ID", "")

        buildConfigField("String", "WEB_CLIENT_ID", "\"$webClientId\"")
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        buildConfigField("String", "BOT_TOKEN", "\"$botToken\"")
        buildConfigField("String", "CHAT_ID", "\"$chatId\"")
        buildConfigField("String", "CLIENT_API", "\"$clientApi\"")
        buildConfigField("String", "AES_MODE", "\"$aesMode\"")
        buildConfigField("int", "AES_KEY_SIZE", "$aesKeySize")
        buildConfigField("int", "GCM_IV_LENGTH", "$gcmIvLength")
        buildConfigField("int", "GCM_TAG_LENGTH", "$gcmTagLength")
        buildConfigField("String", "HMAC_ALGO", "\"$hmacAlgo\"")
        buildConfigField("String", "RAZER_KEY_ID", "\"$razerKeyId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        buildFeatures{
            dataBinding = true
            buildConfig = true
            viewBinding = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_19
        targetCompatibility = JavaVersion.VERSION_19
    }
}

dependencies {
    implementation(libs.checkout)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.swiperefreshlayout)
    implementation(libs.shimmer)
    implementation(libs.firebase.messaging)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.room.ktx)
    implementation(libs.room.rxjava2)
    implementation(libs.room.rxjava3)
    implementation(libs.room.guava)
    testImplementation(libs.room.testing)
    implementation(libs.room.paging)
    implementation(libs.biometric)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.firebase.auth)
    implementation(libs.play.services.auth)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.glide)
    annotationProcessor(libs.compiler)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}