plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.fadysaeed.ketaby"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fadysaeed.ketaby"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "0.5.6"
    }

    // A fixed signing key lets each new APK install over the old one.
    // CI writes it from the KETABY_KEYSTORE_B64 secret; without the secret,
    // builds fall back to the runner's throwaway debug key.
    val ks = System.getenv("KETABY_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (ks != null) create("ketaby") {
            storeFile = ks
            storePassword = System.getenv("KETABY_KEY_PASS")
            keyAlias = "ketaby"
            keyPassword = System.getenv("KETABY_KEY_PASS")
        }
    }

    buildTypes {
        debug {
            if (ks != null) signingConfig = signingConfigs.getByName("ketaby")
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (ks != null) signingConfigs.getByName("ketaby") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
