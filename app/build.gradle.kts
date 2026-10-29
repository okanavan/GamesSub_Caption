plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gamessub.caption"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gamessub.caption"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "edition"
    productFlavors {
        create("vipAdmin") {
            dimension = "edition"
            applicationIdSuffix = ".vip"
            versionNameSuffix = "-VIP-Admin"
            buildConfigField("boolean", "IS_VIP_ADMIN_BUILD", "true")
            buildConfigField("String", "EDITION_NAME", "\"VIP Admin Edition (Unlimited PRO)\"")
            resValue("string", "app_name", "GamesSub VIP")
        }
        create("playstore") {
            dimension = "edition"
            buildConfigField("boolean", "IS_VIP_ADMIN_BUILD", "false")
            buildConfigField("String", "EDITION_NAME", "\"Google Play Store Edition\"")
            resValue("string", "app_name", "GamesSub Caption")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // CameraX (Preview + ImageAnalysis for Previewless ROI cropping)
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Google ML Kit Text Recognition v2 (Multi-script: Latin, Japanese, Korean, Chinese)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")

    // Google ML Kit On-Device Translation (Offline 59+ languages)
    implementation("com.google.mlkit:translate:17.0.3")

    // Coroutines & Play Services Tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // OkHttp for BYOK DeepL & Gemini AI / OpenAI REST calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // DataStore Preferences for ROI presets, API keys, Trial & Master Key persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

