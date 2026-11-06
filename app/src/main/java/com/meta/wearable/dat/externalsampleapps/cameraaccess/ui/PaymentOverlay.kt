/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payment
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.PaymentDetails
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.PaymentState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PayPalVaultAccount
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PayPalVaultStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QrScanReticle(
    isScanning: Boolean,
    onScanNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "scanner_laser")
  val laserOffset by transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
          animation = tween(1800, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
      ),
      label = "laser_pos",
  )

  Box(
      modifier = modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
  ) {
    // Reticle frame
    Box(
        modifier = Modifier
            .size(240.dp)
            .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(4.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
      if (isScanning) {
        // Laser line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .offset(y = (230 * laserOffset).dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0xFF00E676), Color.Transparent)
                    )
                )
        )
      }
    }

    // Floating Scanner Badge / Trigger Button
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .offset(y = 150.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Surface(
          shape = RoundedCornerShape(24.dp),
          color = Color.Black.copy(alpha = 0.7f),
          modifier = Modifier.clickable { onScanNow() },
      ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
              imageVector = Icons.Default.QrCodeScanner,
              contentDescription = null,
              tint = Color(0xFF00E676),
              modifier = Modifier.size(20.dp),
          )
          Text(
              text = "Tap to Scan QR",
              color = Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium,
          )
        }
      }
    }
  }
}

@Composable
fun PaymentFlowDialog(
    paymentState: PaymentState,
    liveTranscription: String = "",
    isDisplayCapable: Boolean = true,
    keypadAmount: String = "",
    onSetAmount: (Double) -> Unit,
    onConfirmPayment: () -> Unit,
    onChangeAmount: () -> Unit = {},
    onSplitBill: (Double) -> Unit = {},
    onDismiss: () -> Unit,
    onDone: () -> Unit = onDismiss,
) {
  when (paymentState) {
    is PaymentState.EnterAmount -> {
      EnterAmountDialog(
          details = paymentState.details,
          liveTranscription = liveTranscription,
          isDisplayCapable = isDisplayCapable,
          keypadAmount = keypadAmount,
          onProceed = onSetAmount,
          onDismiss = onDismiss,
      )
    }

    is PaymentState.ConfirmPayment -> {
      ConfirmPaymentDialog(
          details = paymentState.details,
          liveTranscription = liveTranscription,
          isDisplayCapable = isDisplayCapable,
          onConfirm = onConfirmPayment,
          onChangeAmount = onChangeAmount,
          onSplitBill = onSplitBill,
          onDismiss = onDismiss,
      )
    }

    is PaymentState.Processing -> {
      ProcessingPaymentDialog(details = paymentState.details)
    }

    is PaymentState.Success -> {
      PaymentSuccessDialog(
          details = paymentState.details,
          transactionId = paymentState.transactionId,
          timestamp = paymentState.timestamp,
          fundingSource = paymentState.fundingSource,
          onSplitBill = onSplitBill,
          onDone = onDone,
          onRescan = onDismiss,
      )
    }

    else -> {}
  }
}

@Composable
fun EnterAmountDialog(
    details: PaymentDetails,
    liveTranscription: String = "",
    isDisplayCapable: Boolean = true,
    keypadAmount: String = "",
    onProceed: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
  var amountText by remember { mutableStateOf(keypadAmount) }
  androidx.compose.runtime.LaunchedEffect(keypadAmount) {
    if (keypadAmount.isNotEmpty()) {
      amountText = keypadAmount
    }
  }
  val focusManager = LocalFocusManager.current
  val isPayPal = details.paymentMethod == PaymentMethod.PAYPAL_VAULT
  val symbol = if (details.currency == "USD") "$" else "₹"
  val quickAmounts = if (details.currency == "USD") {
    listOf(5.0, 10.0, 20.0, 50.0)
  } else {
    listOf(50.0, 100.0, 200.0, 500.0)
  }

  Dialog(onDismissRequest = onDismiss) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF272727)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
      Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
                imageVector = if (isPayPal) Icons.Default.Payment else Icons.Default.Security,
                contentDescription = null,
                tint = if (isPayPal) Color(0xFF003087) else Color(0xFF6875F5),
                modifier = Modifier.size(24.dp),
            )
            Column {
              Text(
                  text = if (isPayPal) "AirWallet • PayPal Vault" else "Meta UPI Pay",
                  fontWeight = FontWeight.Bold,
                  fontSize = 17.sp,
                  color = Color(0xFFEAEAEA),
              )
              if (isPayPal) {
                Text(
                    text = "Vaulted Frictionless Checkout",
                    fontSize = 11.sp,
                    color = Color(0xFF85C8F2),
                    fontWeight = FontWeight.Medium,
                )
              }
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E8E93))
          }
        }

        if (!isDisplayCapable) {
          Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFF2E2218),
              modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                  imageVector = Icons.Default.Mic,
                  contentDescription = null,
                  tint = Color(0xFFF7A258),
                  modifier = Modifier.size(16.dp),
              )
              Text(
                  text = "Voice Guidance Active (Audio-only Glasses)",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFFF7A258),
              )
            }
          }
        }

        // Payee Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF383838).copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "PAYING TO",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8E8E93),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                  text = details.payeeName,
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp,
                  color = Color(0xFFEAEAEA),
              )
              Icon(
                  imageVector = Icons.Default.Verified,
                  contentDescription = "Verified",
                  tint = if (isPayPal) Color(0xFF85C8F2) else Color(0xFF63B97A),
                  modifier = Modifier.size(16.dp),
              )
            }
            Text(
                text = details.payeeVpa,
                fontSize = 13.sp,
                color = Color(0xFF8E8E93),
            )
          }
        }

        // Amount Input
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
              text = "Enter Amount ($symbol)",
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF8E8E93),
          )
          Spacer(modifier = Modifier.height(6.dp))
          OutlinedTextField(
              value = amountText,
              onValueChange = { input ->
                if (input.all { it.isDigit() || it == '.' }) {
                  amountText = input
                }
              },
              singleLine = true,
              prefix = { Text("$symbol ", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFEAEAEA)) },
              placeholder = { Text("0.00", fontSize = 20.sp, color = Color(0xFF525255)) },
              keyboardOptions = KeyboardOptions(
                  keyboardType = KeyboardType.Decimal,
                  imeAction = ImeAction.Done,
              ),
              keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFEAEAEA)),
              colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = if (isPayPal) Color(0xFF85C8F2) else Color(0xFF63B97A),
                  unfocusedBorderColor = Color(0xFF383838),
                  cursorColor = Color(0xFFEAEAEA),
              ),
          )
        }

        // Quick amount chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          quickAmounts.forEach { amt ->
            SuggestionChip(
                onClick = { amountText = amt.toInt().toString() },
                label = { Text("$symbol${amt.toInt()}", fontSize = 12.sp, color = Color(0xFFEAEAEA)) },
                shape = RoundedCornerShape(20.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF383838)),
                border = SuggestionChipDefaults.suggestionChipBorder(borderColor = Color(0xFF383838), enabled = true),
            )
          }
        }

        // Voice Input Indicator
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isPayPal) Color(0xFF003087).copy(alpha = 0.20f) else Color(0xFF63B97A).copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = if (isPayPal) Color(0xFF85C8F2) else Color(0xFF63B97A),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (liveTranscription.isNotBlank()) {
                  "Hearing: \"$liveTranscription\""
                } else {
                  if (isPayPal) "Say amount (e.g. \"15\" or \"Twenty dollars\") via glasses" else "Say amount (e.g. \"150\" or \"Fifty\") via glasses"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isPayPal) Color(0xFF85C8F2) else Color(0xFF63B97A),
            )
          }
        }

        // Action button — Pay / Enter Amount button
        Button(
            onClick = {
              val parsed = amountText.toDoubleOrNull() ?: (if (details.currency == "USD") 15.0 else 100.0)
              onProceed(parsed)
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFC5F467),
            ),
        ) {
          val currentAmt = amountText.toDoubleOrNull()
          val btnText = if (currentAmt != null && currentAmt > 0) "Pay $symbol${"%.2f".format(currentAmt)}" else "Enter Amount & Pay"
          Text(btnText, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E1E1E))
        }
      }
    }
  }
}

@Composable
fun ConfirmPaymentDialog(
    details: PaymentDetails,
    liveTranscription: String = "",
    isDisplayCapable: Boolean = true,
    onConfirm: () -> Unit,
    onChangeAmount: () -> Unit = {},
    onSplitBill: ((Double) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
  val isPayPal = details.paymentMethod == PaymentMethod.PAYPAL_VAULT
  val symbol = if (details.currency == "USD") "$" else "₹"
  val amountFormatted = details.amount?.let { "$symbol %.2f".format(it) } ?: "$symbol 0.00"

  Dialog(onDismissRequest = onDismiss) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF272727)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
      Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
                imageVector = if (isPayPal) Icons.Default.Payment else Icons.Default.Security,
                contentDescription = null,
                tint = if (isPayPal) Color(0xFF003087) else Color(0xFF6875F5),
                modifier = Modifier.size(24.dp),
            )
            Column {
              Text(
                  text = if (isPayPal) "PayPal Vault Checkout" else "Confirm Payment",
                  fontWeight = FontWeight.Bold,
                  fontSize = 17.sp,
                  color = Color(0xFFEAEAEA),
              )
              if (isPayPal) {
                Text(
                    text = "Pre-authorized 1-Tap Payment",
                    fontSize = 11.sp,
                    color = Color(0xFF85C8F2),
                    fontWeight = FontWeight.Medium,
                )
              }
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFF8E8E93))
          }
        }

        if (!isDisplayCapable) {
          Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFF2E2218),
              modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                  imageVector = Icons.Default.Mic,
                  contentDescription = null,
                  tint = Color(0xFFF7A258),
                  modifier = Modifier.size(16.dp),
              )
              Text(
                  text = "Voice Guidance Active (Audio-only Glasses)",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFFF7A258),
              )
            }
          }
        }

        // Amount Hero
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
          Text(
              text = amountFormatted,
              fontSize = 34.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color(0xFFEAEAEA),
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
              text = if (isPayPal) "Total in USD (PayPal Vault)" else "Total Payable Amount",
              fontSize = 12.sp,
              color = Color(0xFF8E8E93),
          )
        }

        // Payee & Source Information Box
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF383838).copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("Payee:", fontSize = 13.sp, color = Color(0xFF8E8E93))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(details.payeeName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEAEAEA))
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.Verified,
                    contentDescription = null,
                    tint = if (isPayPal) Color(0xFF85C8F2) else Color(0xFF63B97A),
                    modifier = Modifier.size(14.dp)
                )
              }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                  text = if (isPayPal) "PayPal / Payee ID:" else "UPI ID:",
                  fontSize = 13.sp,
                  color = Color(0xFF8E8E93)
              )
              Text(details.payeeVpa, fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Color(0xFFEAEAEA))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("Payment Source:", fontSize = 13.sp, color = Color(0xFF8E8E93))
              Text(
                  text = if (isPayPal) "PayPal Vault (Linked)" else "Bank via UPI",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = if (isPayPal) Color(0xFF85C8F2) else Color(0xFF63B97A),
              )
            }
            if (details.note.isNotBlank()) {
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Note:", fontSize = 13.sp, color = Color(0xFF8E8E93))
                Text(details.note, fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Color(0xFFEAEAEA))
              }
            }
          }
        }

        // Voice Command Indicator
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isPayPal) Color(0xFF003087).copy(alpha = 0.20f) else Color(0xFF63B97A).copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = if (isPayPal) Color(0xFF85C8F2) else Color(0xFF63B97A),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (liveTranscription.isNotBlank()) {
                  "Hearing: \"$liveTranscription\""
                } else {
                  "Say \"Yes\" or \"Pay\" to confirm via voice"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isPayPal) Color(0xFF85C8F2) else Color(0xFF63B97A),
            )
          }
        }

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          // Lime confirm button per TSX
          Button(
              onClick = onConfirm,
              modifier = Modifier.fillMaxWidth().height(52.dp),
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5F467)),
          ) {
            Text("Pay ${amountFormatted}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E1E1E))
          }

          // Enter / Change Amount button
          Button(
              onClick = onChangeAmount,
              modifier = Modifier.fillMaxWidth().height(48.dp),
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF383838)),
          ) {
            Text("Enter / Change Amount", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEAEAEA))
          }

          OutlinedButton(
              onClick = onDismiss,
              modifier = Modifier.fillMaxWidth().height(48.dp),
              shape = RoundedCornerShape(50),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383838)),
          ) {
            Text("Cancel", fontSize = 15.sp, color = Color(0xFF8E8E93))
          }
        }
      }
    }
  }
}

@Composable
fun ProcessingPaymentDialog(details: PaymentDetails) {
  val isPayPal = details.paymentMethod == PaymentMethod.PAYPAL_VAULT
  Dialog(onDismissRequest = {}) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
      Column(
          modifier = Modifier.padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        CircularProgressIndicator(
            color = if (isPayPal) Color(0xFF0079C1) else Color(0xFF00C853),
            strokeWidth = 4.dp,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = if (isPayPal) "Authorizing PayPal Vault..." else "Processing Payment...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (isPayPal) "Capturing order via saved PayPal Vault token" else "Authorizing transaction with bank",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
fun PaymentSuccessDialog(
    details: PaymentDetails,
    transactionId: String,
    timestamp: Long,
    fundingSource: String = "PayPal Balance",
    onSplitBill: ((Double) -> Unit)? = null,
    onDone: () -> Unit,
    onRescan: () -> Unit,
) {
  val isPayPal = details.paymentMethod == PaymentMethod.PAYPAL_VAULT
  val symbol = if (details.currency == "USD") "$" else "₹"
  val dateFormatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))
  val amountFormatted = details.amount?.let { "$symbol %.2f".format(it) } ?: "$symbol 0.00"

  Dialog(onDismissRequest = onDone) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
      Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Success Icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(if (isPayPal) Color(0xFFE3F2FD) else Color(0xFFE8F5E9), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
          Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = "Success",
              tint = if (isPayPal) Color(0xFF0079C1) else Color(0xFF00C853),
              modifier = Modifier.size(52.dp),
          )
        }

        Text(
            text = if (isPayPal) "PayPal Payment Successful!" else "Payment Successful!",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPayPal) Color(0xFF003087) else Color(0xFF00C853),
        )

        Text(
            text = amountFormatted,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
        )

        // Receipt card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("Paid To:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(details.payeeName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(if (isPayPal) "PayPal Recipient:" else "UPI ID:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(details.payeeVpa, fontSize = 12.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("Payment Method:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(details.paymentMethod.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("Funding Source:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(fundingSource, fontSize = 12.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("Transaction ID:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(transactionId, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("Date & Time:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(dateFormatted, fontSize = 12.sp)
            }
          }
        }

        if (onSplitBill != null) {
          Button(
              onClick = {
                val amt = details.amount ?: (if (details.currency == "USD") 15.0 else 100.0)
                onSplitBill(amt)
              },
              modifier = Modifier.fillMaxWidth().height(50.dp),
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003087)),
          ) {
            Icon(
                imageVector = Icons.Default.Payment,
                contentDescription = null,
                tint = Color(0xFF85C8F2),
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Split ${amountFormatted} with Friends", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
          }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
              onClick = onDone,
              modifier = Modifier.weight(1f).height(50.dp),
              shape = RoundedCornerShape(14.dp),
          ) {
            Text("Done", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
          }
          Button(
              onClick = onRescan,
              modifier = Modifier.weight(1f).height(50.dp),
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                  containerColor = if (isPayPal) Color(0xFF0079C1) else Color(0xFF00C853)
              ),
          ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Rescan QR", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
          }
        }
      }
    }
  }
}

@Composable
fun PayPalVaultBottomSheet(
    vaultStatus: PayPalVaultStatus,
    selectedMethod: PaymentMethod,
    onSelectMethod: (PaymentMethod) -> Unit,
    onLinkDefaultSandbox: () -> Unit,
    onUnlink: () -> Unit,
    onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
      Column(
          modifier = Modifier.padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
                imageVector = Icons.Default.Payment,
                contentDescription = null,
                tint = Color(0xFF003087),
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = "PayPal Vault Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }

        Text(
            text = "PayPal Vaulted Payments enable seamless 1-tap and voice checkouts on Meta Smart Glasses without webview redirects.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Preferred Payment Rail Selector
        Text(
            text = "Active Payment Rail",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          PaymentMethod.values().forEach { method ->
            val isSelected = selectedMethod == method
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) Color(0xFF003087) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectMethod(method) },
            ) {
              Column(
                  modifier = Modifier.padding(12.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text(
                    text = method.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${method.defaultCurrency} (${method.symbol})",
                    fontSize = 10.sp,
                    color = if (isSelected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
              }
            }
          }
        }

        // Vault Account Card
        when (vaultStatus) {
          is PayPalVaultStatus.Vaulted -> {
            val acc = vaultStatus.account
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF0F7FF),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0079C1).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color(0xFF003087), modifier = Modifier.size(16.dp))
                    Text("Vaulted Account", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF003087))
                  }
                  Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE8F5E9)) {
                    Text("Active", color = Color(0xFF00C853), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                  }
                }
                Text("User: ${acc.customerEmail}", fontSize = 12.sp, color = Color.Black)
                Text("Funding: ${acc.fundingSource}", fontSize = 12.sp, color = Color.DarkGray)
                Text("Vault ID: ${acc.vaultId}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
              }
            }

            OutlinedButton(
                onClick = onUnlink,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
              Text("Unlink PayPal Account", color = MaterialTheme.colorScheme.error)
            }
          }
          else -> {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No PayPal Vault account connected", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onLinkDefaultSandbox,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0079C1)),
                ) {
                  Text("Connect Sandbox Vault Buyer", color = Color.White, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
          Text("Done")
        }
      }
    }
  }
}
