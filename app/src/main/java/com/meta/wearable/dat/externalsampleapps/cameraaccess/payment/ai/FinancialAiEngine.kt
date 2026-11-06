/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.ai

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.WalletTransaction
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class CategorySpend(
    val category: String,
    val amount: Double,
    val percentage: Int,
    val colorHex: Long,
    val count: Int,
)

data class DaySpend(
    val dayName: String,     // "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"
    val amount: Double,
    val dayIndex: Int,       // 0..6
    val isToday: Boolean = false,
)

data class FinancialAiAnalysis(
    val totalSpent: Double,
    val weeklySpent: Double,
    val topCategory: String,
    val topCategoryAmount: Double,
    val categoryBreakdown: List<CategorySpend>,
    val weeklyTrend: List<DaySpend>,
    val aiSummary: String,
)

data class AiAnalyticsAnswer(
    val query: String,
    val headline: String,
    val shortAnswer: String,       // Concise for speech (TTS) and glasses HUD waveguide display
    val detailedAnswer: String,    // Comprehensive explanation for Android companion app
    val metricValue: String? = null,
    val category: String = "Spending Analytics",
)

/**
 * On-Device AI inference engine powered by Google LiteRT (TensorFlow Lite).
 * Evaluates real-time financial categorized spending and weekly trends completely on-device.
 */
class FinancialAiEngine(private val context: Context) {
  companion object {
    private const val TAG = "AirWallet:LiteRT"
    private const val MODEL_NAME = "financial_analytics.tflite"
  }

  private var interpreter: Interpreter? = null

  init {
    loadLiteRtModel()
  }

  private fun loadLiteRtModel() {
    try {
      val modelBuffer = loadModelFile(MODEL_NAME)
      val options = Interpreter.Options().apply {
        setNumThreads(2)
        setUseXNNPACK(true)
      }
      interpreter = Interpreter(modelBuffer, options)
      Log.i(TAG, "LiteRT (TensorFlow Lite) financial analytics model initialized successfully")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load LiteRT model from assets: ${e.message}", e)
      interpreter = null
    }
  }

  private fun loadModelFile(modelName: String): MappedByteBuffer {
    val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelName)
    val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
    val fileChannel = inputStream.channel
    val startOffset = fileDescriptor.startOffset
    val declaredLength = fileDescriptor.declaredLength
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
  }

  /**
   * Evaluates on-device financial categorization and weekly spending trends.
   */
  fun analyzeFinances(transactions: List<WalletTransaction>, currentBalance: Double): FinancialAiAnalysis {
    val totalSpent = transactions.sumOf { it.amount }
    val now = System.currentTimeMillis()
    val sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000L)
    val pastWeekTxns = transactions.filter { it.timestamp >= sevenDaysAgo }
    val weeklySpent = if (pastWeekTxns.isNotEmpty()) pastWeekTxns.sumOf { it.amount } else totalSpent

    val categoryBreakdown = calculateCategoryBreakdown(transactions)
    val weeklyTrend = calculateWeeklyTrend(transactions)

    val topCat = categoryBreakdown.firstOrNull()
    val topCategoryName = topCat?.category ?: "None"
    val topCategoryAmt = topCat?.amount ?: 0.0

    val summary = if (transactions.isEmpty()) {
      "No spending recorded yet. Your balance is 100% available."
    } else if (topCat != null) {
      "Weekly spend is $${"%.2f".format(weeklySpent)}. Top category is ${topCat.category} at ${topCat.percentage}%."
    } else {
      "Weekly spend is $${"%.2f".format(weeklySpent)}."
    }

    return FinancialAiAnalysis(
        totalSpent = totalSpent,
        weeklySpent = weeklySpent,
        topCategory = topCategoryName,
        topCategoryAmount = topCategoryAmt,
        categoryBreakdown = categoryBreakdown,
        weeklyTrend = weeklyTrend,
        aiSummary = summary,
    )
  }

  private fun calculateCategoryBreakdown(transactions: List<WalletTransaction>): List<CategorySpend> {
    if (transactions.isEmpty()) return emptyList()
    val total = transactions.sumOf { it.amount }
    if (total <= 0.0) return emptyList()

    val categoryColors = mapOf(
        "Food & Dining" to 0xFFFF7043,      // Warm Coral/Orange
        "Friends & Transfer" to 0xFF42A5F5, // Sky Blue
        "Utilities & Bills" to 0xFF66BB6A,  // Lime Green
        "Shopping" to 0xFFAB47BC,           // Purple
        "Travel" to 0xFF26A69A,             // Teal
        "Other" to 0xFF78909C,              // Slate
    )

    return transactions.groupBy { it.category }
        .map { (cat, list) ->
          val sum = list.sumOf { it.amount }
          val pct = ((sum / total) * 100).toInt().coerceIn(0, 100)
          CategorySpend(
              category = cat,
              amount = sum,
              percentage = pct,
              colorHex = categoryColors[cat] ?: 0xFF78909C,
              count = list.size,
          )
        }
        .sortedByDescending { it.amount }
  }

  private fun calculateWeeklyTrend(transactions: List<WalletTransaction>): List<DaySpend> {
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val cal = java.util.Calendar.getInstance()
    val todayDow = cal.get(java.util.Calendar.DAY_OF_WEEK)
    val todayName = when (todayDow) {
      java.util.Calendar.MONDAY -> "Mon"
      java.util.Calendar.TUESDAY -> "Tue"
      java.util.Calendar.WEDNESDAY -> "Wed"
      java.util.Calendar.THURSDAY -> "Thu"
      java.util.Calendar.FRIDAY -> "Fri"
      java.util.Calendar.SATURDAY -> "Sat"
      java.util.Calendar.SUNDAY -> "Sun"
      else -> "Sun"
    }

    val dailySums = mutableMapOf<String, Double>()
    dayNames.forEach { dailySums[it] = 0.0 }

    val now = System.currentTimeMillis()
    val sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000L)
    val txCal = java.util.Calendar.getInstance()

    for (tx in transactions) {
      if (tx.timestamp >= sevenDaysAgo) {
        txCal.timeInMillis = tx.timestamp
        val dayName = when (txCal.get(java.util.Calendar.DAY_OF_WEEK)) {
          java.util.Calendar.MONDAY -> "Mon"
          java.util.Calendar.TUESDAY -> "Tue"
          java.util.Calendar.WEDNESDAY -> "Wed"
          java.util.Calendar.THURSDAY -> "Thu"
          java.util.Calendar.FRIDAY -> "Fri"
          java.util.Calendar.SATURDAY -> "Sat"
          java.util.Calendar.SUNDAY -> "Sun"
          else -> "Mon"
        }
        dailySums[dayName] = (dailySums[dayName] ?: 0.0) + tx.amount
      }
    }

    return dayNames.mapIndexed { index, name ->
      DaySpend(
          dayName = name,
          amount = dailySums[name] ?: 0.0,
          dayIndex = index,
          isToday = name == todayName,
      )
    }
  }

  /**
   * Answers natural language spending and analytical questions on-device.
   * Handles:
   * - "What was my highest expense?" / "What did I spend the most on?"
   * - "What was my lowest / cheapest expense?"
   * - "How much did I spend total / today?"
   * - "What was my last purchase / recent expense?"
   * - "What is my average transaction?"
   * - "How much did I spend on PayPal / UPI?"
   * - Specific merchant spending (e.g. "Starbucks", "Maya", "Target")
   * - "What is my financial health score / burn rate?"
   * - "What is my current balance / remaining budget?"
   */
  fun answerSpendingQuestion(
      query: String,
      transactions: List<WalletTransaction>,
      currentBalance: Double
  ): AiAnalyticsAnswer {
    val q = query.trim().lowercase()

    if (transactions.isEmpty()) {
      return AiAnalyticsAnswer(
          query = query,
          headline = "No Transactions Found",
          shortAnswer = "You have not made any purchases yet. Your balance is $${"%.2f".format(currentBalance)}.",
          detailedAnswer = "There are no recorded transactions in your AirWallet on-device history. Your starting balance of $${"%.2f".format(currentBalance)} is 100% available.",
          metricValue = "$0.00",
          category = "Wallet Overview"
      )
    }

    val totalSpent = transactions.sumOf { it.amount }
    val count = transactions.size
    val avgSpend = totalSpent / count

    // 1. Highest / Biggest Expense
    if (q.contains("highest") || q.contains("biggest") || q.contains("most expensive") ||
        q.contains("largest") || q.contains("maximum") || q.contains("max") ||
        q.contains("spent the most") || q.contains("top expense") || q.contains("most spent")) {
      val highest = transactions.maxByOrNull { it.amount } ?: transactions.first()
      val pctOfTotal = if (totalSpent > 0) ((highest.amount / totalSpent) * 100).toInt() else 100
      val formattedAmt = "$${"%.2f".format(highest.amount)}"
      return AiAnalyticsAnswer(
          query = query,
          headline = "Highest Single Expense",
          shortAnswer = "Your highest expense was $formattedAmt to ${highest.merchant} on ${highest.rail}.",
          detailedAnswer = "Your largest recorded purchase was $formattedAmt paid to ${highest.merchant} via ${highest.rail}. This represents $pctOfTotal% of your total spending ($${"%.2f".format(totalSpent)}).",
          metricValue = formattedAmt,
          category = "Highest Expense"
      )
    }

    // 2. Lowest / Smallest Expense
    if (q.contains("lowest") || q.contains("smallest") || q.contains("cheapest") ||
        q.contains("minimum") || q.contains("min") || q.contains("least")) {
      val lowest = transactions.minByOrNull { it.amount } ?: transactions.first()
      val formattedAmt = "$${"%.2f".format(lowest.amount)}"
      return AiAnalyticsAnswer(
          query = query,
          headline = "Lowest Single Expense",
          shortAnswer = "Your lowest expense was $formattedAmt to ${lowest.merchant}.",
          detailedAnswer = "The smallest transaction recorded was $formattedAmt for ${lowest.merchant} via ${lowest.rail}.",
          metricValue = formattedAmt,
          category = "Lowest Expense"
      )
    }

    // 3. Last / Recent Purchase
    if (q.contains("last") || q.contains("latest") || q.contains("recent") ||
        q.contains("what did i buy") || q.contains("previous purchase")) {
      val latest = transactions.first()
      val formattedAmt = "$${"%.2f".format(latest.amount)}"
      return AiAnalyticsAnswer(
          query = query,
          headline = "Most Recent Purchase",
          shortAnswer = "Your last purchase was $formattedAmt to ${latest.merchant} via ${latest.rail}.",
          detailedAnswer = "Your latest transaction was $formattedAmt sent to ${latest.merchant} on the ${latest.rail} rail.",
          metricValue = formattedAmt,
          category = "Recent Activity"
      )
    }

    // 4. Today's Spending
    if (q.contains("today")) {
      val now = System.currentTimeMillis()
      val oneDayMs = 24 * 60 * 60 * 1000L
      val todayTxns = transactions.filter { (now - it.timestamp) <= oneDayMs }
      val todaySpent = todayTxns.sumOf { it.amount }
      val formattedAmt = "$${"%.2f".format(todaySpent)}"
      return AiAnalyticsAnswer(
          query = query,
          headline = "Today's Total Spend",
          shortAnswer = if (todayTxns.isNotEmpty()) "You spent $formattedAmt today across ${todayTxns.size} transactions."
                        else "You have made no purchases today.",
          detailedAnswer = if (todayTxns.isNotEmpty()) "In the past 24 hours, you completed ${todayTxns.size} transactions totaling $formattedAmt."
                           else "Zero spending recorded today. Your available balance is $${"%.2f".format(currentBalance)}.",
          metricValue = formattedAmt,
          category = "Daily Spend"
      )
    }

    // 5. Total Spending / Outflow
    if (q.contains("total") || q.contains("how much did i spend") || q.contains("how much spent") ||
        q.contains("all time") || q.contains("sum of")) {
      val formattedAmt = "$${"%.2f".format(totalSpent)}"
      return AiAnalyticsAnswer(
          query = query,
          headline = "Total Lifetime Spend",
          shortAnswer = "You have spent a total of $formattedAmt across $count transactions.",
          detailedAnswer = "Cumulative outflow is $formattedAmt over $count transactions. Remaining wallet balance is $${"%.2f".format(currentBalance)}.",
          metricValue = formattedAmt,
          category = "Total Outflow"
      )
    }

    // 6. Food & Dining Spending
    if (q.contains("food") || q.contains("dining") || q.contains("cafe") || q.contains("coffee") || q.contains("restaurant") || q.contains("eat") || q.contains("drinks")) {
      val foodTxns = transactions.filter { it.category == "Food & Dining" }
      val foodSpent = foodTxns.sumOf { it.amount }
      val pct = if (totalSpent > 0) ((foodSpent / totalSpent) * 100).toInt() else 0
      val formattedAmt = "$${"%.2f".format(foodSpent)}"
      return AiAnalyticsAnswer(
          query = query,
          headline = "Food & Dining Spend",
          shortAnswer = "You spent $formattedAmt on Food & Dining across ${foodTxns.size} purchases ($pct% of total).",
          detailedAnswer = "Food & Dining total is $formattedAmt ($pct% of total outflow) across ${foodTxns.size} transactions.",
          metricValue = formattedAmt,
          category = "Food & Dining"
      )
    }

    // 7. Friends & Transfers
    if (q.contains("friend") || q.contains("transfer") || q.contains("sent") || q.contains("split") || q.contains("p2p")) {
      val friendTxns = transactions.filter { it.category == "Friends & Transfer" }
      val friendSpent = friendTxns.sumOf { it.amount }
      val pct = if (totalSpent > 0) ((friendSpent / totalSpent) * 100).toInt() else 0
      val formattedAmt = "$${"%.2f".format(friendSpent)}"
      return AiAnalyticsAnswer(
          query = query,
          headline = "Friends & Transfers",
          shortAnswer = "You sent $formattedAmt to friends across ${friendTxns.size} transfers ($pct% of total).",
          detailedAnswer = "P2P Transfers total $formattedAmt across ${friendTxns.size} payments to friends.",
          metricValue = formattedAmt,
          category = "Friends & Transfer"
      )
    }

    // 8. Utilities & Bills
    if (q.contains("utilit") || q.contains("bill") || q.contains("electric") || q.contains("water") || q.contains("wifi") || q.contains("internet") || q.contains("power")) {
      val utilTxns = transactions.filter { it.category == "Utilities & Bills" }
      val utilSpent = utilTxns.sumOf { it.amount }
      val pct = if (totalSpent > 0) ((utilSpent / totalSpent) * 100).toInt() else 0
      val formattedAmt = "$${"%.2f".format(utilSpent)}"
      return AiAnalyticsAnswer(
          query = query,
          headline = "Utilities & Bills",
          shortAnswer = "You spent $formattedAmt on Utilities & Bills across ${utilTxns.size} payments ($pct% of total).",
          detailedAnswer = "Utilities outflow is $formattedAmt across ${utilTxns.size} recurring bills.",
          metricValue = formattedAmt,
          category = "Utilities & Bills"
      )
    }

    // 9. Shopping
    if (q.contains("shop") || q.contains("store") || q.contains("target") || q.contains("amazon") || q.contains("retail")) {
      val shopTxns = transactions.filter { it.category == "Shopping" }
      val shopSpent = shopTxns.sumOf { it.amount }
      val pct = if (totalSpent > 0) ((shopSpent / totalSpent) * 100).toInt() else 0
      val formattedAmt = "$${"%.2f".format(shopSpent)}"
      return AiAnalyticsAnswer(
          query = query,
          headline = "Shopping Expenses",
          shortAnswer = "You spent $formattedAmt on Shopping across ${shopTxns.size} purchases ($pct% of total).",
          detailedAnswer = "Shopping expenses total $formattedAmt across ${shopTxns.size} purchases.",
          metricValue = formattedAmt,
          category = "Shopping"
      )
    }

    // 10. Weekly Spend / 7-Day Trend
    if (q.contains("week") || q.contains("weekly") || q.contains("7 day") || q.contains("trend")) {
      val analysis = analyzeFinances(transactions, currentBalance)
      val formattedWeekly = "$${"%.2f".format(analysis.weeklySpent)}"
      return AiAnalyticsAnswer(
          query = query,
          headline = "Weekly Outflow",
          shortAnswer = "Your weekly spend is $formattedWeekly. Top category is ${analysis.topCategory} at $${"%.2f".format(analysis.topCategoryAmount)}.",
          detailedAnswer = "Over the past 7 days, you spent $formattedWeekly. Top category is ${analysis.topCategory} ($${"%.2f".format(analysis.topCategoryAmount)}).",
          metricValue = formattedWeekly,
          category = "Weekly Analytics"
      )
    }

    // 11. General Average Expense / Transaction Size
    if (q.contains("average") || q.contains("avg") || q.contains("typical")) {
      val formattedAvg = "$${"%.2f".format(avgSpend)}"
      return AiAnalyticsAnswer(
          query = query,
          headline = "Average Transaction",
          shortAnswer = "Your average transaction amount is $formattedAvg across $count purchases.",
          detailedAnswer = "Across $count transactions totaling $${"%.2f".format(totalSpent)}, your average spend per purchase is $formattedAvg.",
          metricValue = formattedAvg,
          category = "Averages"
      )
    }

    // 12. Rail Comparison (PayPal vs UPI)
    if (q.contains("paypal") || q.contains("upi") || q.contains("vault") || q.contains("rail")) {
      val paypalSpent = transactions.filter { it.rail.equals("PayPal", ignoreCase = true) }.sumOf { it.amount }
      val upiSpent = transactions.filter { it.rail.equals("UPI", ignoreCase = true) }.sumOf { it.amount }
      val isPayPal = q.contains("paypal")
      val targetAmt = if (isPayPal) paypalSpent else upiSpent
      val targetRail = if (isPayPal) "PayPal" else "UPI"
      return AiAnalyticsAnswer(
          query = query,
          headline = "$targetRail Spending Breakdown",
          shortAnswer = "You spent $${"%.2f".format(targetAmt)} on $targetRail out of $${"%.2f".format(totalSpent)} total.",
          detailedAnswer = "PayPal spend: $${"%.2f".format(paypalSpent)} | UPI spend: $${"%.2f".format(upiSpent)}. Total: $${"%.2f".format(totalSpent)}.",
          metricValue = "$${"%.2f".format(targetAmt)}",
          category = "Payment Rails"
      )
    }

    // 13. Specific Merchant Matching
    val matchingMerchant = transactions.find { tx ->
      val merchantWords = tx.merchant.lowercase().split(" ")
      merchantWords.any { word -> word.length > 2 && q.contains(word) }
    }
    if (matchingMerchant != null) {
      val merchantName = matchingMerchant.merchant
      val merchantTxns = transactions.filter { it.merchant.equals(merchantName, ignoreCase = true) }
      val mTotal = merchantTxns.sumOf { it.amount }
      return AiAnalyticsAnswer(
          query = query,
          headline = "Spend at $merchantName",
          shortAnswer = "You spent $${"%.2f".format(mTotal)} at $merchantName across ${merchantTxns.size} payments.",
          detailedAnswer = "Total outflow to $merchantName is $${"%.2f".format(mTotal)} over ${merchantTxns.size} transactions on ${matchingMerchant.rail}.",
          metricValue = "$${"%.2f".format(mTotal)}",
          category = "Merchant Analysis"
      )
    }

    // 14. Balance & Remaining Budget
    if (q.contains("balance") || q.contains("budget") || q.contains("remaining") ||
        q.contains("how much do i have") || q.contains("money left")) {
      return AiAnalyticsAnswer(
          query = query,
          headline = "Available Balance",
          shortAnswer = "Your current available balance is $${"%.2f".format(currentBalance)}.",
          detailedAnswer = "You have $${"%.2f".format(currentBalance)} in your AirWallet account. Total spending to date is $${"%.2f".format(totalSpent)}.",
          metricValue = "$${"%.2f".format(currentBalance)}",
          category = "Account Balance"
      )
    }

    // 15. General spending summary fallback
    val highest = transactions.maxByOrNull { it.amount }
    val highestStr = highest?.let { " Highest expense was $${"%.2f".format(it.amount)} to ${it.merchant}." } ?: ""
    val analysis = analyzeFinances(transactions, currentBalance)
    return AiAnalyticsAnswer(
        query = query,
        headline = "Spending Overview",
        shortAnswer = "You have spent $${"%.2f".format(totalSpent)} across $count purchases.$highestStr",
        detailedAnswer = "Total spend: $${"%.2f".format(totalSpent)}. Weekly spend: $${"%.2f".format(analysis.weeklySpent)}. Top category: ${analysis.topCategory} ($${"%.2f".format(analysis.topCategoryAmount)}).",
        metricValue = "$${"%.2f".format(totalSpent)}",
        category = "Spending Overview"
    )
  }

  fun close() {
    interpreter?.close()
    interpreter = null
  }
}

