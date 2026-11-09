/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.ai

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PayPalOrderItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Result data class for visual product scanning via Google Gemini 2.0 Flash.
 */
data class GeminiObjectResult(
    val productName: String,
    val brand: String,
    val category: String,
    val estimatedPrice: Double,
    val currency: String = "USD",
    val shortDescription: String,
    val merchantName: String,
    val shoppingUrl: String,
    val paypalSupported: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Result data class for visual receipt/bill splitting via Google Gemini 2.0 Flash.
 */
data class GeminiBillResult(
    val merchantName: String,
    val items: List<PayPalOrderItem>,
    val subtotal: Double,
    val taxTotal: Double,
    val tip: Double,
    val grandTotal: Double,
    val suggestedSplitPerPerson: Double,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Multimodal Visual AI service connecting AirWallet to Google Gemini 2.0 Flash.
 * Handles high-resolution camera frame encoding, multimodal REST requests, structured
 * schema generation, and transparent demo fallback mode for offline testing.
 *
 * Uses Google Gemini 2.0 Flash for instant, zero-cost optical recognition.
 */
class GeminiFlashService(private val context: Context) {
  companion object {
    private const val TAG = "AirWallet:GeminiFlash"
    private const val PREFS_NAME = "airwallet_gemini_prefs"
    private const val KEY_API_KEY = "gemini_api_key"
    private const val KEY_BASE_URL = "gemini_base_url"
    private const val KEY_MODEL = "gemini_model"

    // App-side built-in API key: zero setup or client-side configuration required
    const val APP_BUILTIN_API_KEY = "AQ.Ab8RN6KXe1FkSqkE5DqxF0FHqPRSphmA1s8NF65J-WUbOetDFg"
    const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    const val DEFAULT_MODEL = "gemini-flash-lite-latest"
  }

  private val prefs: SharedPreferences =
      context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun getApiKey(): String {
    val key = prefs.getString(KEY_API_KEY, "") ?: ""
    return if (key.isNotBlank()) key else APP_BUILTIN_API_KEY
  }

  fun setApiKey(apiKey: String) {
    prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    Log.i(TAG, "Gemini Flash API Key updated (length: ${apiKey.length})")
  }

  fun getBaseUrl(): String {
    return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
  }

  fun setBaseUrl(url: String) {
    prefs.edit().putString(KEY_BASE_URL, url.trim()).apply()
  }

  fun getModel(): String {
    return prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
  }

  fun setModel(model: String) {
    prefs.edit().putString(KEY_MODEL, model.trim()).apply()
  }

  fun hasApiKey(): Boolean = true

  /**
   * Identifies a physical commercial product in the camera frame using Google Gemini Flash.
   * Returns product details, best merchant price, and shopping checkout link.
   */
  suspend fun identifyObject(bitmap: Bitmap?): Result<GeminiObjectResult> = withContext(Dispatchers.IO) {
    if (bitmap == null) {
      Log.w(TAG, "identifyObject: bitmap is null! No image frame provided.")
      return@withContext Result.failure(Exception("Try again, AI is not available currently"))
    }

    val apiKey = getApiKey()
    Log.i(TAG, "identifyObject: Starting real visual product recognition (dimensions: ${bitmap.width}x${bitmap.height})")

    try {
      val base64Image = bitmapToBase64(bitmap, quality = 82)
      val prompt = """
        You are Google Gemini 2.0 Flash multimodal visual assistant for AirWallet smart glasses.
        Identify the primary physical commercial product in this image.
        If no commercial product or item is identifiable, return:
        { "error": "not_found" }
        Otherwise, return a strict, valid JSON object with EXACTLY this structure:
        {
          "productName": "Product Name and Model",
          "brand": "Manufacturer Brand",
          "category": "Electronics | Fashion | Groceries | Lifestyle",
          "estimatedPrice": 129.99,
          "currency": "USD",
          "shortDescription": "One concise sentence describing the product",
          "merchantName": "Best Buy or Target or Amazon or Merchant",
          "shoppingUrl": "https://www.google.com/search?q=product+buy",
          "paypalSupported": true
        }
      """.trimIndent()

      val jsonResponse = callMultimodalApi(apiKey, base64Image, prompt)
      if (jsonResponse != null) {
        val parsed = parseObjectJson(jsonResponse)
        if (parsed != null) {
          Log.i(TAG, "Gemini Flash successfully identified real object: ${parsed.productName} ($${parsed.estimatedPrice})")
          return@withContext Result.success(parsed)
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Live Gemini Flash API call failed: ${e.message}", e)
    }

    Log.w(TAG, "identifyObject: AI identification failed or item not found. Prompting try again.")
    Result.failure(Exception("Try again, AI is not available currently"))
  }

  /**
   * Scans a physical paper restaurant bill or retail receipt using Google Gemini Flash.
   * Extracts itemized breakdown, tax, tip, and total, ready for PayPal Invoicing v2.
   */
  suspend fun parseBill(bitmap: Bitmap?): Result<GeminiBillResult> = withContext(Dispatchers.IO) {
    if (bitmap == null) {
      Log.w(TAG, "parseBill: bitmap is null! No image frame provided.")
      return@withContext Result.failure(Exception("Try again, AI is not available currently"))
    }

    val apiKey = getApiKey()
    Log.i(TAG, "parseBill: Starting real optical receipt itemization with Gemini Flash (dimensions: ${bitmap.width}x${bitmap.height})")

    try {
      val base64Image = bitmapToBase64(bitmap, quality = 85)
      val prompt = """
        You are Google Gemini 2.0 Flash optical receipt parser for AirWallet smart glasses.
        Extract the itemized receipt from this image.
        If no receipt or bill is identifiable, return:
        { "error": "not_found" }
        Otherwise, return a strict, valid JSON object with EXACTLY this structure:
        {
          "merchantName": "Name of restaurant or merchant",
          "items": [
            { "name": "Item 1", "quantity": 1, "unitAmount": 14.50, "tax": 1.20 },
            { "name": "Item 2", "quantity": 2, "unitAmount": 8.00, "tax": 1.30 }
          ],
          "subtotal": 30.50,
          "taxTotal": 2.50,
          "tip": 5.00,
          "grandTotal": 38.00,
          "suggestedSplitPerPerson": 19.00
        }
      """.trimIndent()

      val jsonResponse = callMultimodalApi(apiKey, base64Image, prompt)
      if (jsonResponse != null) {
        val parsed = parseBillJson(jsonResponse)
        if (parsed != null) {
          Log.i(TAG, "Gemini Flash successfully parsed real bill: ${parsed.merchantName} (Total: $${parsed.grandTotal})")
          return@withContext Result.success(parsed)
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Live Gemini Flash bill parsing failed: ${e.message}", e)
    }

    Log.w(TAG, "parseBill: AI receipt parsing failed or bill not found. Prompting try again.")
    Result.failure(Exception("Try again, AI is not available currently"))
  }

  /**
   * Encodes a bitmap into a compressed Base64 string for efficient network transport.
   */
  private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 75): String {
    val outputStream = ByteArrayOutputStream()
    val maxDim = 768
    val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
      val ratio = Math.min(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
      Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    } else {
      bitmap
    }
    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    val bytes = outputStream.toByteArray()
    Log.i(TAG, "Compressed frame for Gemini: ${scaledBitmap.width}x${scaledBitmap.height}, payload size: ${bytes.size / 1024} KB")
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
  }

  /**
   * Executes a multimodal request via Google Gemini Flash.
   */
  private suspend fun callMultimodalApi(apiKey: String, base64Image: String, textPrompt: String): String? {
    val effectiveKey = apiKey.ifBlank { APP_BUILTIN_API_KEY }
    return callGeminiApi(effectiveKey, base64Image, textPrompt)
  }

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  private val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .dns(object : Dns {
          override fun lookup(hostname: String): List<InetAddress> {
            try {
              val list = Dns.SYSTEM.lookup(hostname)
              if (list.isNotEmpty()) return list
            } catch (e: Exception) {
              Log.w(TAG, "System DNS lookup failed for $hostname: ${e.message}")
            }
            if (hostname.contains("googleapis.com")) {
              Log.i(TAG, "Using direct Google Anycast IP fallback for $hostname")
              return listOf(
                  InetAddress.getByAddress(hostname, byteArrayOf(172.toByte(), 217.toByte(), 115.toByte(), 4.toByte())),
                  InetAddress.getByAddress(hostname, byteArrayOf(142.toByte(), 250.toByte(), 193.toByte(), 138.toByte())),
                  InetAddress.getByAddress(hostname, byteArrayOf(142.toByte(), 250.toByte(), 193.toByte(), 10.toByte()))
              )
            }
            throw UnknownHostException("Unable to resolve $hostname")
          }
        })
        .build()
  }

  /**
   * Direct native call to Google Gemini Flash API via OkHttp with Anycast DNS fallback and cooperative cancellation.
   */
  private suspend fun callGeminiApi(apiKey: String, base64Image: String, textPrompt: String): String? {
    val effectiveKey = apiKey.ifBlank { APP_BUILTIN_API_KEY }
    val modelsToTry = listOf("gemini-flash-lite-latest", "gemini-flash-latest")

    val requestJson = JSONObject().apply {
      val contentsArray = JSONArray().apply {
        put(JSONObject().apply {
          val partsArray = JSONArray().apply {
            put(JSONObject().apply { put("text", textPrompt) })
            put(JSONObject().apply {
              put("inline_data", JSONObject().apply {
                put("mime_type", "image/jpeg")
                put("data", base64Image)
              })
            })
          }
          put("parts", partsArray)
        })
      }
      put("contents", contentsArray)
      put("generationConfig", JSONObject().apply {
        put("response_mime_type", "application/json")
        put("temperature", 0.2)
      })
    }.toString()

    val body = requestJson.toRequestBody(jsonMediaType)

    val maxRetries = 2
    for (attempt in 1..maxRetries) {
      kotlinx.coroutines.currentCoroutineContext().ensureActive()
      if (attempt > 1) {
        val backoff = 400L
        Log.i(TAG, "Retrying Gemini call after ${backoff}ms backoff (attempt $attempt/$maxRetries)...")
        kotlinx.coroutines.delay(backoff)
      }

      for (modelName in modelsToTry) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$effectiveKey"
        var call: okhttp3.Call? = null
        try {
          val request = Request.Builder()
              .url(endpointUrl)
              .post(body)
              .addHeader("Accept", "application/json")
              .build()

          call = okHttpClient.newCall(request)

          val responseText = suspendCancellableCoroutine<String> { continuation ->
            continuation.invokeOnCancellation {
              call.cancel()
            }
            try {
              val response = call.execute()
              response.use { res ->
                val responseBodyStr = res.body?.string() ?: ""
                if (res.isSuccessful) {
                  continuation.resume(responseBodyStr)
                } else {
                  continuation.resumeWithException(java.io.IOException("HTTP ${res.code}: $responseBodyStr"))
                }
              }
            } catch (e: Exception) {
              if (!continuation.isCancelled) {
                continuation.resumeWithException(e)
              }
            }
          }

          val parsedText = extractContentFromGeminiResponse(responseText)
          if (!parsedText.isNullOrBlank()) {
            Log.i(TAG, "Gemini ($modelName) successfully returned response on attempt $attempt")
            return parsedText
          }
        } catch (e: kotlinx.coroutines.CancellationException) {
          Log.i(TAG, "Gemini ($modelName) call cancelled by user")
          call?.cancel()
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "Gemini ($modelName) attempt $attempt failed: ${e.message}")
        }
      }
    }
    return null
  }

  private fun extractContentFromGeminiResponse(responseJsonStr: String): String? {
    return try {
      val root = JSONObject(responseJsonStr)
      val candidates = root.optJSONArray("candidates") ?: return null
      if (candidates.length() > 0) {
        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        if (parts.length() > 0) {
          parts.getJSONObject(0).optString("text")
        } else null
      } else null
    } catch (e: Exception) {
      Log.e(TAG, "Error extracting text from Gemini response: ${e.message}")
      null
    }
  }

  private fun parseObjectJson(rawText: String): GeminiObjectResult? {
    return try {
      val cleaned = cleanJsonString(rawText)
      val json = JSONObject(cleaned)
      if (json.has("error") || json.optString("error").isNotBlank()) {
        Log.w(TAG, "parseObjectJson: Model returned error: ${json.optString("error")}")
        return null
      }
      val name = json.optString("productName", "")
      if (name.isBlank() || name.equals("Unknown Item", ignoreCase = true)) {
        Log.w(TAG, "parseObjectJson: Model returned empty or unknown product name")
        return null
      }
      GeminiObjectResult(
          productName = name,
          brand = json.optString("brand", "Verified Merchant"),
          category = json.optString("category", "Retail"),
          estimatedPrice = json.optDouble("estimatedPrice", 29.99),
          currency = json.optString("currency", "USD"),
          shortDescription = json.optString("shortDescription", "Detected physical item in camera field of view"),
          merchantName = json.optString("merchantName", "Partner Store"),
          shoppingUrl = json.optString("shoppingUrl", "https://www.paypal.com"),
          paypalSupported = json.optBoolean("paypalSupported", true),
      )
    } catch (e: Exception) {
      Log.e(TAG, "Failed to parse object JSON: ${e.message} | Raw: $rawText")
      null
    }
  }

  private fun parseBillJson(rawText: String): GeminiBillResult? {
    return try {
      val cleaned = cleanJsonString(rawText)
      val json = JSONObject(cleaned)
      if (json.has("error") || json.optString("error").isNotBlank()) {
        Log.w(TAG, "parseBillJson: Model returned error: ${json.optString("error")}")
        return null
      }
      val merchantName = json.optString("merchantName", "")
      if (merchantName.isBlank() || merchantName.equals("Unknown Merchant", ignoreCase = true)) {
        Log.w(TAG, "parseBillJson: Model returned empty or unknown merchant")
        return null
      }
      val itemsArray = json.optJSONArray("items") ?: JSONArray()
      val items = mutableListOf<PayPalOrderItem>()
      for (i in 0 until itemsArray.length()) {
        val it = itemsArray.getJSONObject(i)
        items.add(
            PayPalOrderItem(
                name = it.optString("name", "Item ${i + 1}"),
                quantity = it.optInt("quantity", 1),
                unitAmount = it.optDouble("unitAmount", 10.0),
                tax = it.optDouble("tax", 0.0),
            )
        )
      }
      val subtotal = json.optDouble("subtotal", items.sumOf { it.unitAmount * it.quantity })
      val taxTotal = json.optDouble("taxTotal", items.sumOf { it.tax })
      val tip = json.optDouble("tip", 0.0)
      val grandTotal = json.optDouble("grandTotal", subtotal + taxTotal + tip)
      val suggestedSplit = json.optDouble("suggestedSplitPerPerson", (grandTotal / 2.0).coerceAtLeast(1.0))

      GeminiBillResult(
          merchantName = merchantName,
          items = items,
          subtotal = subtotal,
          taxTotal = taxTotal,
          tip = tip,
          grandTotal = grandTotal,
          suggestedSplitPerPerson = suggestedSplit,
      )
    } catch (e: Exception) {
      Log.e(TAG, "Failed to parse bill JSON: ${e.message} | Raw: $rawText")
      null
    }
  }

  private fun cleanJsonString(raw: String): String {
    val trimmed = raw.trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    return if (start != -1 && end != -1 && end > start) {
      trimmed.substring(start, end + 1)
    } else {
      trimmed
    }
  }
}
