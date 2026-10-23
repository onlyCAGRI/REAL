import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

val testerUpdatesEnabled = providers.gradleProperty("REAL_TESTER_UPDATES_ENABLED").orElse("true").get().toBooleanStrict()

val appCheckEnabled = providers.gradleProperty("REAL_APP_CHECK_ENABLED").orElse("true").get().toBooleanStrict()

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}


android {
    namespace = "com.cagri.real"
    compileSdk = 37

    defaultConfig {
        buildConfigField("boolean", "REAL_TESTER_UPDATES_ENABLED", testerUpdatesEnabled.toString())
        buildConfigField("boolean", "REAL_APP_CHECK_ENABLED", appCheckEnabled.toString())
        applicationId = "com.cagri.real"
        minSdk = 26
        targetSdk = 36
        versionCode = 64
        versionName = "0.9.6"

    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Phones only: the x86 / x86_64 native libraries are for emulators (~44 MB of the APK). Debug keeps them.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("optimized") {
            initWith(getByName("release"))
            isDebuggable = false
            // R8 validation variant: keep debug signing + Debug App Check
            // so optimized remains convenient for local/device validation.
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    // Only tester builds merge the install permission. Main/store manifest has none.
    if (testerUpdatesEnabled) {
        sourceSets {
            getByName("debug").manifest.srcFile("src/debug/AndroidManifest.xml")
            getByName("release").manifest.srcFile("src/tester/AndroidManifest.xml")
            getByName("optimized").manifest.srcFile("src/tester/AndroidManifest.xml")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("17")
    }
}

dependencies {
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-appcheck")
    implementation("com.google.firebase:firebase-messaging") // Pano push
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    add("optimizedImplementation", "com.google.firebase:firebase-appcheck-debug")
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    // "Konuma varınca hatırlat": battery-friendly geofences, registered only while such a card is waiting.
    implementation("com.google.android.gms:play-services-location:21.4.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    // PDF text layer with fonts (bold / italic) on every Android version; OCR stays for scanned pages. Apache-2.0.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.mlkit:text-recognition:16.0.1") // On-device, bundled Latin OCR for scanned books.
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0") // Pano GIF decoder; same existing Coil version.
    // Temporary concept demo: extract a restrained accent from its bundled image.
    implementation("androidx.palette:palette:1.1.0")
    implementation("org.maplibre.gl:android-sdk:11.8.0")
    implementation("org.checkerframework:checker-qual:3.43.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
