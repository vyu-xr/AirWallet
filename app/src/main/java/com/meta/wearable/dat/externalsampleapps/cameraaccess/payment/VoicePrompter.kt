/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.payment

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoicePrompter(context: Context) {
  companion object {
    private const val TAG = "VoicePrompter"
  }

  private var tts: TextToSpeech? = null
  private var isTtsReady = false
  private val pendingPrompts = mutableListOf<String>()

  init {
    tts = TextToSpeech(context.applicationContext) { status ->
      if (status == TextToSpeech.SUCCESS) {
        val result = tts?.setLanguage(Locale.US)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
          tts?.language = Locale.ENGLISH
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        tts?.setAudioAttributes(audioAttributes)
        tts?.setSpeechRate(1.0f)
        tts?.setPitch(1.0f)
        isTtsReady = true
        Log.i(TAG, "TTS initialized successfully")
        synchronized(pendingPrompts) {
          pendingPrompts.forEach { text -> speakInternal(text) }
          pendingPrompts.clear()
        }
      } else {
        Log.e(TAG, "TTS initialization failed with status $status")
      }
    }
  }

  fun speak(text: String) {
    if (!isTtsReady) {
      Log.i(TAG, "TTS not ready yet, queuing: \"$text\"")
      synchronized(pendingPrompts) {
        pendingPrompts.add(text)
      }
      return
    }
    speakInternal(text)
  }

  private fun speakInternal(text: String) {
    Log.i(TAG, "Playing voice cue: \"$text\"")
    val params = Bundle().apply {
      putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
    }
    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "prompt_${System.currentTimeMillis()}")
  }

  fun isSpeaking(): Boolean {
    return tts?.isSpeaking == true
  }

  fun stop() {
    tts?.stop()
  }

  fun shutdown() {
    tts?.stop()
    tts?.shutdown()
    tts = null
  }
}

/**
 * Listens for voice confirmation ("yes", "pay", "no", "cancel")
 * using Android's built-in SpeechRecognizer.
 */
class SpeechConfirmationListener(
    private val context: Context,
    private val onConfirm: () -> Unit,
    private val onCancel: () -> Unit,
) {
  companion object {
    private const val TAG = "SpeechConfirmation"
  }

  private var speechRecognizer: SpeechRecognizer? = null
  private var isListening = false

  fun startListening() {
    // Suppress Android SpeechRecognizer over Bluetooth to prevent audio routing conflicts on glasses
    Log.d(TAG, "SpeechRecognizer startListening suppressed for glasses session stability")
  }

  fun stopListening() {
    try {
      speechRecognizer?.stopListening()
      speechRecognizer?.destroy()
    } catch (e: Exception) {
      // Ignored
    }
    speechRecognizer = null
    isListening = false
  }
}

