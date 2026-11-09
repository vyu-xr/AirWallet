/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// CameraAccess Sample App - Main Activity
//
// This is the main entry point for the CameraAccess sample application that demonstrates how to use
// the Meta Wearables Device Access Toolkit (DAT) to:
// - Initialize the DAT SDK
// - Handle device permissions (Bluetooth, Internet)
// - Request camera permissions from wearable devices (Ray-Ban Meta glasses)
// - Stream video and capture photos from connected wearable devices

package com.meta.wearable.dat.externalsampleapps.cameraaccess

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.INTERNET
import android.Manifest.permission.RECORD_AUDIO
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.voiceinvocations.VoiceInvocationsStream
import com.meta.wearable.dat.core.voiceinvocations.isVoiceInvocationsIntent
import com.meta.wearable.dat.core.voiceinvocations.startVoiceInvocationsStream
import com.meta.wearable.dat.core.voiceinvocations.types.actions.LaunchApp
import com.meta.wearable.dat.core.voiceinvocations.types.actions.VoiceInvocation
import com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.CameraAccessScaffold
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainActivity : ComponentActivity() {
  companion object {
    // Required Android permissions for the DAT SDK and mobile camera/mic to function properly
    val PERMISSIONS: Array<String> = arrayOf(
        BLUETOOTH,
        BLUETOOTH_CONNECT,
        INTERNET,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
    )
  }

  val viewModel: WearablesViewModel by viewModels()

  private var voiceInvocationsStream: VoiceInvocationsStream? = null

  private val permissionCheckLauncher =
      registerForActivityResult(RequestMultiplePermissions()) { permissionsResult ->
        viewModel.onPermissionsResult(permissionsResult) {
          ensureSdkInitialized()
        }
      }

  private var permissionContinuation: CancellableContinuation<PermissionStatus>? = null
  private val permissionMutex = Mutex()
  // Requesting wearable device permissions via the Meta AI app
  private val permissionsResultLauncher =
      registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
        val permissionStatus = result.getOrDefault(PermissionStatus.Denied)
        permissionContinuation?.resume(permissionStatus)
        permissionContinuation = null
      }

  // Convenience method to make a permission request in a sequential manner
  // Uses a Mutex to ensure requests are processed one at a time, preventing race conditions
  suspend fun requestWearablesPermission(permission: Permission): PermissionStatus {
    return permissionMutex.withLock {
      suspendCancellableCoroutine { continuation ->
        permissionContinuation = continuation
        continuation.invokeOnCancellation { permissionContinuation = null }
        permissionsResultLauncher.launch(permission)
      }
    }
  }

  private var audioPermissionContinuation: CancellableContinuation<Boolean>? = null
  // Phone microphone permission, requested in context when recording with sound-in-video on.
  private val recordAudioPermissionLauncher =
      registerForActivityResult(RequestPermission()) { granted ->
        audioPermissionContinuation?.resume(granted)
        audioPermissionContinuation = null
      }

  // Requests RECORD_AUDIO for sound-in-video. Returns true if granted (already or just now); false
  // if denied, so recording can proceed video-only instead of being blocked.
  suspend fun requestRecordAudioPermission(): Boolean {
    if (
        ContextCompat.checkSelfPermission(this, RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    ) {
      return true
    }
    return permissionMutex.withLock {
      suspendCancellableCoroutine { continuation ->
        audioPermissionContinuation = continuation
        continuation.invokeOnCancellation { audioPermissionContinuation = null }
        recordAudioPermissionLauncher.launch(RECORD_AUDIO)
      }
    }
  }

  private var isSdkInitialized = false

  private fun ensureSdkInitialized() {
    if (isSdkInitialized) return
    try {
      Wearables.initialize(applicationContext)
      isSdkInitialized = true
      val permissionsGranted = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
      }
      if (permissionsGranted) {
        initVoiceInvocationsStream()
      }
    } catch (e: Throwable) {
      Log.e("MainActivity", "Error initializing Wearables SDK", e)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    try {
      intent?.setExtrasClassLoader(classLoader)
      Wearables.handleIntent(intent) { request ->
        request.continueRegistration(this)
      }
      if (isVoiceInvocationsIntent(intent)) {
        Log.i("MainActivity", "Voice-triggered launch detected in onCreate")
        viewModel.onVoiceTriggeredLaunch()
      }
    } catch (e: Throwable) {
      Log.e("MainActivity", "Error checking voice invocations intent in onCreate", e)
    }

    ensureSdkInitialized()

    // Request battery optimization exemption for uninterrupted background AI requests via glasses
    try {
      val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
      if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
        val batteryIntent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
          data = android.net.Uri.parse("package:$packageName")
        }
        startActivity(batteryIntent)
      }
    } catch (e: Throwable) {
      Log.w("MainActivity", "Battery optimization prompt skipped: ${e.message}")
    }

    // Pre-warm ML Kit QR neural engine into GPU/NNAPI cache for instant 0ms cold starts
    com.meta.wearable.dat.externalsampleapps.cameraaccess.camera.FastQrScanner.prewarm()

    enableEdgeToEdge()
    setContent {
      CameraAccessScaffold(
          viewModel = viewModel,
          onRequestWearablesPermission = ::requestWearablesPermission,
          onRequestRecordAudioPermission = ::requestRecordAudioPermission,
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    try {
      intent?.setExtrasClassLoader(classLoader)
      Wearables.handleIntent(intent) { request ->
        request.continueRegistration(this)
      }
      if (isVoiceInvocationsIntent(intent)) {
        Log.i("MainActivity", "Voice-triggered launch detected in onNewIntent")
        viewModel.onVoiceTriggeredLaunch()
      }
    } catch (e: Throwable) {
      Log.e("MainActivity", "Error checking voice invocations intent in onNewIntent", e)
    }
  }

  private fun initVoiceInvocationsStream() {
    if (voiceInvocationsStream != null) return
    try {
      val stream = Wearables.startVoiceInvocationsStream(AutoDeviceSelector())
      voiceInvocationsStream = stream
      lifecycleScope.launch {
        try {
          stream.invocations.collect { invocation: VoiceInvocation ->
            try {
              when (invocation) {
                is LaunchApp -> {
                  Log.i("MainActivity", "Handling LaunchApp voice invocation")
                  viewModel.onVoiceTriggeredLaunch()
                  invocation.responseHandle.sendSuccess(null)
                }
                else -> {
                  Log.i("MainActivity", "Handling unexpected voice invocation: $invocation")
                  try {
                    val handle = invocation.javaClass.getMethod("getResponseHandle").invoke(invocation) as? com.meta.wearable.dat.core.voiceinvocations.types.actions.ResponseHandle
                    handle?.sendSuccess(null)
                  } catch (e: Throwable) {
                    Log.w("MainActivity", "Could not get responseHandle: ${e.message}")
                  }
                }
              }
            } catch (e: Throwable) {
              Log.e("MainActivity", "Error answering voice invocation: ${e.message}")
              try {
                if (invocation is LaunchApp) {
                  invocation.responseHandle.sendFailure(e.message ?: "Failed")
                }
              } catch (ignored: Throwable) {}
            }
          }
        } catch (e: Throwable) {
          Log.e("MainActivity", "Error collecting voice invocations: ${e.message}")
        }
      }

      lifecycleScope.launch {
        try {
          stream.errors.collect { error ->
            Log.w("MainActivity", "Voice invocations stream error: ${error.description}")
          }
        } catch (e: Throwable) {
          Log.e("MainActivity", "Error in voice invocations errors flow: ${e.message}")
        }
      }
    } catch (e: Throwable) {
      Log.e("MainActivity", "Error starting voice invocations stream: ${e.message}")
    }
  }

  override fun onStart() {
    super.onStart()
    // First, ensure the app has necessary Android permissions
    permissionCheckLauncher.launch(PERMISSIONS)
  }

  override fun onResume() {
    super.onResume()
    Log.i("MainActivity", "onResume: Checking SDK initialization and voice stream")
    ensureSdkInitialized()
  }

  override fun onDestroy() {
    super.onDestroy()
    try {
      voiceInvocationsStream?.close()
    } catch (e: Throwable) {
      Log.e("MainActivity", "Error closing voice stream: ${e.message}")
    }
    voiceInvocationsStream = null
  }
}
