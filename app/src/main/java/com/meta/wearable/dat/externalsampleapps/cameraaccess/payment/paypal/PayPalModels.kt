/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal

/**
 * Supported payment rails in AirWallet.
 */
enum class PaymentMethod(val displayName: String, val defaultCurrency: String, val symbol: String) {
  PAYPAL_VAULT("PayPal Vault", "USD", "$"),
  UPI("UPI Pay", "INR", "₹"),
}

/**
 * Vaulted payment source details stored on device for frictionless O2O checkout.
 * Per PayPal Vault Best Practice (https://developer.paypal.com/v5/checkout/best-practice/vaulted):
 * Pre-authorizes high-frequency, low-friction smart glasses payments without requiring
 * re-authentication on every micro-LED tap or voice confirmation.
 */
data class PayPalVaultAccount(
    val vaultId: String,
    val customerEmail: String,
    val payerName: String,
    val payerId: String,
    val fundingSource: String = "PayPal Balance",
    val cardLast4: String = "4242",
    val isLinked: Boolean = true,
    val vaultedAt: Long = System.currentTimeMillis(),
)

/**
 * Current status of the PayPal Vault integration on this device.
 */
sealed interface PayPalVaultStatus {
  object Unlinked : PayPalVaultStatus

  data class Vaulted(val account: PayPalVaultAccount) : PayPalVaultStatus

  object Processing : PayPalVaultStatus

  data class Error(val message: String) : PayPalVaultStatus
}

/**
 * Result returned after capturing an order via PayPal Vault.
 */
data class PayPalOrderResult(
    val isSuccess: Boolean,
    val transactionId: String,
    val amount: Double,
    val currency: String,
    val payee: String,
    val fundingSource: String,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    val itemizedBreakdown: PayPalItemizedBreakdown? = null,
)

/**
 * Itemized product or service detail conforming to PayPal Orders v2 API:
 * POST /v2/checkout/orders -> purchase_units[0].items
 */
data class PayPalOrderItem(
    val name: String,
    val quantity: Int = 1,
    val unitAmount: Double,
    val sku: String = "",
    val category: String = "PHYSICAL_GOODS",
    val tax: Double = 0.0,
)

/**
 * Full itemized receipt breakdown matching PayPal purchase_units[0].amount.breakdown
 */
data class PayPalItemizedBreakdown(
    val items: List<PayPalOrderItem>,
    val itemTotal: Double,
    val taxTotal: Double = 0.0,
    val tipAmount: Double = 0.0,
    val grandTotal: Double = itemTotal + taxTotal + tipAmount,
)

/**
 * Invoice line item conforming to PayPal Invoicing v2 API:
 * POST /v2/invoicing/invoices -> items
 */
data class PayPalInvoiceItem(
    val name: String,
    val quantity: Int = 1,
    val unitAmount: Double,
    val taxPercent: Double = 0.0,
)

/**
 * Represents a PayPal Invoicing v2 invoice record generated for bill splitting or payment requests.
 */
data class PayPalInvoice(
    val invoiceId: String,
    val invoiceNumber: String,
    val recipientEmail: String,
    val recipientName: String,
    val items: List<PayPalInvoiceItem>,
    val totalAmount: Double,
    val currency: String = "USD",
    val note: String = "",
    val status: String = "SENT", // DRAFT, SENT, PAID, CANCELLED
    val paymentUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Outcome of an AI bill split calculation across multiple friends with associated PayPal invoices.
 */
data class BillSplitResult(
    val billTotal: Double,
    val friendsInvolved: List<String>,
    val perPersonAmount: Double,
    val invoices: List<PayPalInvoice>,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Itemized disbursement for PayPal Payouts v1 API:
 * POST /v1/payments/payouts -> items
 */
data class PayPalPayoutItem(
    val recipientEmail: String,
    val recipientName: String,
    val amount: Double,
    val currency: String = "USD",
    val note: String = "AirWallet Wearable Instant Payout",
    val senderItemId: String = "",
)

/**
 * Result of a batch or direct payout via PayPal Payouts v1 API.
 */
data class PayPalPayoutBatch(
    val batchId: String,
    val batchStatus: String = "SUCCESS", // PENDING, SUCCESS, DENIED
    val recipientEmail: String,
    val recipientName: String,
    val amount: Double,
    val currency: String = "USD",
    val senderNote: String = "",
    val fee: Double = 0.25,
    val timestamp: Long = System.currentTimeMillis(),
)


