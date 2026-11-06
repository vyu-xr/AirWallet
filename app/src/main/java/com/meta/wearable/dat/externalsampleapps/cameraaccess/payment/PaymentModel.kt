/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.payment

import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PayPalItemizedBreakdown

data class PaymentDetails(
    val payeeVpa: String,
    val payeeName: String,
    val amount: Double?,
    val currency: String = "USD",
    val note: String = "",
    val rawQr: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.PAYPAL_VAULT,
    val itemizedBreakdown: PayPalItemizedBreakdown? = null,
)

sealed interface PaymentState {
  object Idle : PaymentState

  object Scanning : PaymentState

  data class EnterAmount(val details: PaymentDetails) : PaymentState

  data class ConfirmPayment(val details: PaymentDetails) : PaymentState

  data class Processing(val details: PaymentDetails) : PaymentState

  data class Success(
      val details: PaymentDetails,
      val transactionId: String,
      val timestamp: Long,
      val fundingSource: String = "PayPal Balance",
      val itemizedBreakdown: PayPalItemizedBreakdown? = null,
  ) : PaymentState

  data class Error(val message: String) : PaymentState
}
