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
        // Fixed by the app already on Google Play: never change it. The code package
        // (namespace above) is unrelated and keeps its shorter name.
        applicationId = "com.app.whereismybeluga"
        minSdk = 28
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
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
    bundle {
        // The language can be picked in the app: ship every language to every phone,
        // not only the phone's own (Google Play's default split).
        language {
            enableSplit = false
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
    testOptions {
        // Needed by AGP's Compose Preview tasks: see androidComponents below.
        unitTests.isIncludeAndroidResources = true
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

// AGP registers a Compose Preview task (generate<Variant>ComposePreviewRunfiles) for
// every variant when Compose is on, with no switch to turn it off. Creating it fails
// unless the variant has unit tests that include Android resources, which breaks
// `./gradlew tasks` (it creates every task to list them). Unit tests are off for
// release by default: turn them on.
androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        it.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}
