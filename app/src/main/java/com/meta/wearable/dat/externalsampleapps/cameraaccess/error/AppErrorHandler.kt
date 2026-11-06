/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.error

import android.content.Context
import android.util.Log
import com.meta.wearable.dat.speech.types.SpeechError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ErrorSeverity {
  INFO,
  WARNING,
  ERROR
}

enum class ErrorCategory {
  CONNECTION,
  SPEECH,
  CAMERA,
  PAYMENT,
  PERMISSION,
  GENERAL
}

data class UserFacingError(
    val id: Long,
    val userMessage: String,
    val category: ErrorCategory,
    val severity: ErrorSeverity = ErrorSeverity.WARNING,
    val timestamp: Long = System.currentTimeMillis(),
    val actionLabel: String? = null,
)

/**
 * Centralized, production-grade error management engine for AirWallet.
 * 
 * Solves continuous error popup loops by providing:
 * 1. Automatic deduplication (identical errors suppressed for 8 seconds).
 * 2. Rate limiting (maximum 1 error popup every 3 seconds).
 * 3. Silent suppression of benign/expected transient events (e.g. Speech ALREADY_LISTENING, normal closures).
 * 4. Human-friendly message translation from low-level SDK exceptions.
 * 5. Automatic self-dismissal after 4 seconds.
 */
object AppErrorHandler {
  private const val TAG = "AppErrorHandler"
  private var nextErrorId = 1L
  private val scope = CoroutineScope(Dispatchers.Main + Job())
  private var autoDismissJob: Job? = null

  private val _activeError = MutableStateFlow<UserFacingError?>(null)
  val activeError: StateFlow<UserFacingError?> = _activeError.asStateFlow()

  // Track timestamps for deduplication (errorKey -> epochMs)
  private val recentErrorTimes = mutableMapOf<String, Long>()
  private var lastAnyErrorTime = 0L

  private const val SAME_ERROR_COOLDOWN_MS = 8_000L
  private const val GLOBAL_MIN_INTERVAL_MS = 3_000L
  private const val AUTO_DISMISS_DELAY_MS = 4_500L

  /**
   * Benign or internal lifecycle strings that must never be presented to the user.
   */
  private val BENIGN_SUBSTRINGS = listOf(
      "already_listening",
      "already listening",
      "invalid_state",
      "session completed",
      "session finished",
      "normal closure",
      "cancelled by user",
      "user stopped",
      "stream terminated normally",
      "client disconnected normally",
      "starting stream",
      "stopped",
  )

  fun postError(
      rawError: String,
      category: ErrorCategory = ErrorCategory.GENERAL,
      severity: ErrorSeverity = ErrorSeverity.WARNING,
      actionLabel: String? = null,
      context: Context? = null,
  ) {
    val trimmed = rawError.trim()
    if (trimmed.isEmpty()) return

    val lower = trimmed.lowercase()

    // 1. Completely ignore benign / expected lifecycle events
    if (BENIGN_SUBSTRINGS.any { lower.contains(it) }) {
      Log.d(TAG, "Suppressed benign event from UI: \"$trimmed\"")
      return
    }

    val now = System.currentTimeMillis()

    // 2. Global rate-limiting to prevent visual spam
    if (now - lastAnyErrorTime < GLOBAL_MIN_INTERVAL_MS) {
      Log.d(TAG, "Rate-limited rapid error popup: \"$trimmed\"")
      return
    }

    // 3. Deduplicate repeating identical errors
    val errorKey = "${category.name}_$trimmed"
    val lastSeen = recentErrorTimes[errorKey] ?: 0L
    if (now - lastSeen < SAME_ERROR_COOLDOWN_MS) {
      Log.d(TAG, "Deduplicated recurring error: \"$trimmed\"")
      return
    }

    recentErrorTimes[errorKey] = now
    lastAnyErrorTime = now

    // Trim cache periodically
    if (recentErrorTimes.size > 50) {
      recentErrorTimes.entries.removeIf { now - it.value > 30_000L }
    }

    // 4. Translate raw SDK error into clear, non-technical user feedback
    val userFriendlyMessage = formatUserFriendlyMessage(trimmed, category)
    val errorId = nextErrorId++

    Log.w(TAG, "Surfacing error [$category]: \"$userFriendlyMessage\" (Raw: \"$trimmed\")")

    _activeError.update {
      UserFacingError(
          id = errorId,
          userMessage = userFriendlyMessage,
          category = category,
          severity = severity,
          timestamp = now,
          actionLabel = actionLabel,
      )
    }

    // Schedule auto-dismiss
    autoDismissJob?.cancel()
    autoDismissJob = scope.launch {
      delay(AUTO_DISMISS_DELAY_MS)
      clearError(errorId)
    }
  }

  /**
   * Exhaustively handles [SpeechError] enum values, filtering out internal states.
   */
  fun handleSpeechError(error: SpeechError) {
    when (error) {
      SpeechError.ALREADY_LISTENING -> {
        // Completely expected when verifying mic status; silently ignored
        Log.d(TAG, "Speech benign: ALREADY_LISTENING")
      }
      SpeechError.INVALID_STATE -> {
        // Occurs transiently during screen changes; log only
        Log.d(TAG, "Speech transient: INVALID_STATE")
      }
      SpeechError.DEVICE_DISCONNECTED -> {
        postError(
            rawError = "Glasses microphone disconnected. Check Bluetooth connection.",
            category = ErrorCategory.SPEECH,
            severity = ErrorSeverity.WARNING,
        )
      }
      SpeechError.UNAVAILABLE -> {
        postError(
            rawError = "Glasses speech recognition temporarily unavailable.",
            category = ErrorCategory.SPEECH,
            severity = ErrorSeverity.INFO,
        )
      }
      SpeechError.START_FAILED -> {
        postError(
            rawError = "Unable to start voice listening. Please try again.",
            category = ErrorCategory.SPEECH,
            severity = ErrorSeverity.INFO,
        )
      }
      SpeechError.UNEXPECTED_ERROR -> {
        Log.w(TAG, "Speech encountered unexpected error")
      }
    }
  }

  fun clearError(errorId: Long) {
    _activeError.update { current ->
      if (current?.id == errorId) null else current
    }
  }

  fun dismissCurrent() {
    autoDismissJob?.cancel()
    _activeError.update { null }
  }

  private fun formatUserFriendlyMessage(raw: String, category: ErrorCategory): String {
    val lower = raw.lowercase()
    return when {
      lower.contains("permission") -> "Camera or microphone permission required. Please enable in Settings."
      lower.contains("update required") || lower.contains("dat_app_on_the_glasses_update_required") ->
        "Smart glasses firmware or Meta View update required."
      lower.contains("device_disconnected") || lower.contains("disconnected") ->
        "Glasses disconnected. Reconnecting via Bluetooth..."
      lower.contains("start_session_first") -> "Please connect to smart glasses first."
      lower.contains("insufficient balance") -> "Insufficient funds for this transaction."
      lower.contains("network") || lower.contains("timeout") -> "Network request timed out. Please check your connection."
      lower.contains("paypal payment failed") -> "Payment authorization failed. Please try again."
      lower.contains("add camera") -> "Unable to start glasses camera. Please reconnect glasses."
      else -> raw
    }
  }
}
