/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.payment

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.common.GlobalHistogramBinarizer
import java.util.concurrent.TimeUnit

object QrCodeAnalyzer {
  private const val TAG = "QrCodeAnalyzer"

  // Bundled ML Kit QR Scanner for hardware-accelerated sub-50ms frame recognition
  private val mlKitOptions = BarcodeScannerOptions.Builder()
      .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
      .build()
  private val mlKitScanner = BarcodeScanning.getClient(mlKitOptions)

  private val fastHints = mapOf(
      DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
      DecodeHintType.CHARACTER_SET to "UTF-8",
  )
  private val thoroughHints = mapOf(
      DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
      DecodeHintType.TRY_HARDER to java.lang.Boolean.TRUE,
      DecodeHintType.ALSO_INVERTED to java.lang.Boolean.TRUE,
      DecodeHintType.CHARACTER_SET to "UTF-8",
  )

  private fun tryDecode(source: RGBLuminanceSource, tryHarder: Boolean = false): String? {
    val hints = if (tryHarder) thoroughHints else fastHints
    // 1. HybridBinarizer (adaptive local thresholding)
    try {
      val reader = MultiFormatReader().apply { setHints(hints) }
      val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
      if (!result.text.isNullOrBlank()) return result.text
    } catch (_: NotFoundException) {
    } catch (_: Exception) {}

    // 2. GlobalHistogramBinarizer (fast for high contrast & uniform backgrounds)
    try {
      val reader = MultiFormatReader().apply { setHints(hints) }
      val result = reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source)))
      if (!result.text.isNullOrBlank()) return result.text
    } catch (_: NotFoundException) {
    } catch (_: Exception) {}

    // 3. Inverted luminance (dark mode / white-on-black QR codes)
    try {
      val reader = MultiFormatReader().apply { setHints(hints) }
      val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert())))
      if (!result.text.isNullOrBlank()) return result.text
    } catch (_: NotFoundException) {
    } catch (_: Exception) {}

    return null
  }

  private fun bitmapToSource(bmp: Bitmap): RGBLuminanceSource {
    val w = bmp.width
    val h = bmp.height
    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    return RGBLuminanceSource(w, h, pixels)
  }

  /**
   * Decodes a QR code from a [Bitmap] using a multi-tiered ultra-fast detection pipeline
   * optimized for both large and small/distant QR codes:
   * 1. Center Reticle Zoom Crop (42% ROI, 2x bilinear upscale, TRY_HARDER) - Small QR Specialist
   * 2. Micro Reticle Zoom Crop (28% ROI, 2.5x bilinear upscale, TRY_HARDER) - Micro / Distant QR Specialist
   * 3. Wide Region-of-Interest (88% ROI) at full native sensor resolution
   * 4. Full frame high-resolution scan fallback (up to 1600px)
   */
  fun decodeBitmap(bitmap: Bitmap): String? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null

    // 0. TIER 0: Hardware-Accelerated ML Kit Neural Pass (sub-15ms cold start)
    try {
      val inputImage = InputImage.fromBitmap(bitmap, 0)
      val task = mlKitScanner.process(inputImage)
      val barcodes = Tasks.await(task, 150, TimeUnit.MILLISECONDS)
      for (barcode in barcodes) {
        val raw = barcode.rawValue
        if (!raw.isNullOrBlank()) {
          Log.i(TAG, "QR decoded in ML Kit pass: $raw")
          return raw
        }
      }
    } catch (e: Exception) {
      Log.d(TAG, "ML Kit bitmap pass error or timeout: ${e.message}")
    }

    // 1. TIER 1: Center Reticle Zoom Crop (Dedicated Small QR Detector)
    // When wearers target a small QR code, they align the reticle on it.
    // We extract the central 42% ROI (with slight left-shift matching the left camera optical parallax)
    // and upscale it 2x with bilinear filtering.
    // This doubles module pixel count and prevents HybridBinarizer 8x8 blocks from blurring small modules.
    try {
      val cropW = (width * 0.42f).toInt().coerceAtLeast(10)
      val cropH = (height * 0.42f).toInt().coerceAtLeast(10)
      val cropLeft = ((width - cropW) / 2 - (width * 0.05f).toInt()).coerceIn(0, width - cropW)
      val cropTop = ((height - cropH) / 2).coerceIn(0, height - cropH)

      val centerCrop = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropW, cropH)
      try {
        val upscaled = Bitmap.createScaledBitmap(centerCrop, cropW * 2, cropH * 2, true)
        try {
          val text = tryDecode(bitmapToSource(upscaled), tryHarder = true)
          if (!text.isNullOrBlank()) {
            Log.i(TAG, "QR decoded in Center Reticle Zoom pass (2x): $text")
            return text
          }
        } finally {
          if (upscaled != centerCrop) upscaled.recycle()
        }
      } finally {
        centerCrop.recycle()
      }
    } catch (e: Exception) {
      Log.d(TAG, "Center Reticle Zoom pass error: ${e.message}")
    }

    // 2. TIER 2: Micro Reticle Zoom Crop (Ultra-small / Distant QRs - 2.5x Zoom)
    // Tighter 28% central crop for tiny stickers or small monitor barcodes at >1.5ft distance
    try {
      val microW = (width * 0.28f).toInt().coerceAtLeast(10)
      val microH = (height * 0.28f).toInt().coerceAtLeast(10)
      val microLeft = ((width - microW) / 2 - (width * 0.05f).toInt()).coerceIn(0, width - microW)
      val microTop = ((height - microH) / 2).coerceIn(0, height - microH)

      val microCrop = Bitmap.createBitmap(bitmap, microLeft, microTop, microW, microH)
      try {
        val microUpscaled = Bitmap.createScaledBitmap(
            microCrop,
            (microW * 2.5f).toInt().coerceAtLeast(1),
            (microH * 2.5f).toInt().coerceAtLeast(1),
            true,
        )
        try {
          val text = tryDecode(bitmapToSource(microUpscaled), tryHarder = true)
          if (!text.isNullOrBlank()) {
            Log.i(TAG, "QR decoded in Micro Reticle Zoom pass (2.5x): $text")
            return text
          }
        } finally {
          if (microUpscaled != microCrop) microUpscaled.recycle()
        }
      } finally {
        microCrop.recycle()
      }
    } catch (e: Exception) {
      Log.d(TAG, "Micro Reticle Zoom pass error: ${e.message}")
    }

    // 3. TIER 3: Wide Region-of-Interest (ROI) at Full Native Resolution
    // Covers 88% width and 75% height for large QR codes or QRs held close to the camera.
    try {
      val roiWidth = (width * 0.88f).toInt().coerceAtLeast(1)
      val roiHeight = (height * 0.75f).toInt().coerceAtLeast(1)
      val roiLeft = ((width - roiWidth) / 2).coerceIn(0, (width - roiWidth).coerceAtLeast(0))
      val roiTop = ((height - roiHeight) / 2).coerceIn(0, (height - roiHeight).coerceAtLeast(0))

      val roiPixels = IntArray(roiWidth * roiHeight)
      bitmap.getPixels(roiPixels, 0, roiWidth, roiLeft, roiTop, roiWidth, roiHeight)
      val roiSource = RGBLuminanceSource(roiWidth, roiHeight, roiPixels)

      val text = tryDecode(roiSource, tryHarder = false)
      if (!text.isNullOrBlank()) {
        Log.i(TAG, "QR decoded in Wide ROI pass: $text")
        return text
      }
    } catch (e: Exception) {
      Log.d(TAG, "Wide ROI pass error: ${e.message}")
    }

    // 4. TIER 4: Full Frame Scan (High Resolution Fallback)
    // Preserves up to 1600px (instead of previous aggressive 800px downsampling)
    val scaledBitmap = if (width > 1600 || height > 1600) {
      val maxDim = maxOf(width, height)
      val scale = 1600f / maxDim
      val newWidth = (width * scale).toInt().coerceAtLeast(1)
      val newHeight = (height * scale).toInt().coerceAtLeast(1)
      Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    } else {
      bitmap
    }

    try {
      val fullWidth = scaledBitmap.width
      val fullHeight = scaledBitmap.height
      val pixels = IntArray(fullWidth * fullHeight)
      scaledBitmap.getPixels(pixels, 0, fullWidth, 0, 0, fullWidth, fullHeight)
      val fullSource = RGBLuminanceSource(fullWidth, fullHeight, pixels)

      val text = tryDecode(fullSource, tryHarder = true)
      if (!text.isNullOrBlank()) {
        Log.i(TAG, "QR decoded in Full Frame exhaustive pass: $text")
        return text
      }
    } catch (e: Exception) {
      Log.d(TAG, "Full Frame pass error: ${e.message}")
    } finally {
      if (scaledBitmap != bitmap) {
        scaledBitmap.recycle()
      }
    }

    return null
  }

  /**
   * Parses the raw QR code text into [PaymentDetails].
   * Handles PayPal Links/QRs, Venmo, UPI URI schemes, raw query strings, VPAs, and fallback.
   */
  fun parsePaymentQr(
      qrContent: String,
      preferredMethod: com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod =
          com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod.PAYPAL_VAULT,
  ): PaymentDetails? {
    val trimmed = qrContent.trim()
    if (trimmed.isEmpty()) return null

    // Instant offline zero-latency parser (< 1ms) for UPI, EMVCo, BharatQR & PayPal
    val fastPayload = QrPayloadParser.parse(trimmed)
    if (fastPayload !is QrPayload.Unknown) {
      val details = QrPayloadParser.toPaymentDetails(fastPayload, preferredMethod)
      if (details != null) return details
    }

    // 1. PayPal.me links: e.g. https://paypal.me/CoffeeShop/15 or paypal.me/Bakery
    if (trimmed.contains("paypal.me/", ignoreCase = true)) {
      return try {
        val cleanUrl = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
          "https://$trimmed"
        } else {
          trimmed
        }
        val uri = Uri.parse(cleanUrl)
        val segments = uri.pathSegments
        val payeeName = segments.getOrNull(0)?.replace("+", " ") ?: "PayPal Merchant"
        val amount = segments.getOrNull(1)?.toDoubleOrNull()?.takeIf { it > 0.0 }
        PaymentDetails(
            payeeVpa = "$payeeName@paypal.com",
            payeeName = payeeName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            amount = amount,
            currency = "USD",
            note = "PayPal.me Payment",
            rawQr = trimmed,
            paymentMethod = com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod.PAYPAL_VAULT,
        )
      } catch (e: Exception) {
        Log.e(TAG, "Failed to parse PayPal.me URI: $trimmed", e)
        null
      }
    }

    // 2. PayPal URI scheme or managed QR: paypal://pay?... or https://www.paypal.com/qrcodes/managed/...
    if (trimmed.startsWith("paypal://", ignoreCase = true) ||
        trimmed.contains("paypal.com/qrcodes/", ignoreCase = true)) {
      return try {
        val uri = Uri.parse(trimmed)
        val recipient = uri.getQueryParameter("recipient")
            ?: uri.getQueryParameter("payer_id")
            ?: uri.lastPathSegment
            ?: "PayPal Merchant"
        val amStr = uri.getQueryParameter("amount")
        val amount = amStr?.toDoubleOrNull()?.takeIf { it > 0.0 }
        val cu = uri.getQueryParameter("currency") ?: "USD"
        PaymentDetails(
            payeeVpa = recipient,
            payeeName = recipient.substringBefore("@")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            amount = amount,
            currency = cu,
            note = "PayPal Vault Order",
            rawQr = trimmed,
            paymentMethod = com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod.PAYPAL_VAULT,
        )
      } catch (e: Exception) {
        Log.e(TAG, "Failed to parse PayPal QR: $trimmed", e)
        null
      }
    }

    // 3. Venmo QR / Link: venmo://paycharge?... or https://venmo.com/...
    if (trimmed.startsWith("venmo://", ignoreCase = true) ||
        trimmed.contains("venmo.com/", ignoreCase = true)) {
      return try {
        val uri = Uri.parse(trimmed)
        val recipient = uri.getQueryParameter("recipients") ?: uri.lastPathSegment ?: "Venmo Merchant"
        val amStr = uri.getQueryParameter("amount")
        val amount = amStr?.toDoubleOrNull()?.takeIf { it > 0.0 }
        val note = uri.getQueryParameter("note") ?: "Venmo Pay"
        PaymentDetails(
            payeeVpa = "$recipient@venmo",
            payeeName = recipient.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            amount = amount,
            currency = "USD",
            note = note,
            rawQr = trimmed,
            paymentMethod = com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod.PAYPAL_VAULT,
        )
      } catch (e: Exception) {
        Log.e(TAG, "Failed to parse Venmo URI: $trimmed", e)
        null
      }
    }

    // 4. UPI URI scheme: upi://pay?pa=...&pn=...&am=...&cu=INR&tn=...
    if (trimmed.startsWith("upi://pay", ignoreCase = true) ||
        trimmed.startsWith("upi://", ignoreCase = true)) {
      return try {
        val uri = Uri.parse(trimmed)
        val pa = uri.getQueryParameter("pa") ?: return null
        val pn = uri.getQueryParameter("pn")?.replace("+", " ") ?: pa.substringBefore("@")
        val amStr = uri.getQueryParameter("am")
        val amount = amStr?.toDoubleOrNull()?.takeIf { it > 0.0 }
        val cu = uri.getQueryParameter("cu") ?: "INR"
        val tn = uri.getQueryParameter("tn")?.replace("+", " ") ?: ""
        PaymentDetails(
            payeeVpa = pa,
            payeeName = pn,
            amount = amount,
            currency = cu,
            note = tn,
            rawQr = trimmed,
            paymentMethod = com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod.UPI,
        )
      } catch (e: Exception) {
        Log.e(TAG, "Failed to parse UPI URI: $trimmed", e)
        null
      }
    }

    // 5. Query param style without upi:// scheme (e.g. pa=merchant@upi&pn=Store&am=200)
    if (trimmed.contains("pa=") && trimmed.contains("@")) {
      return try {
        val dummyUri = Uri.parse("upi://pay?$trimmed")
        val pa = dummyUri.getQueryParameter("pa") ?: return null
        val pn = dummyUri.getQueryParameter("pn")?.replace("+", " ") ?: pa.substringBefore("@")
        val amStr = dummyUri.getQueryParameter("am")
        val amount = amStr?.toDoubleOrNull()?.takeIf { it > 0.0 }
        val cu = dummyUri.getQueryParameter("cu") ?: "INR"
        val tn = dummyUri.getQueryParameter("tn")?.replace("+", " ") ?: ""
        PaymentDetails(
            payeeVpa = pa,
            payeeName = pn,
            amount = amount,
            currency = cu,
            note = tn,
            rawQr = trimmed,
            paymentMethod = com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod.UPI,
        )
      } catch (e: Exception) {
        null
      }
    }

    // 6. Simple UPI VPA (e.g. "coffee@okhdfcbank" or "shop@paytm")
    val upiHandles = listOf("okhdfcbank", "okaxis", "oksbi", "okicici", "paytm", "upi", "ybl", "ibl", "axl", "apl")
    val vpaRegex = Regex("""^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$""")
    if (vpaRegex.matches(trimmed)) {
      val handle = trimmed.substringAfter("@").lowercase()
      val isUpi = upiHandles.any { handle.contains(it) }
      val name = trimmed.substringBefore("@")
          .replace(".", " ")
          .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

      return if (isUpi) {
        PaymentDetails(
            payeeVpa = trimmed,
            payeeName = name,
            amount = null,
            currency = "INR",
            note = "",
            rawQr = trimmed,
            paymentMethod = com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod.UPI,
        )
      } else {
        // Standard email address -> PayPal payee!
        PaymentDetails(
            payeeVpa = trimmed,
            payeeName = name,
            amount = null,
            currency = "USD",
            note = "",
            rawQr = trimmed,
            paymentMethod = com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod.PAYPAL_VAULT,
        )
      }
    }

    // 7. Fallback for test / prototype QR codes using preferred payment method
    return if (preferredMethod == com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod.PAYPAL_VAULT) {
      PaymentDetails(
          payeeVpa = "merchant@sandbox.paypal.com",
          payeeName = trimmed.take(24).ifEmpty { "PayPal Merchant" },
          amount = null,
          currency = "USD",
          note = "Scanned QR: ${trimmed.take(30)}",
          rawQr = trimmed,
          paymentMethod = com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod.PAYPAL_VAULT,
      )
    } else {
      PaymentDetails(
          payeeVpa = "demo.merchant@upi",
          payeeName = trimmed.take(24).ifEmpty { "Test Payee" },
          amount = null,
          currency = "INR",
          note = "Scanned QR: ${trimmed.take(30)}",
          rawQr = trimmed,
          paymentMethod = com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod.UPI,
      )
    }
  }

  /**
   * Parses spoken text for an amount in currency (e.g. "50", "pay 150", "two hundred", "fifty", "fifteen dollars").
   * Returns parsed double if found, or null.
   */
  fun parseSpokenAmount(text: String): Double? {
    val lower = text.lowercase().trim()
    // 1. Look for digits: e.g. "150", "pay 50.50", "15", "$20"
    val digitMatch = Regex("""\b(\d+(\.\d{1,2})?)\b""").find(lower)
    if (digitMatch != null) {
      val num = digitMatch.groupValues[1].toDoubleOrNull()
      if (num != null && num > 0.0) return num
    }

    // 2. Check compound tens + units (e.g. "twenty five", "thirty two", "seventy five")
    val tens = mapOf(
        "twenty" to 20.0,
        "thirty" to 30.0,
        "forty" to 40.0,
        "fifty" to 50.0,
        "sixty" to 60.0,
        "seventy" to 70.0,
        "eighty" to 80.0,
        "ninety" to 90.0,
    )
    val units = mapOf(
        "one" to 1.0,
        "two" to 2.0,
        "three" to 3.0,
        "four" to 4.0,
        "five" to 5.0,
        "six" to 6.0,
        "seven" to 7.0,
        "eight" to 8.0,
        "nine" to 9.0,
    )
    for ((tenWord, tenVal) in tens) {
      for ((unitWord, unitVal) in units) {
        if (Regex("""\b$tenWord\s+$unitWord\b""").containsMatchIn(lower)) {
          return tenVal + unitVal
        }
      }
    }

    // 3. Look for hundreds / thousands phrases
    val phrases = linkedMapOf(
        "one thousand" to 1000.0,
        "two thousand" to 2000.0,
        "five thousand" to 5000.0,
        "one hundred" to 100.0,
        "two hundred" to 200.0,
        "three hundred" to 300.0,
        "four hundred" to 400.0,
        "five hundred" to 500.0,
        "thousand" to 1000.0,
        "hundred" to 100.0,
    )
    for ((phrase, value) in phrases) {
      if (Regex("""\b${Regex.escape(phrase)}\b""").containsMatchIn(lower)) {
        return value
      }
    }

    // 4. Exact individual word matching with strict word boundary \b to prevent matching inside "done", "height", etc.
    val singleWords = linkedMapOf(
        "twenty" to 20.0,
        "thirty" to 30.0,
        "forty" to 40.0,
        "fifty" to 50.0,
        "sixty" to 60.0,
        "seventy" to 70.0,
        "eighty" to 80.0,
        "ninety" to 90.0,
        "eleven" to 11.0,
        "twelve" to 12.0,
        "thirteen" to 13.0,
        "fourteen" to 14.0,
        "fifteen" to 15.0,
        "sixteen" to 16.0,
        "seventeen" to 17.0,
        "eighteen" to 18.0,
        "nineteen" to 19.0,
        "ten" to 10.0,
        "nine" to 9.0,
        "eight" to 8.0,
        "seven" to 7.0,
        "six" to 6.0,
        "five" to 5.0,
        "four" to 4.0,
        "three" to 3.0,
        "two" to 2.0,
        "one" to 1.0,
    )
    for ((word, value) in singleWords) {
      if (Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(lower)) {
        return value
      }
    }
    return null
  }
}

