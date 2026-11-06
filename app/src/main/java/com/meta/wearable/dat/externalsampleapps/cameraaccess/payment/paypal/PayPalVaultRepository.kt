/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages vaulted PayPal payment credentials locally on the device.
 * Adheres to PayPal Vaulted Best Practice for O2O / smart wearables:
 * https://developer.paypal.com/v5/checkout/best-practice/vaulted
 */
class PayPalVaultRepository(context: Context) {
  private val prefs: SharedPreferences =
      context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _vaultStatus = MutableStateFlow<PayPalVaultStatus>(PayPalVaultStatus.Unlinked)
  val vaultStatus: StateFlow<PayPalVaultStatus> = _vaultStatus.asStateFlow()

  init {
    loadSavedVault()
  }

  private fun loadSavedVault() {
    val isLinked = prefs.getBoolean(KEY_IS_LINKED, false)
    if (isLinked) {
      val vaultId = prefs.getString(KEY_VAULT_ID, "") ?: ""
      val email = prefs.getString(KEY_EMAIL, "") ?: ""
      val name = prefs.getString(KEY_NAME, "") ?: ""
      val payerId = prefs.getString(KEY_PAYER_ID, "") ?: ""
      val fundingSource = prefs.getString(KEY_FUNDING, "PayPal Balance") ?: "PayPal Balance"
      val cardLast4 = prefs.getString(KEY_CARD_LAST4, "4242") ?: "4242"
      val vaultedAt = prefs.getLong(KEY_VAULTED_AT, System.currentTimeMillis())

      if (vaultId.isNotEmpty() && email.isNotEmpty()) {
        _vaultStatus.value = PayPalVaultStatus.Vaulted(
            PayPalVaultAccount(
                vaultId = vaultId,
                customerEmail = email,
                payerName = name,
                payerId = payerId,
                fundingSource = fundingSource,
                cardLast4 = cardLast4,
                isLinked = true,
                vaultedAt = vaultedAt,
            )
        )
        return
      }
    }

    // Default pre-provisioned Sandbox Vault account for instant testing on glasses
    linkDefaultSandboxVault()
  }

  /**
   * Sets up or restores a default sandbox vaulted account so testing works out of the box.
   */
  fun linkDefaultSandboxVault() {
    val defaultAccount = PayPalVaultAccount(
        vaultId = "v-sandbox-meta-glasses-" + (1000..9999).random(),
        customerEmail = "wearable-buyer@sandbox.paypal.com",
        payerName = "Meta Wearer",
        payerId = "PAYER-" + (100000..999999).random(),
        fundingSource = "PayPal Balance & Visa •••• 4242",
        cardLast4 = "4242",
        isLinked = true,
        vaultedAt = System.currentTimeMillis(),
    )
    saveAccount(defaultAccount)
  }

  /**
   * Links a custom PayPal sandbox account with vaulted payment source.
   */
  fun linkCustomAccount(
      email: String,
      name: String,
      fundingSource: String = "PayPal Balance",
      cardLast4: String = "4242",
  ) {
    val customAccount = PayPalVaultAccount(
        vaultId = "v-" + System.currentTimeMillis().toString(36),
        customerEmail = email.trim(),
        payerName = name.trim().ifEmpty { "PayPal Buyer" },
        payerId = "PAYER-" + (100000..999999).random(),
        fundingSource = fundingSource,
        cardLast4 = cardLast4,
        isLinked = true,
        vaultedAt = System.currentTimeMillis(),
    )
    saveAccount(customAccount)
  }

  fun unlinkVault() {
    prefs.edit().clear().apply()
    _vaultStatus.value = PayPalVaultStatus.Unlinked
    Log.i(TAG, "PayPal Vault unlinked successfully")
  }

  private fun saveAccount(account: PayPalVaultAccount) {
    prefs.edit()
        .putBoolean(KEY_IS_LINKED, true)
        .putString(KEY_VAULT_ID, account.vaultId)
        .putString(KEY_EMAIL, account.customerEmail)
        .putString(KEY_NAME, account.payerName)
        .putString(KEY_PAYER_ID, account.payerId)
        .putString(KEY_FUNDING, account.fundingSource)
        .putString(KEY_CARD_LAST4, account.cardLast4)
        .putLong(KEY_VAULTED_AT, account.vaultedAt)
        .apply()

    _vaultStatus.value = PayPalVaultStatus.Vaulted(account)
    Log.i(TAG, "PayPal Vault account saved: ${account.customerEmail} (ID: ${account.vaultId})")
  }

  fun getVaultedAccount(): PayPalVaultAccount? {
    return when (val s = _vaultStatus.value) {
      is PayPalVaultStatus.Vaulted -> s.account
      else -> null
    }
  }

  companion object {
    private const val TAG = "PayPalVaultRepository"
    private const val PREFS_NAME = "airwallet_paypal_vault"
    private const val KEY_IS_LINKED = "is_linked"
    private const val KEY_VAULT_ID = "vault_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_NAME = "name"
    private const val KEY_PAYER_ID = "payer_id"
    private const val KEY_FUNDING = "funding"
    private const val KEY_CARD_LAST4 = "card_last4"
    private const val KEY_VAULTED_AT = "vaulted_at"
  }
}

