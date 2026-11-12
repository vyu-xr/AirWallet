/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val localProperties = Properties().apply {
  val localPropsFile = rootProject.file("local.properties")
  if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { load(it) }
  }
}
val geminiApiKey: String = (localProperties.getProperty("GEMINI_API_KEY")
    ?: System.getenv("GEMINI_API_KEY")
    ?: "").trim().removeSurrounding("\"")

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.compose.compiler)
}

layout.buildDirectory.set(file("${projectDir}/build.nosync"))

android {
  namespace = "com.meta.wearable.dat.externalsampleapps.cameraaccess"
  compileSdk = 36

  buildFeatures { buildConfig = true }

  androidResources {
    noCompress += "tflite"
  }

  defaultConfig {
    applicationId = "com.meta.wearable.dat.externalsampleapps.cameraaccess"
    minSdk = 31
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Meta Wearables Device Access Toolkit Setup
    // Without Developer Mode, these values need to be set with credentials from the app registered
    // in Wearables Developer Center
    manifestPlaceholders["mwdat_application_id"] = "2624968554607531"
    manifestPlaceholders["mwdat_client_token"] = "AR|2624968554607531|cdc91fb1e0eccde8586eeb106f2ddfc8"

    buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")

    ndk {
      abiFilters += listOf("arm64-v8a")
    }
  }

  buildTypes {
    debug {
      signingConfig = signingConfigs.getByName("debug")
    }
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
  signingConfigs {
    getByName("debug") {
      storeFile = file("sample.keystore")
      storePassword = "sample"
      keyAlias = "sample"
      keyPassword = "sample"
    }
  }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

dependencies {
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.exifinterface)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.material.icons.extended)
  implementation(libs.androidx.material3)
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.mwdat.core)
  implementation(libs.mwdat.camera)
  implementation(libs.mwdat.display)
  implementation(libs.mwdat.speech)
  implementation(libs.mwdat.mockdevice)
  implementation("com.google.zxing:core:3.5.3")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")

  // CameraX + ML Kit Barcode Scanning (sub-50ms QR engine)
  val cameraxVersion = "1.4.0"
  implementation("androidx.camera:camera-core:$cameraxVersion")
  implementation("androidx.camera:camera-camera2:$cameraxVersion")
  implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
  implementation("androidx.camera:camera-view:$cameraxVersion")
  implementation("androidx.camera:camera-mlkit-vision:$cameraxVersion")
  implementation("com.google.mlkit:barcode-scanning:17.3.0")

  // LiteRT (TensorFlow Lite) Support Library
  implementation("org.tensorflow:tensorflow-lite:2.14.0")
  implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
  androidTestImplementation(libs.androidx.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.uiautomator)
  androidTestImplementation(libs.androidx.test.rules)
}
