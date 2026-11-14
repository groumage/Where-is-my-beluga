import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is read from keystore.properties (never committed):
// see docs/google-play/README.md.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val hasUploadKey = keystoreProperties.containsKey("storeFile")

android {
    namespace = "com.whereismybeluga"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.whereismybeluga"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasUploadKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Canned adsb.lol data instead of the real API (see src/debug/.../LiveStatesSources.kt).
            val fakeAdsbLol = providers.gradleProperty("fakeAdsbLol").getOrElse("flying")
            buildConfigField("String", "FAKE_ADSBLOL", "\"$fakeAdsbLol\"")
        }
        release {
            // Without the upload key, sign with the debug key so the exact release
            // build can still be tested with `./gradlew :app:installRelease`.
            // Google Play rejects such builds, and bundleRelease refuses them (below).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

// The AAB is the file sent to Google Play: never build it with the debug key.
// Decided here, at configuration time: the task action only holds a String, which
// the configuration cache can store (a reference to this script it cannot).
if (!hasUploadKey) {
    tasks.matching { it.name == "bundleRelease" }.configureEach {
        val message = "keystore.properties is missing: create the upload key first (docs/google-play/README.md, step 3)."
        doFirst { throw GradleException(message) }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.maplibre)
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
    // android.jar only ships stubs of org.json: unit tests need the real one.
    testImplementation(libs.json)
}
