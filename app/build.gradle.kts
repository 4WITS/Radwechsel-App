import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Signing-Daten liegen in keystore.properties (gitignored, separat sichern).
// Fehlt die Datei, laesst sich nur der Debug-Build erzeugen.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

// Harte Bremse: ein Release-APK mit Debug-Key oder unsigniert waere via
// Scalefusion nicht ueber die bestehende Installation installierbar
// (INSTALL_FAILED_UPDATE_INCOMPATIBLE). Bewusst in der Konfigurationsphase
// geprueft, ein doFirst-Lambda ist nicht configuration-cache-faehig.
if (!keystorePropsFile.exists() &&
    gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
) {
    throw GradleException(
        "keystore.properties fehlt - Release-Build nicht signierbar. " +
            "Benoetigt: storeFile, storePassword, keyAlias, keyPassword."
    )
}

android {
    namespace = "com.fourwheels.radwechsel"
    compileSdk = 35

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.fourwheels.radwechsel"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            buildConfigField("String", "AUTH_BASE_URL", "\"https://login-test.4wheels.de/\"")
            buildConfigField("String", "API_BASE_URL",  "\"https://api-test.4wheels.de/\"")
            buildConfigField("String", "CLIENT_ID",     "\"Wch8ngRe4dLpXq2z\"")
        }
        release {
            buildConfigField("String", "AUTH_BASE_URL", "\"https://login.4wheels.de/\"")
            buildConfigField("String", "API_BASE_URL",  "\"https://api.4wheels.de/\"")
            buildConfigField("String", "CLIENT_ID",     "\"XCVA1Q39eKj2g5UE\"")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // ── Compose BOM ───────────────────────────────────────────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Core ──────────────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    // ── Navigation ────────────────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // ── ViewModel ─────────────────────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // ── Hilt ─────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ── Retrofit ──────────────────────────────────────────────────────────────
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ── Token-Speicher (verschlüsselt, synchron – für Auto-Refresh) ────────────
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ── Tests (XlsxWriter) ────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
}
