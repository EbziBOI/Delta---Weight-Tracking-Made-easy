import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes a build number so every APK can install over the previous one.
val releaseVersionCode = 32
val buildNumber = maxOf(releaseVersionCode, (System.getenv("GITHUB_RUN_NUMBER") ?: releaseVersionCode.toString()).toInt())

// CI points these at a keystore. Without them (for example in Android Studio)
// the release build falls back to the local debug key.
val keystorePath: String? = System.getenv("DELTA_KEYSTORE_FILE")

android {
    namespace = "io.github.ebziboi.delta"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.ebziboi.delta"
        minSdk = 28
        targetSdk = 36
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("sideload") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("DELTA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DELTA_KEY_ALIAS") ?: "delta"
                keyPassword = System.getenv("DELTA_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) {
                signingConfigs.getByName("sideload")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // The date picker and a few other Material 3 pieces are still marked experimental.
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // Pinned by hand: the icon set stopped at 1.7.8 and does not follow the Compose BOM.
    implementation("androidx.compose.material:material-icons-core:1.7.8")

    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("androidx.health.connect:connect-client:1.1.0")

    implementation("androidx.glance:glance-appwidget:1.1.1")

    // Real backdrop blur for the floating glass bars (falls back to a tinted pane before Android 12).
    // Pinned to the last release built against Compose 1.10 and Kotlin 2.2.
    implementation("dev.chrisbanes.haze:haze:1.7.2")

    // On-device smoke test: launches the app on an emulator and photographs it.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

// Also produce a versioned local file after Android's packaging and listing tasks finish.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    doLast {
        val apkDirectory = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        apkDirectory.resolve("app-release.apk").copyTo(apkDirectory.resolve("Delta-0.1.$buildNumber.apk"), overwrite = true)
    }
}
