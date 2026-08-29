import java.util.Properties
import java.io.FileInputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val appVersion: String = file("${rootDir}/../VERSION").readText().trim()

val (versionMajor, versionMinor, versionPatch) = appVersion.split(".").let {
    Triple(
        it.getOrNull(0)?.toIntOrNull() ?: 0,
        it.getOrNull(1)?.toIntOrNull() ?: 0,
        it.getOrNull(2)?.toIntOrNull() ?: 0
    )
}
val versionCodeValue = versionMajor * 10000 + versionMinor * 100 + versionPatch

// ---- Release signing ----------------------------------------------------
// Read signing config from local.properties (which is .gitignored) so
// individual developers can override the password if they choose to.
// We also support reading the same values from environment variables
// for CI builds.
//
// The keystore file (android/keystore/speedshare-release.jks) IS
// committed to the repo so that every contributor produces a build
// signed with the same key. The password is the project default and
// is documented in android/local.properties.
fun loadSigningProperties(): Properties {
    val props = Properties()
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        FileInputStream(localFile).use { props.load(it) }
    }
    // Allow env var override for CI
    System.getenv("SPEEDSHARE_RELEASE_STORE_FILE")?.let { props.setProperty("SPEEDSHARE_RELEASE_STORE_FILE", it) }
    System.getenv("SPEEDSHARE_RELEASE_STORE_PASSWORD")?.let { props.setProperty("SPEEDSHARE_RELEASE_STORE_PASSWORD", it) }
    System.getenv("SPEEDSHARE_RELEASE_KEY_ALIAS")?.let { props.setProperty("SPEEDSHARE_RELEASE_KEY_ALIAS", it) }
    System.getenv("SPEEDSHARE_RELEASE_KEY_PASSWORD")?.let { props.setProperty("SPEEDSHARE_RELEASE_KEY_PASSWORD", it) }
    return props
}

val signingProps = loadSigningProperties()

fun signingOrNull(key: String, default: String? = null): String? =
    signingProps.getProperty(key) ?: System.getenv(key) ?: default

android {
    namespace = "com.example.speedshareandroid"
    compileSdk = 36
    defaultConfig {
        // NOTE: applicationId is intentionally kept at
        // com.example.speedshareandroid so that existing v1.0.0 / v1.1.0 /
        // v1.1.1 users (who were installed under this package identity)
        // can upgrade in place. The "com.example." prefix is a heuristic
        // risk for Play Protect, but a package rename would break every
        // existing install (different package = new install + data loss).
        // Mitigations for the prefix signal are applied elsewhere
        // (proper release signing, sha256 verification of the update
        // payload, proper installer intent extras).
        applicationId = "com.example.speedshareandroid"
        minSdk = 24
        targetSdk = 36
        versionCode = versionCodeValue
        versionName = appVersion
    }

    signingConfigs {
        create("release") {
            val storeFilePath = signingOrNull("SPEEDSHARE_RELEASE_STORE_FILE", "keystore/speedshare-release.jks")
            storeFile = rootProject.file(storeFilePath!!)
            storePassword = signingOrNull("SPEEDSHARE_RELEASE_STORE_PASSWORD")
            keyAlias = signingOrNull("SPEEDSHARE_RELEASE_KEY_ALIAS", "speedshare")
            keyPassword = signingOrNull("SPEEDSHARE_RELEASE_KEY_PASSWORD")
            // Required for the APK to be installable on Android 9+ and
            // recommended for App Bundle signing. Lets us use the same
            // signing key for the v2/v3 schemes.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
        // Debug signing config: only present in debug builds so the
        // default debug keystore continues to work for local dev.
        getByName("debug") {
            // Use Android's standard debug keystore
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-core")
  implementation("androidx.compose.material:material-icons-extended")

  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}
