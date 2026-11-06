/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.payment

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a persisted transaction stored on-device.
 */
data class WalletTransaction(
    val id: String,
    val merchant: String,
    val rail: String, // "PayPal" or "UPI"
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SUCCESS",
    val fundingSource: String = "",
    val note: String = "",
    val category: String = inferCategory(merchant, note),
    val itemizedSummary: String = "",
    val referenceId: String = "", // e.g. PayPal Capture ID, Payout Batch ID, Invoice ID
) {
  fun toJson(): JSONObject {
    val json = JSONObject()
    json.put("id", id)
    json.put("merchant", merchant)
    json.put("rail", rail)
    json.put("amount", amount)
    json.put("timestamp", timestamp)
    json.put("status", status)
    json.put("fundingSource", fundingSource)
    json.put("note", note)
    json.put("category", category)
    json.put("itemizedSummary", itemizedSummary)
    json.put("referenceId", referenceId)
    return json
  }

  companion object {
    fun inferCategory(merchant: String, note: String = ""): String {
      val text = "$merchant $note".lowercase()
      return when {
        text.contains("food") || text.contains("cafe") || text.contains("coffee") ||
        text.contains("starbucks") || text.contains("burger") || text.contains("pizza") ||
        text.contains("restaurant") || text.contains("diner") || text.contains("bakery") ||
        text.contains("swiggy") || text.contains("zomato") || text.contains("doordash") ||
        text.contains("grocery") || text.contains("groceries") || text.contains("tea") ||
        text.contains("lunch") || text.contains("dinner") || text.contains("eats") ||
        text.contains("subway") || text.contains("bistro") || text.contains("sweetgreen") ||
        text.contains("chipotle") -> "Food & Dining"

        text.contains("friend") || text.contains("alex") || text.contains("maya") ||
        text.contains("devon") || text.contains("sarah") || text.contains("atharv") ||
        text.contains("sent to") || text.contains("transfer") || text.contains("split") ||
        text.contains("peer") || text.contains("p2p") || text.contains("@") -> "Friends & Transfer"

        text.contains("utilit") || text.contains("electric") || text.contains("water") ||
        text.contains("gas") || text.contains("power") || text.contains("wifi") ||
        text.contains("internet") || text.contains("broadband") || text.contains("recharge") ||
        text.contains("bill") || text.contains("rent") || text.contains("mobile") ||
        text.contains("airtel") || text.contains("jio") || text.contains("verizon") -> "Utilities & Bills"

        text.contains("shop") || text.contains("amazon") || text.contains("target") ||
        text.contains("walmart") || text.contains("apple") || text.contains("store") ||
        text.contains("mall") || text.contains("clothing") || text.contains("retail") ||
        text.contains("merchant") || text.contains("mart") -> "Shopping"

        text.contains("uber") || text.contains("lyft") || text.contains("transit") ||
        text.contains("metro") || text.contains("flight") || text.contains("train") ||
        text.contains("fuel") || text.contains("petrol") || text.contains("taxi") -> "Travel"

        else -> "Food & Dining"
      }
    }

    fun fromJson(json: JSONObject): WalletTransaction {
      val merchant = json.optString("merchant", "Merchant")
      val note = json.optString("note", "")
      val rawCategory = json.optString("category", "")
      val category = if (rawCategory.isNotBlank() && rawCategory != "Other") {
        rawCategory
      } else {
        inferCategory(merchant, note)
      }
      return WalletTransaction(
          id = json.optString("id", System.currentTimeMillis().toString()),
          merchant = merchant,
          rail = json.optString("rail", "PayPal"),
          amount = json.optDouble("amount", 0.0),
          timestamp = json.optLong("timestamp", System.currentTimeMillis()),
          status = json.optString("status", "SUCCESS"),
          fundingSource = json.optString("fundingSource", ""),
          note = note,
          category = category,
          itemizedSummary = json.optString("itemizedSummary", ""),
          referenceId = json.optString("referenceId", ""),
      )
    }
  }
}

/**
 * On-device persistence repository for transaction history and balance.
 * Uses Android private SharedPreferences with structured JSON serialization.
 */
class TransactionRepository(context: Context) {
  companion object {
    private const val TAG = "AirWallet:TxRepo"
    private const val PREFS_NAME = "airwallet_storage"
    private const val KEY_TRANSACTIONS = "transactions_history"
    private const val KEY_BALANCE = "account_balance"
    private const val INITIAL_BALANCE = 250.00 // Default starting wallet balance
  }

  private val prefs: SharedPreferences =
      context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _transactions = MutableStateFlow<List<WalletTransaction>>(emptyList())
  val transactions: StateFlow<List<WalletTransaction>> = _transactions.asStateFlow()

  private val _balance = MutableStateFlow<Double>(INITIAL_BALANCE)
  val balance: StateFlow<Double> = _balance.asStateFlow()

  init {
    loadFromDisk()
  }

  private fun loadFromDisk() {
    try {
      val savedBalance = if (prefs.contains(KEY_BALANCE)) {
        prefs.getFloat(KEY_BALANCE, INITIAL_BALANCE.toFloat()).toDouble()
      } else {
        INITIAL_BALANCE
      }
      _balance.value = savedBalance

      val rawJson = prefs.getString(KEY_TRANSACTIONS, null)
      if (!rawJson.isNullOrBlank()) {
        val jsonArray = JSONArray(rawJson)
        val list = mutableListOf<WalletTransaction>()
        for (i in 0 until jsonArray.length()) {
          val obj = jsonArray.getJSONObject(i)
          list.add(WalletTransaction.fromJson(obj))
        }
        _transactions.value = list
        Log.i(TAG, "Loaded ${list.size} transactions from on-device storage")
      } else {
        val initialList = createDefaultDemoTransactions()
        _transactions.value = initialList
        saveToDisk(initialList, savedBalance)
        Log.i(TAG, "Initialized default demo transaction history on device (${initialList.size} transactions)")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error loading saved transactions: ${e.message}", e)
      val initialList = createDefaultDemoTransactions()
      _transactions.value = initialList
    }
  }

  private fun createDefaultDemoTransactions(): List<WalletTransaction> {
    val now = System.currentTimeMillis()
    val oneDayMs = 24 * 60 * 60 * 1000L
    return listOf(
        WalletTransaction(
            id = "demo_1",
            merchant = "Starbucks Reserve",
            rail = "PayPal",
            amount = 7.40,
            timestamp = now - (3 * oneDayMs),
            note = "Morning coffee & croissant",
            category = "Food & Dining"
        ),
        WalletTransaction(
            id = "demo_2",
            merchant = "Maya Patel",
            rail = "UPI",
            amount = 32.50,
            timestamp = now - (2 * oneDayMs) - (2 * 3600 * 1000L),
            note = "Dinner split",
            category = "Friends & Transfer"
        ),
        WalletTransaction(
            id = "demo_3",
            merchant = "Sweetgreen",
            rail = "PayPal",
            amount = 16.80,
            timestamp = now - (2 * oneDayMs),
            note = "Healthy lunch bowl",
            category = "Food & Dining"
        ),
        WalletTransaction(
            id = "demo_4",
            merchant = "Power & Electric Co",
            rail = "UPI",
            amount = 48.20,
            timestamp = now - (1 * oneDayMs) - (4 * 3600 * 1000L),
            note = "Monthly electricity utility bill",
            category = "Utilities & Bills"
        ),
        WalletTransaction(
            id = "demo_5",
            merchant = "Alex Rivera",
            rail = "PayPal",
            amount = 25.00,
            timestamp = now - (1 * oneDayMs),
            note = "Movie tickets transfer",
            category = "Friends & Transfer"
        ),
        WalletTransaction(
            id = "demo_6",
            merchant = "Chipotle Mexican Grill",
            rail = "UPI",
            amount = 14.60,
            timestamp = now - (3 * 3600 * 1000L),
            note = "Burrito bowl",
            category = "Food & Dining"
        ),
        WalletTransaction(
            id = "demo_7",
            merchant = "Target",
            rail = "PayPal",
            amount = 28.90,
            timestamp = now - (1 * 3600 * 1000L),
            note = "Weekly essentials shopping",
            category = "Shopping"
        ),
    )
  }

  @Synchronized
  fun addTransaction(tx: WalletTransaction) {
    val current = _transactions.value.toMutableList()
    // Deduplicate if already present
    if (current.any { it.id == tx.id }) return
    current.add(0, tx)
    _transactions.value = current

    val newBalance = (_balance.value - tx.amount).coerceAtLeast(0.0)
    _balance.value = newBalance

    saveToDisk(current, newBalance)
    Log.i(TAG, "Stored transaction: ${tx.id} for $${tx.amount} to ${tx.merchant} (${tx.rail})")
  }

  @Synchronized
  fun clearTransactions() {
    _transactions.value = emptyList()
    _balance.value = INITIAL_BALANCE
    prefs.edit()
        .remove(KEY_TRANSACTIONS)
        .putFloat(KEY_BALANCE, INITIAL_BALANCE.toFloat())
        .apply()
    Log.i(TAG, "Cleared all transaction records from on-device storage")
  }

  private fun saveToDisk(list: List<WalletTransaction>, newBalance: Double) {
    try {
      val array = JSONArray()
      for (item in list) {
        array.put(item.toJson())
      }
      prefs.edit()
          .putString(KEY_TRANSACTIONS, array.toString())
          .putFloat(KEY_BALANCE, newBalance.toFloat())
          .apply()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to persist transactions to disk: ${e.message}", e)
    }
  }
}

