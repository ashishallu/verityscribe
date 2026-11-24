import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.google.services)
}

// Client configuration is read only from local, untracked configuration or process environment.
// The publishable Supabase key is designed for client apps; never add a service-role key here.
val configValues = Properties().apply {
  val backendEnvFile = rootProject.projectDir.parentFile.resolve("backend/.env").canonicalFile
  val candidates = listOf(
    rootProject.file("local.properties"),
    rootProject.file(".env"),
    backendEnvFile
  )
  println("Verity config backend env path=$backendEnvFile exists=${backendEnvFile.isFile}")
  candidates.filter { it.isFile }.forEach { file ->
    file.readLines().forEach { line ->
      val trimmed = line.trim()
      if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
        val separator = trimmed.indexOf('=')
        if (separator > 0) {
          val normalized = trimmed.removePrefix("export ").trim()
          val key = normalized.substringBefore('=').trim()
          val value = normalized.substringAfter('=').substringBefore(" #").trim().trim('"', '\'')
          if (key in setOf("SUPABASE_URL", "SUPABASE_PUBLISHABLE_KEY", "API_BASE_URL") && value.isNotEmpty()) {
            setProperty(key, value)
          }
        }
      }
    }
  }
}

println("Verity config parsed keys=${configValues.stringPropertyNames().sorted()}")
println("Verity config SUPABASE_URL=${configValues.getProperty("SUPABASE_URL", "<missing>")}")
println("Verity config API_BASE_URL=${configValues.getProperty("API_BASE_URL", "<missing; using Render fallback>")}")

fun buildConfigValue(name: String, fallback: String = ""): String =
  configValues.getProperty(name)?.takeIf { it.isNotBlank() }
    ?: providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
    ?: System.getenv(name)?.takeIf { it.isNotBlank() }
    ?: fallback

val resolvedSupabaseUrl = buildConfigValue("SUPABASE_URL")
require(resolvedSupabaseUrl.isNotBlank() && !resolvedSupabaseUrl.contains("your-project.supabase.co")) {
  "SUPABASE_URL is missing or still uses the template URL. Configure it in local.properties or backend/.env."
}
val resolvedApiBaseUrl = buildConfigValue("API_BASE_URL", "https://verityscribe-1.onrender.com/api/v1")
require(resolvedApiBaseUrl.isNotBlank() && !resolvedApiBaseUrl.contains("your-api-host")) {
  "API_BASE_URL is missing or still uses the template URL. Configure it in local.properties or backend/.env."
}

fun quotedBuildConfig(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.verity.vqrkmp"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    buildConfigField("String", "SUPABASE_URL", quotedBuildConfig(resolvedSupabaseUrl))
    buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", quotedBuildConfig(buildConfigValue("SUPABASE_PUBLISHABLE_KEY")))
    buildConfigField("String", "API_BASE_URL", quotedBuildConfig(resolvedApiBaseUrl))

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
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
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
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
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.health.connect)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
