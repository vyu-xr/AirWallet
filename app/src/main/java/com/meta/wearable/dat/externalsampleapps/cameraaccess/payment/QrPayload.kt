/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.payment

import android.net.Uri
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod

sealed class QrPayload {
  data class Upi(
      val vpa: String,           // pa: Payee VPA (e.g., merchant@okaxis)
      val payeeName: String?,    // pn: Payee Name
      val amount: String?,       // am: Pre-filled amount
      val merchantCode: String?, // mc: MCC code
      val rawUri: String,
  ) : QrPayload()

  data class PayPal(
      val identifier: String,    // Username, merchant ID, or QR token
      val amount: String?,
      val currency: String?,
      val rawUri: String,
  ) : QrPayload()

  data class Unknown(val raw: String) : QrPayload()
}

/**
 * High-speed local offline parser for UPI, PayPal, and EMVCo QR codes.
 * Operates in < 1ms without network calls.
 */
object QrPayloadParser {

  fun parse(rawValue: String): QrPayload {
    val trimmed = rawValue.trim()

    // 1. Check NPCI UPI Standard (upi://pay?pa=... or upi://mandate?...)
    if (trimmed.startsWith("upi://", ignoreCase = true)) {
      val uri = Uri.parse(trimmed)
      val vpa = uri.getQueryParameter("pa")
      if (!vpa.isNullOrBlank()) {
        return QrPayload.Upi(
            vpa = vpa,
            payeeName = uri.getQueryParameter("pn"),
            amount = uri.getQueryParameter("am"),
            merchantCode = uri.getQueryParameter("mc"),
            rawUri = trimmed,
        )
      }
    }

    // 2. Check EMVCo / BharatQR / PayPal EMVCo TLV format (starts with "000201")
    if (trimmed.startsWith("000201")) {
      return parseEmvCo(trimmed)
    }

    // 3. Check PayPal URL-based QR Codes (PayPal.me or PayPal Merchant QR)
    if (trimmed.contains("paypal.com/qrcodes", ignoreCase = true) ||
        trimmed.contains("paypal.me/", ignoreCase = true)) {
      val cleanUrl = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
        "https://$trimmed"
      } else {
        trimmed
      }
      val uri = Uri.parse(cleanUrl)
      val pathSegments = uri.pathSegments

      val (id, pathAmt) = if (trimmed.contains("paypal.me/", ignoreCase = true)) {
        val user = pathSegments.getOrNull(0)?.replace("+", " ") ?: "PayPal Merchant"
        val amt = pathSegments.getOrNull(1)?.takeIf { it.toDoubleOrNull() != null }
        user to amt
      } else {
        val recipientParam = uri.getQueryParameter("recipient")
            ?: uri.getQueryParameter("payer_id")
            ?: uri.getQueryParameter("name")
        val lastSegment = pathSegments.lastOrNull()?.takeIf { it.isNotBlank() } ?: "PayPal Merchant"
        (recipientParam ?: lastSegment) to null
      }

      val finalAmount = uri.getQueryParameter("amount") ?: uri.getQueryParameter("am") ?: pathAmt
      val currency = uri.getQueryParameter("currencyCode") ?: uri.getQueryParameter("currency") ?: "USD"

      return QrPayload.PayPal(
          identifier = id,
          amount = finalAmount,
          currency = currency,
          rawUri = trimmed,
      )
    }

    return QrPayload.Unknown(trimmed)
  }

  // Lightweight parser for EMVCo Merchant-Presented QR (used by BharatQR & PayPal In-Store)
  private fun parseEmvCo(payload: String): QrPayload {
    if (payload.contains("A000000524", ignoreCase = true) || payload.contains("@")) {
      // Contains NPCI RID or VPA inside TLV tag 26/27/28
      val vpaRegex = Regex("([a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64})")
      val match = vpaRegex.find(payload)
      if (match != null) {
        return QrPayload.Upi(
            vpa = match.value,
            payeeName = null,
            amount = null,
            merchantCode = null,
            rawUri = payload,
        )
      }
    }
    if (payload.contains("paypal", ignoreCase = true)) {
      return QrPayload.PayPal(
          identifier = "EMVCO_PAYPAL_MERCHANT",
          amount = null,
          currency = null,
          rawUri = payload,
      )
    }
    return QrPayload.Unknown(payload)
  }

  /**
   * Directly transforms parsed payload to AirWallet's [PaymentDetails].
   */
  fun toPaymentDetails(
      payload: QrPayload,
      preferredMethod: PaymentMethod = PaymentMethod.PAYPAL_VAULT,
  ): PaymentDetails? {
    return when (payload) {
      is QrPayload.Upi -> {
        val amt = payload.amount?.toDoubleOrNull()
        val name = payload.payeeName?.takeIf { it.isNotBlank() } ?: payload.vpa
        PaymentDetails(
            payeeVpa = payload.vpa,
            payeeName = name,
            amount = amt,
            currency = "INR",
            note = "UPI Payment",
            rawQr = payload.rawUri,
            paymentMethod = PaymentMethod.UPI,
        )
      }
      is QrPayload.PayPal -> {
        val amt = payload.amount?.toDoubleOrNull()
        val cleanName = payload.identifier.replace("_", " ").capitalizeWords()
        PaymentDetails(
            payeeVpa = payload.identifier,
            payeeName = cleanName,
            amount = amt,
            currency = payload.currency ?: "USD",
            note = "PayPal Scan & Pay",
            rawQr = payload.rawUri,
            paymentMethod = PaymentMethod.PAYPAL_VAULT,
        )
      }
      is QrPayload.Unknown -> null
    }
  }

  private fun String.capitalizeWords(): String {
    return split(" ").joinToString(" ") { word ->
      word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
  }
}

