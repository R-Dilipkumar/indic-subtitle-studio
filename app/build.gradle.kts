import java.net.URI
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

// Release signing details live in keystore.properties (not committed). Without it,
// release builds are simply unsigned, so fresh clones still build.
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply {
  if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}

// sherpa-onnx (offline speech engine) is not on Maven Central, so the official AAR is fetched
// from its GitHub release on the first build. The "static-link" build has ONNX Runtime baked in,
// so it does not clash with the ONNX Runtime library the translator uses.
val sherpaVersion = "1.13.8"
val sherpaAar = file("libs/sherpa-onnx-static-link-onnxruntime-$sherpaVersion.aar")
val fetchSherpaOnnx by tasks.registering {
  val target = sherpaAar
  val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/" +
    "sherpa-onnx-static-link-onnxruntime-$sherpaVersion.aar"
  outputs.file(target)
  onlyIf { !target.exists() }
  doLast {
    target.parentFile.mkdirs()
    val tmp = File(target.path + ".part")
    URI(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
    tmp.renameTo(target)
  }
}
tasks.named("preBuild") { dependsOn(fetchSherpaOnnx) }

android {
  namespace = "io.github.rdilipkumar.subtitlestudio"
  compileSdk { version = release(36) }

  defaultConfig {
    applicationId = "io.github.rdilipkumar.subtitlestudio"
    // 29+: built-in MediaStore saving with no storage permission, and modern MediaCodec.
    minSdk = 29
    targetSdk = 36
    versionCode = 2
    versionName = "0.2.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      // x86 (32-bit) is left out: its sherpa-onnx build ships a separate ONNX Runtime
      // that would clash with the translator's copy. No phone needs it.
      abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
    }
  }

  splits {
    abi {
      // Per-phone-type APKs for releases (much smaller), plus one universal APK.
      isEnable = true
      reset()
      include("arm64-v8a", "armeabi-v7a", "x86_64")
      isUniversalApk = true
    }
  }

  packaging {
    jniLibs {
      useLegacyPackaging = true
      // 32-bit x86 is not shipped (see abiFilters); drop it before the two ONNX Runtimes meet.
      excludes += setOf("lib/x86/*.so")
    }
  }

  signingConfigs {
    if (keystoreFile.exists()) {
      create("release") {
        storeFile = rootProject.file(keystore.getProperty("storeFile"))
        storePassword = keystore.getProperty("storePassword")
        keyAlias = keystore.getProperty("keyAlias")
        keyPassword = keystore.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfigs.findByName("release")?.let { signingConfig = it }
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

  androidResources {
    // Model files must stay uncompressed so they can be memory-mapped.
    noCompress += listOf("onnx")
  }
}

dependencies {
  implementation(files("libs/sherpa-onnx-static-link-onnxruntime-$sherpaVersion.aar"))
  implementation(libs.onnxruntime.android)

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui)
  implementation(libs.media3.transformer)
  implementation(libs.media3.effect)
  implementation(libs.media3.common)

  testImplementation(libs.junit)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
