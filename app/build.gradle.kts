import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Base64
import java.security.KeyStore

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  // Restores debug.keystore from debug.keystore.base64 dynamically if missing (e.g., in CI environments)
  val restoredDebugKeystore = project.file("${rootDir}/debug.keystore")
  if (!restoredDebugKeystore.exists()) {
    val base64Keystore = project.file("${rootDir}/debug.keystore.base64")
    if (base64Keystore.exists()) {
      try {
        val base64Content = base64Keystore.readText().trim()
        val decodedBytes = Base64.getDecoder().decode(base64Content)
        restoredDebugKeystore.writeBytes(decodedBytes)
        println("Restored debug.keystore from base64 encoding.")
      } catch (e: Exception) {
        println("Warning: Failed to decode debug.keystore.base64: ${e.message}")
      }
    }
  }

  defaultConfig {
    applicationId = "com.aistudio.tuitionmanager.kxmzpq"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
        ?: (project.findProperty("signing.storeFile") as? String)
        ?: run {
          val relKeystore = file("${rootDir}/release.keystore")
          if (relKeystore.exists()) {
            "${rootDir}/release.keystore"
          } else {
            "${rootDir}/my-upload-key.jks"
          }
        }
      val keystoreFile = file(keystorePath)
      
      val storePass = System.getenv("STORE_PASSWORD").takeIf { !it.isNullOrEmpty() }
        ?: (project.findProperty("signing.storePassword") as? String).takeIf { !it.isNullOrEmpty() }
        ?: "tuition"
      val keyAl = System.getenv("KEY_ALIAS").takeIf { !it.isNullOrEmpty() }
        ?: (project.findProperty("signing.keyAlias") as? String).takeIf { !it.isNullOrEmpty() }
        ?: "tuition"
      val keyPass = System.getenv("KEY_PASSWORD").takeIf { !it.isNullOrEmpty() }
        ?: (project.findProperty("signing.keyPassword") as? String).takeIf { !it.isNullOrEmpty() }
        ?: "tuition"

      var isKeystoreValid = false
      if (keystoreFile.exists()) {
        try {
          val keystore = KeyStore.getInstance("JKS")
          keystoreFile.inputStream().use { fis: java.io.InputStream ->
            keystore.load(fis, storePass.toCharArray())
          }
          isKeystoreValid = true
        } catch (e: Exception) {
          println("Warning: Keystore at ${keystoreFile.name} could not be loaded with storePassword: ${e.message}. Falling back to debug signing.")
        }
      }

      if (isKeystoreValid) {
        storeFile = keystoreFile
        storePassword = storePass
        keyAlias = keyAl
        keyPassword = keyPass
      } else {
        // Fallback to the debug keystore if the release keystore is not available, avoiding build crash
        val customDebugKeystore = file("${rootDir}/debug.keystore")
        val defaultDebugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
        storeFile = if (customDebugKeystore.exists()) customDebugKeystore else defaultDebugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("debugConfig") {
      val customDebugKeystore = file("${rootDir}/debug.keystore")
      val defaultDebugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
      storeFile = if (customDebugKeystore.exists()) customDebugKeystore else defaultDebugKeystore
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

googleServices {
  missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}


// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  // implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
