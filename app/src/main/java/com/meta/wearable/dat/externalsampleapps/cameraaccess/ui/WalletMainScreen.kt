/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.camera.view.PreviewView
import androidx.compose.runtime.DisposableEffect
import com.meta.wearable.dat.externalsampleapps.cameraaccess.camera.FastQrScanner
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.QrPayloadParser
import com.meta.wearable.dat.core.session.DeviceSessionState
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.meta.wearable.dat.externalsampleapps.cameraaccess.error.AppErrorHandler
import com.meta.wearable.dat.externalsampleapps.cameraaccess.error.ErrorCategory
import com.meta.wearable.dat.externalsampleapps.cameraaccess.error.ErrorSeverity
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.camera.CameraUiState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.camera.CameraViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.camera.GlassesDisplayScreen
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.PaymentDetails
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.PaymentState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.WalletTransaction
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.ai.CategorySpend
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.ai.DaySpend
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.BillSplitResult
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesUiState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class WalletScreen { HOME, STATS, SCAN, SETTINGS, CONTACTS }

data class WalletContact(
    val id: String,
    val name: String,
    val handle: String,
    val rail: String,
    val initials: String,
    val color: Color,
)

@Composable
fun WalletMainScreen(
    wearablesViewModel: WearablesViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    onRequestRecordAudioPermission: suspend () -> Boolean,
    modifier: Modifier = Modifier,
    cameraViewModel: CameraViewModel = viewModel(
        factory = CameraViewModel.Factory(
            application = (LocalActivity.current as ComponentActivity).application,
            wearablesViewModel = wearablesViewModel,
        ),
    ),
) {
  val ui by cameraViewModel.uiState.collectAsStateWithLifecycle()
  val wearablesUi by wearablesViewModel.uiState.collectAsStateWithLifecycle()
  val context = androidx.compose.ui.platform.LocalContext.current
  val activity = androidx.activity.compose.LocalActivity.current
      ?: (context as? android.app.Activity)
      ?: ((context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity)

  var currentScreen by remember { mutableStateOf(WalletScreen.HOME) }
  var scanMode by remember { mutableStateOf("glasses") } // "glasses" or "camera"
  var doublePressScan by remember { mutableStateOf(true) }
  var showBillSplitDialog by remember { mutableStateOf(false) }
  var billSplitInitialAmount by remember { mutableStateOf(0.0) }

  // Persistent On-Device Transaction History & Balance
  val transactions by cameraViewModel.transactionRepository.transactions.collectAsStateWithLifecycle()
  val balance by cameraViewModel.transactionRepository.balance.collectAsStateWithLifecycle()

  val activeRail = if (ui.selectedPaymentMethod == PaymentMethod.PAYPAL_VAULT) "PayPal" else "UPI"
  val isConnected = wearablesUi.hasActiveDevice && wearablesUi.registrationState == RegistrationState.REGISTERED
  val isPairing = wearablesUi.registrationState == RegistrationState.REGISTERING

  // Auto-connect and launch glasses HUD when connected
  LaunchedEffect(isConnected) {
    if (isConnected) {
      cameraViewModel.onAppResumed()
    }
  }

  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  var hasCameraPermission by remember {
    mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }

  // Refresh camera permission and ensure glasses display session on app resume
  DisposableEffect(lifecycleOwner) {
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
      if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        cameraViewModel.onAppResumed()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasCameraPermission = isGranted
    if (isGranted) {
      scanMode = "camera"
      currentScreen = WalletScreen.SCAN
    } else {
      AppErrorHandler.postError(
          rawError = "Camera access is needed to scan QR codes with your mobile device.",
          category = ErrorCategory.PERMISSION,
          severity = ErrorSeverity.WARNING,
      )
    }
  }

  val onLaunchGlassesScanner = {
    scanMode = "glasses"
    currentScreen = WalletScreen.SCAN
    cameraViewModel.startGlassesCamera()
  }

  val onLaunchMobileScanner = {
    val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    hasCameraPermission = isGranted
    if (isGranted) {
      scanMode = "camera"
      currentScreen = WalletScreen.SCAN
    } else {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  // Synchronize phone screen automatically with glasses HUD navigation & ensure camera stops when leaving SCAN
  LaunchedEffect(ui.glassesDisplayScreen) {
    if (ui.glassesDisplayScreen == GlassesDisplayScreen.SCAN_QR) {
      if (currentScreen != WalletScreen.SCAN) {
        scanMode = "glasses"
        currentScreen = WalletScreen.SCAN
      }
    } else if (ui.glassesDisplayScreen == GlassesDisplayScreen.SCAN_OBJECT || ui.glassesDisplayScreen == GlassesDisplayScreen.SCAN_BILL) {
      // Keep stream and session active for Visual AI scanning
    } else {
      if (ui.isStreaming || ui.streamState != StreamState.STOPPED) {
        cameraViewModel.stopStreaming()
      }
      if (currentScreen == WalletScreen.SCAN) {
        currentScreen = WalletScreen.HOME
      }
    }
  }

  // Ensure camera stream is stopped whenever phone navigates away from SCAN screen unless in Visual AI mode
  LaunchedEffect(currentScreen) {
    val isVisualAi = ui.glassesDisplayScreen == GlassesDisplayScreen.SCAN_OBJECT || ui.glassesDisplayScreen == GlassesDisplayScreen.SCAN_BILL
    if (currentScreen != WalletScreen.SCAN && !isVisualAi) {
      if (ui.isStreaming || ui.streamState != StreamState.STOPPED) {
        cameraViewModel.stopStreaming()
      }
      if (ui.glassesDisplayScreen == GlassesDisplayScreen.SCAN_QR) {
        cameraViewModel.returnToGlassesHome()
      }
    }
  }

  Box(
      modifier = modifier
          .fillMaxSize()
          .background(AppColor.BgCanvas)
          .statusBarsPadding()
          .navigationBarsPadding()
  ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // 1. Subtle Screen Switcher Pill Above Phone Frame (matches TSX line 883)
      Row(
          modifier = Modifier
              .align(Alignment.CenterHorizontally)
              .clip(RoundedCornerShape(50))
              .background(AppColor.BgSurface)
              .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(50))
              .padding(3.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        listOf(
            WalletScreen.HOME to "Home",
            WalletScreen.STATS to "Statistics",
            WalletScreen.SCAN to "Scan",
            WalletScreen.SETTINGS to "Settings",
        ).forEach { (screen, label) ->
          val active = currentScreen == screen || (currentScreen == WalletScreen.CONTACTS && screen == WalletScreen.HOME)
          Surface(
              shape = RoundedCornerShape(50),
              color = if (active) AppColor.PillActive else Color.Transparent,
              modifier = Modifier.clickable {
                currentScreen = screen
                if (screen == WalletScreen.SCAN) {
                  onLaunchGlassesScanner()
                }
              },
          ) {
            Text(
                text = label,
                color = if (active) AppColor.TextInverted else AppColor.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // 2. Active Screen Content
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when (currentScreen) {
          WalletScreen.HOME -> {
            WalletHomeScreen(
                activeRail = activeRail,
                isConnected = isConnected,
                isPairing = isPairing,
                transactions = transactions,
                ui = ui,
                cameraViewModel = cameraViewModel,
                onSelectRail = { rail ->
                  cameraViewModel.selectPaymentMethod(
                      if (rail == "PayPal") PaymentMethod.PAYPAL_VAULT else PaymentMethod.UPI
                  )
                },
                onOpenScanner = { mode ->
                  if (mode == "glasses") {
                    onLaunchGlassesScanner()
                  } else {
                    onLaunchMobileScanner()
                  }
                },
                onNavigate = { screen -> currentScreen = screen },
                onOpenBillSplit = { amt ->
                  billSplitInitialAmount = amt
                  showBillSplitDialog = true
                },
            )
          }
          WalletScreen.STATS -> {
            WalletStatisticsScreen(
                balance = balance,
                activeRail = activeRail,
                transactions = transactions,
                cameraViewModel = cameraViewModel,
                onSelectRail = { rail ->
                  cameraViewModel.selectPaymentMethod(
                      if (rail == "PayPal") PaymentMethod.PAYPAL_VAULT else PaymentMethod.UPI
                  )
                },
                onClearHistory = { cameraViewModel.transactionRepository.clearTransactions() },
                onBack = { currentScreen = WalletScreen.HOME },
            )
          }
          WalletScreen.SCAN -> {
            WalletScannerScreen(
                mode = scanMode,
                activeRail = activeRail,
                ui = ui,
                wearablesUi = wearablesUi,
                cameraViewModel = cameraViewModel,
                hasCameraPermission = hasCameraPermission,
                onRequestCameraPermission = {
                  val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                  hasCameraPermission = isGranted
                  if (!isGranted) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                  }
                },
                onSwitchMode = { newMode ->
                  if (newMode == "camera") {
                    val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    hasCameraPermission = isGranted
                    if (!isGranted) {
                      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                  }
                  scanMode = newMode
                },
                onNavigateSettings = { currentScreen = WalletScreen.SETTINGS },
                onBack = {
                  currentScreen = WalletScreen.HOME
                  cameraViewModel.returnToGlassesHome()
                },
                onSurfaceChanged = cameraViewModel::setSurface,
            )
          }
          WalletScreen.SETTINGS -> {
            WalletSettingsScreen(
                isConnected = isConnected,
                isPairing = isPairing,
                activeRail = activeRail,
                doublePressScan = doublePressScan,
                onToggleDoublePress = { doublePressScan = !doublePressScan },
                onTriggerConnectGlasses = {
                  activity?.let {
                    if (wearablesUi.registrationState == RegistrationState.REGISTERED) {
                      wearablesViewModel.startUnregistration(it)
                    } else {
                      wearablesViewModel.startRegistration(it)
                    }
                  }
                },
                onSelectRail = { rail ->
                  cameraViewModel.selectPaymentMethod(
                      if (rail == "PayPal") PaymentMethod.PAYPAL_VAULT else PaymentMethod.UPI
                  )
                },
                onOpenPayPalVaultSheet = {
                  cameraViewModel.togglePayPalVaultSheet(true)
                },
                onBack = { currentScreen = WalletScreen.HOME },
            )
          }
          WalletScreen.CONTACTS -> {
            WalletContactsScreen(
                cameraViewModel = cameraViewModel,
                onSend = { contact, amount ->
                  cameraViewModel.selectPaymentMethod(
                      if (contact.rail == "PayPal") PaymentMethod.PAYPAL_VAULT else PaymentMethod.UPI
                  )
                  cameraViewModel.recordManualTransaction(
                      merchant = contact.name,
                      rail = contact.rail,
                      amount = amount,
                      note = "Sent to ${contact.name} (${contact.handle})",
                  )
                  currentScreen = WalletScreen.HOME
                },
                onBack = { currentScreen = WalletScreen.HOME },
            )
          }
        }
      }

      // 3. Bottom Navigation Bar (Matches TSX line 979: Glasses, Settings Gear, Avatar 'A')
      Column(
          modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          // Glasses Icon -> Scan with Meta Glasses
          IconButton(onClick = onLaunchGlassesScanner) {
            GlassesVectorIcon(
                tint = if (currentScreen == WalletScreen.SCAN) AppColor.Lime else AppColor.TextPrimary,
                modifier = Modifier.size(26.dp),
            )
          }

          // Settings Gear
          IconButton(onClick = { currentScreen = WalletScreen.SETTINGS }) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = if (currentScreen == WalletScreen.SETTINGS) AppColor.Lime else AppColor.TextSecondary,
                modifier = Modifier.size(24.dp),
            )
          }

          // Avatar 'A' (Home / Profile A)
          Box(
              modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFDDD0F7))
                  .then(
                      if (currentScreen == WalletScreen.HOME)
                        Modifier.border(2.dp, AppColor.Lavender, CircleShape)
                      else Modifier
                  )
                  .clickable { currentScreen = WalletScreen.HOME },
              contentAlignment = Alignment.Center,
          ) {
            Text(
                text = "A",
                color = Color(0xFF1E1E1E),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
          }
        }

        // Home indicator bar
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .width(110.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF323232))
        )
      }
    }

    // Modal Payment Overlays (Dialogs, PayPal Vault Sheet)
    PaymentFlowDialog(
        paymentState = ui.paymentState,
        liveTranscription = ui.liveSpeechTranscription,
        isDisplayCapable = ui.isDisplayCapable,
        keypadAmount = ui.keypadAmount,
        onSetAmount = cameraViewModel::setPaymentAmount,
        onConfirmPayment = cameraViewModel::confirmPayment,
        onChangeAmount = {
          val current = ui.paymentState
          if (current is PaymentState.ConfirmPayment) {
            cameraViewModel.editPaymentAmount(current.details)
          }
        },
        onSplitBill = { amt ->
          cameraViewModel.dismissPayment()
          billSplitInitialAmount = amt
          showBillSplitDialog = true
        },
        onDismiss = cameraViewModel::dismissPayment,
        onDone = cameraViewModel::returnToGlassesHome,
    )

    if (showBillSplitDialog) {
      PayPalBillSplitDialog(
          cameraViewModel = cameraViewModel,
          initialAmount = billSplitInitialAmount,
          onDismiss = {
            showBillSplitDialog = false
            billSplitInitialAmount = 0.0
          },
      )
    }

    if (ui.showPayPalVaultSheet) {
      PayPalVaultBottomSheet(
          vaultStatus = ui.paypalVaultStatus,
          selectedMethod = ui.selectedPaymentMethod,
          onSelectMethod = cameraViewModel::selectPaymentMethod,
          onLinkDefaultSandbox = cameraViewModel::linkDefaultSandboxVault,
          onUnlink = cameraViewModel::unlinkPayPalAccount,
          onDismiss = { cameraViewModel.togglePayPalVaultSheet(false) },
      )
    }

    if (ui.isVoiceQaListening) {
      VoiceQaListeningModal(
          liveTranscript = ui.liveSpeechTranscription,
          onQuestionSelected = { q ->
            cameraViewModel.askSpendingAiQuestion(q, speakOutput = true)
          },
          onDismiss = cameraViewModel::dismissVoiceQaListening,
      )
    }

    // Meta Wearables Permission Prompts (Redirect to Meta AI app when required)
    if (ui.showCameraPermissionRedirectConfirm) {
      AlertDialog(
          onDismissRequest = { cameraViewModel.cancelCameraPermissionRedirect() },
          title = { Text("Camera Permission Required") },
          text = { Text("Allow camera access for smart glasses in the Meta AI app.") },
          confirmButton = {
            TextButton(
                onClick = {
                  cameraViewModel.confirmCameraPermissionRedirect(onRequestWearablesPermission)
                }
            ) {
              Text("Continue")
            }
          },
          dismissButton = {
            TextButton(onClick = { cameraViewModel.cancelCameraPermissionRedirect() }) {
              Text("Cancel")
            }
          },
      )
    }

    if (ui.showMicPermissionRedirectConfirm) {
      AlertDialog(
          onDismissRequest = { cameraViewModel.cancelMicPermissionRedirect() },
          title = { Text("Microphone Permission Required") },
          text = { Text("To use voice commands on your smart glasses, grant microphone access in the Meta AI app.") },
          confirmButton = {
            TextButton(
                onClick = {
                  cameraViewModel.confirmMicPermissionRedirect(onRequestWearablesPermission)
                }
            ) {
              Text("Continue")
            }
          },
          dismissButton = {
            TextButton(onClick = { cameraViewModel.cancelMicPermissionRedirect() }) {
              Text("Cancel")
            }
          },
      )
    }
  }
}

// ==========================================
// SCREEN 1: HOME (Strict Minimalist Screenshot 3)
// ==========================================
@Composable
private fun WalletHomeScreen(
    activeRail: String,
    isConnected: Boolean,
    isPairing: Boolean,
    transactions: List<WalletTransaction>,
    ui: CameraUiState,
    cameraViewModel: CameraViewModel,
    onSelectRail: (String) -> Unit,
    onOpenScanner: (String) -> Unit,
    onNavigate: (WalletScreen) -> Unit,
    onOpenBillSplit: (Double) -> Unit = {},
) {
  var selectedTxForReceipt by remember { mutableStateOf<WalletTransaction?>(null) }
  var showApiKeyDialog by remember { mutableStateOf(false) }
  val context = LocalContext.current

  val homeCameraPermissionLauncher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      Log.i("WalletHomeScreen", "Camera permission granted by user")
    }
  }

  // High-resolution real-world camera photo capture launchers for Visual AI
  val takeObjectPhotoLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.TakePicturePreview()
  ) { capturedBitmap ->
    if (capturedBitmap != null) {
      Log.i("WalletHomeScreen", "Object photo captured: ${capturedBitmap.getWidth()}x${capturedBitmap.getHeight()}")
      cameraViewModel.onScanObjectClicked(bitmap = capturedBitmap, fromGlasses = false)
    }
  }

  val takeBillPhotoLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.TakePicturePreview()
  ) { capturedBitmap ->
    if (capturedBitmap != null) {
      Log.i("WalletHomeScreen", "Bill photo captured: ${capturedBitmap.getWidth()}x${capturedBitmap.getHeight()}")
      cameraViewModel.onScanBillClicked(bitmap = capturedBitmap, fromGlasses = false)
    }
  }

  val safeLaunchObjectPhoto = {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      try {
        takeObjectPhotoLauncher.launch(null)
      } catch (e: Exception) {
        Log.e("WalletHomeScreen", "Cannot launch camera: ${e.message}")
      }
    } else {
      homeCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  val safeLaunchBillPhoto = {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      try {
        takeBillPhotoLauncher.launch(null)
      } catch (e: Exception) {
        Log.e("WalletHomeScreen", "Cannot launch camera: ${e.message}")
      }
    } else {
      homeCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  if (selectedTxForReceipt != null) {
    PayPalSmartReceiptDialog(
        tx = selectedTxForReceipt!!,
        cameraViewModel = cameraViewModel,
        onDismiss = { selectedTxForReceipt = null },
    )
  }

  if (ui.showGeminiDialog) {
    GeminiResultDialog(
        cameraViewModel = cameraViewModel,
        ui = ui,
        onDismiss = { cameraViewModel.dismissGeminiDialog() },
        onOpenApiKeyDialog = { showApiKeyDialog = true },
    )
  }

  if (showApiKeyDialog) {
    GeminiApiKeyDialog(
        cameraViewModel = cameraViewModel,
        onDismiss = { showApiKeyDialog = false },
    )
  }

  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
    // Top Bar: Active Rail Switcher + Meta Status Pill
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      // Rail Switcher (PayPal Vault / UPI)
      Surface(
          shape = RoundedCornerShape(50),
          color = AppColor.BgSurface,
      ) {
        Row(modifier = Modifier.padding(3.dp)) {
          listOf("PayPal" to "PayPal Vault", "UPI" to "UPI").forEach { (rail, label) ->
            val active = activeRail == rail
            Surface(
                shape = RoundedCornerShape(50),
                color = if (active) AppColor.PillActive else Color.Transparent,
                modifier = Modifier.clickable { onSelectRail(rail) },
            ) {
              Text(
                  text = label,
                  color = if (active) AppColor.TextInverted else AppColor.TextSecondary,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
              )
            }
          }
        }
      }

      Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        // Google Gemini Multimodal Visual AI Pill (App-side Preconfigured)
        Surface(
            shape = RoundedCornerShape(50),
            color = AppColor.BgSurface,
            border = BorderStroke(1.dp, AppColor.Lime.copy(alpha = 0.4f)),
            modifier = Modifier.clickable { showApiKeyDialog = true },
        ) {
          Row(
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Visual AI",
                tint = AppColor.Lime,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "Gemini Live",
                color = AppColor.Lime,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
          }
        }

        // Meta Glasses Status Dot
        Surface(
            shape = RoundedCornerShape(50),
            color = AppColor.BgSurface,
            modifier = Modifier.clickable { onNavigate(WalletScreen.SETTINGS) },
        ) {
          Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnected) AppColor.Lime
                        else if (isPairing) AppColor.Orange
                        else AppColor.TextMuted
                    )
            )
            Text(text = "Meta", color = AppColor.TextSecondary, fontSize = 11.sp)
          }
        }
      }
    }

    // Glasses Display Status & Control Card (Always available on Home page)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AppColor.BgSurface,
        border = BorderStroke(1.dp, if (ui.isDisplayActive) AppColor.Lime.copy(alpha = 0.35f) else Color(0xFF383838)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
      Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
          GlassesVectorIcon(
              tint = if (ui.isDisplayActive) AppColor.Lime else AppColor.TextSecondary,
              modifier = Modifier.size(20.dp)
          )
          Column {
            Text(
                text = if (ui.isDisplayActive) "Glasses Display: ${ui.glassesDisplayScreen.name}" else "Meta Glasses Display",
                color = AppColor.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (ui.isDisplayActive) "Display session active on glasses" else "Display session idle",
                color = if (ui.isDisplayActive) AppColor.Lime else AppColor.TextSecondary,
                fontSize = 10.sp,
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          if (!isConnected) {
            Button(
                onClick = { onNavigate(WalletScreen.SETTINGS) },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = AppColor.Lime),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp),
            ) {
              Text(
                  text = "Connect Glasses",
                  color = AppColor.TextInverted,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
              )
            }
          } else if (!ui.isDisplayActive) {
            Button(
                onClick = { cameraViewModel.startGlassesDisplayHome() },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = AppColor.Lime),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp),
            ) {
              Text(
                  text = "Start Display",
                  color = AppColor.TextInverted,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
              )
            }
          } else {
            if (ui.glassesDisplayScreen != GlassesDisplayScreen.HOME) {
              Button(
                  onClick = { cameraViewModel.returnToGlassesHome() },
                  shape = RoundedCornerShape(50),
                  colors = ButtonDefaults.buttonColors(containerColor = AppColor.PillActive),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                  modifier = Modifier.height(32.dp),
              ) {
                Text(
                    text = "HUD Home",
                    color = AppColor.TextInverted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
              }
            }
            Button(
                onClick = { cameraViewModel.exitWalletDisplaySession() },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = AppColor.Coral),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                modifier = Modifier.height(32.dp),
            ) {
              Text(
                  text = "Exit Wallet",
                  color = Color.White,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }
    }

    // 2x2 Minimal Action Grid (Matches Screenshot 3)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Scan from mobile
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppColor.BgSurface,
            modifier = Modifier
                .weight(1f)
                .clickable { onOpenScanner("camera") },
        ) {
          Column(
              modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            CameraQrVectorIcon(accent = AppColor.Indigo, modifier = Modifier.size(32.dp))
            Text("Scan from mobile", color = AppColor.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
          }
        }

        // 2. Scan with glasses
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppColor.BgSurface,
            border = BorderStroke(1.dp, AppColor.Lime.copy(alpha = 0.35f)),
            modifier = Modifier
                .weight(1f)
                .clickable { onOpenScanner("glasses") },
        ) {
          Column(
              modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            GlassesQrVectorIcon(accent = AppColor.Lime, modifier = Modifier.size(32.dp))
            Text("Scan with glasses", color = AppColor.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // 3. Send to contacts
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppColor.BgSurface,
            modifier = Modifier
                .weight(1f)
                .clickable { onNavigate(WalletScreen.CONTACTS) },
        ) {
          Column(
              modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ContactCardVectorIcon(accent = AppColor.Lavender, modifier = Modifier.size(32.dp))
            Text("Send to contacts", color = AppColor.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
          }
        }

        // 4. History
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppColor.BgSurface,
            modifier = Modifier
                .weight(1f)
                .clickable { onNavigate(WalletScreen.STATS) },
        ) {
          Column(
              modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ReceiptHistoryVectorIcon(accent = AppColor.Orange, modifier = Modifier.size(32.dp))
            Text("History", color = AppColor.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
          }
        }
      }

      // Row 3: PayPal Invoicing v2 (Bill Split)
      Row(modifier = Modifier.fillMaxWidth()) {
        // 5. Split Bill & Invoice (PayPal Invoicing v2)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppColor.BgSurface,
            border = BorderStroke(1.dp, AppColor.Sky.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenBillSplit(0.0) },
        ) {
          Row(
              modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            BillSplitVectorIcon(accent = AppColor.Sky, modifier = Modifier.size(26.dp))
            Column {
              Text("Split Bill & Invoice", color = AppColor.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
              Text("PayPal Invoices v2 • Split with friends", color = AppColor.Sky, fontSize = 10.sp)
            }
          }
        }
      }

      // Row 4: Google Gemini 2.0 Flash Multimodal Visual AI (Scan Object & Scan Bill)
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Scan Object (Visual Shop)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppColor.BgSurface,
            border = BorderStroke(1.dp, AppColor.Coral.copy(alpha = 0.35f)),
            modifier = Modifier
                .weight(1f)
                .clickable {
                  if (ui.hasSession || isConnected || ui.isSessionActive) {
                    cameraViewModel.onScanObjectClicked(fromGlasses = true)
                  } else {
                    safeLaunchObjectPhoto()
                  }
                },
        ) {
          Row(
              modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Scan Object",
                tint = AppColor.Coral,
                modifier = Modifier.size(24.dp),
            )
            Column {
              Text("Scan Object", color = AppColor.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              Text("Gemini Flash • Visual Shop", color = AppColor.Coral, fontSize = 9.sp)
            }
          }
        }

        // Scan Paper Bill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppColor.BgSurface,
            border = BorderStroke(1.dp, AppColor.Lime.copy(alpha = 0.35f)),
            modifier = Modifier
                .weight(1f)
                .clickable {
                  if (ui.hasSession || isConnected || ui.isSessionActive) {
                    cameraViewModel.onScanBillClicked(fromGlasses = true)
                  } else {
                    safeLaunchBillPhoto()
                  }
                },
        ) {
          Row(
              modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
                imageVector = Icons.Default.Receipt,
                contentDescription = "Scan Bill",
                tint = AppColor.Lime,
                modifier = Modifier.size(24.dp),
            )
            Column {
              Text("Scan Bill", color = AppColor.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              Text("Gemini Flash • OCR Split", color = AppColor.Lime, fontSize = 9.sp)
            }
          }
        }
      }
    }

    // Recent Transactions Card (Matches Screenshot 3)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppColor.BgSurface,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(top = 8.dp),
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Text(
            text = "Recent\ntransactions",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColor.TextPrimary,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (transactions.isEmpty()) {
            item {
              Box(
                  modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 32.dp),
                  contentAlignment = Alignment.Center,
              ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  GlassesVectorIcon(tint = AppColor.TextMuted, modifier = Modifier.size(32.dp))
                  Text(
                      text = "No transactions yet",
                      color = AppColor.TextSecondary,
                      fontSize = 13.sp,
                      fontWeight = FontWeight.Medium,
                  )
                  Text(
                      text = "Scan a QR code with smart glasses to pay",
                      color = AppColor.TextMuted,
                      fontSize = 11.sp,
                  )
                }
              }
            }
          } else {
            items(transactions.take(10)) { tx ->
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(10.dp))
                      .clickable { selectedTxForReceipt = tx }
                      .padding(vertical = 4.dp, horizontal = 4.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                      text = tx.merchant,
                      color = AppColor.TextPrimary,
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Medium,
                  )
                  Text(
                      text = if (tx.rail == "PayPal") "PayPal Orders v2" else "UPI",
                      color = if (tx.rail == "PayPal") AppColor.Sky else AppColor.TextSecondary,
                      fontSize = 10.sp,
                  )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                      text = "$ " + "%.2f".format(tx.amount),
                      color = AppColor.TextPrimary,
                      fontSize = 14.sp,
                      fontWeight = FontWeight.SemiBold,
                  )
                  Text("›", color = AppColor.TextSecondary, fontSize = 14.sp)
                }
              }
            }
          }
        }
      }
    }
  }
}

// SCREEN 2: STATISTICS (LiteRT On-Device AI Financial Intelligence)
// ==========================================
@Composable
private fun WalletStatisticsScreen(
    balance: Double,
    activeRail: String,
    transactions: List<WalletTransaction>,
    cameraViewModel: CameraViewModel,
    onSelectRail: (String) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
) {
  var period by remember { mutableStateOf("All Time") }
  val uiState by cameraViewModel.uiState.collectAsStateWithLifecycle()
  val activeQaAnswer = uiState.aiQaAnswer ?: remember(transactions, balance) {
    cameraViewModel.financialAiEngine.answerSpendingQuestion("Highest expense", transactions, balance)
  }

  val speechLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
      if (!spoken.isNullOrBlank()) {
        cameraViewModel.askSpendingAiQuestion(spoken, speakOutput = true)
      }
    }
  }

  val totalSpent = transactions.sumOf { it.amount }
  val txCount = transactions.size
  val paypalSpent = transactions.filter { it.rail == "PayPal" }.sumOf { it.amount }
  val upiSpent = transactions.filter { it.rail == "UPI" }.sumOf { it.amount }
  val paypalPct = if (totalSpent > 0) ((paypalSpent / totalSpent) * 100).toInt() else 0
  val upiPct = if (totalSpent > 0) ((upiSpent / totalSpent) * 100).toInt() else 0

  // Execute on-device LiteRT financial intelligence inference
  val aiAnalysis = remember(transactions, balance) {
    cameraViewModel.financialAiEngine.analyzeFinances(transactions, balance)
  }

  Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Header
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColor.TextSecondary)
      }
      Text("Statistics", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColor.TextSecondary)
      if (transactions.isNotEmpty()) {
        Text(
            text = "Clear",
            color = AppColor.Coral,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onClearHistory() },
        )
      } else {
        Box(modifier = Modifier.size(24.dp))
      }
    }

    // Summary Card
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppColor.BgSurface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
          Column {
            Text(
                text = if (activeRail == "PayPal") "PayPal Vault" else "UPI Linked",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = AppColor.TextPrimary,
            )
            Text(
                text = if (activeRail == "PayPal") "Vaulted 1-Click Pay" else "atharv@okaxis",
                fontSize = 11.sp,
                color = AppColor.TextSecondary,
            )
          }
          Row(verticalAlignment = Alignment.Bottom) {
            Text("$ ", fontSize = 14.sp, color = AppColor.TextSecondary, fontWeight = FontWeight.Bold)
            Text(
                text = "%,.2f".format(balance),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AppColor.TextPrimary,
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              text = "$txCount Transactions",
              color = AppColor.TextPrimary,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
          )
          Text(
              text = "Switch Rail",
              color = AppColor.Sky,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.clickable {
                onSelectRail(if (activeRail == "PayPal") "UPI" else "PayPal")
              },
          )
          Text(
              text = if (totalSpent > 0) "Spent: $%,.2f".format(totalSpent) else "No expenses",
              color = AppColor.TextSecondary,
              fontSize = 12.sp,
          )
        }
      }
    }

    // 1. Weekly Spend Line Graph Card
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppColor.BgSurface,
        border = BorderStroke(1.dp, AppColor.Lime.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AppColor.Lime))
            Text(
                text = "Weekly Spend",
                color = AppColor.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
          }
          Surface(
              shape = RoundedCornerShape(50),
              color = AppColor.Lime.copy(alpha = 0.15f),
          ) {
            Text(
                text = "Past 7 Days",
                color = AppColor.Lime,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("$", fontSize = 14.sp, color = AppColor.TextSecondary, fontWeight = FontWeight.Bold)
          Text(
              text = "%,.2f".format(aiAnalysis.weeklySpent),
              fontSize = 24.sp,
              fontWeight = FontWeight.Bold,
              color = AppColor.TextPrimary,
          )
          Text(
              text = "total this week",
              fontSize = 11.sp,
              color = AppColor.TextSecondary,
              modifier = Modifier.padding(bottom = 3.dp, start = 4.dp),
          )
        }

        WeeklySpendLineCanvas(
            weeklyTrend = aiAnalysis.weeklyTrend,
            lineColor = AppColor.Lime,
            modifier = Modifier.fillMaxWidth().height(100.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          aiAnalysis.weeklyTrend.forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                  text = day.dayName,
                  color = if (day.isToday) AppColor.Sky else if (day.amount > 0) AppColor.TextPrimary else AppColor.TextMuted,
                  fontSize = 10.sp,
                  fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
              )
              Text(
                  text = if (day.amount > 0) "$${day.amount.toInt()}" else "–",
                  color = if (day.isToday) AppColor.Sky else if (day.amount > 0) AppColor.TextSecondary else AppColor.TextMuted,
                  fontSize = 9.sp,
                  fontWeight = if (day.amount > 0) FontWeight.SemiBold else FontWeight.Normal,
              )
            }
          }
        }
      }
    }

    // 2. Categorized Payment Types Pie Chart Card
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppColor.BgSurface,
        border = BorderStroke(1.dp, AppColor.Coral.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AppColor.Coral))
            Text(
                text = "Spending by Category",
                color = AppColor.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
          }
          Surface(
              shape = RoundedCornerShape(50),
              color = AppColor.Coral.copy(alpha = 0.15f),
          ) {
            Text(
                text = "${aiAnalysis.categoryBreakdown.size} Categories",
                color = AppColor.Coral,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            contentAlignment = Alignment.Center,
        ) {
          CategorizedPieCanvas(
              categories = aiAnalysis.categoryBreakdown,
              modifier = Modifier.size(130.dp),
          )
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$${"%.0f".format(aiAnalysis.totalSpent)}",
                color = AppColor.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (aiAnalysis.topCategory != "None") aiAnalysis.topCategory else "Total Outflow",
                color = AppColor.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          aiAnalysis.categoryBreakdown.chunked(2).forEach { rowCats ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              rowCats.forEach { cat ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF191919),
                    modifier = Modifier.weight(1f),
                ) {
                  Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                      Box(
                          modifier = Modifier
                              .size(8.dp)
                              .clip(CircleShape)
                              .background(Color(cat.colorHex))
                      )
                      Column {
                        Text(
                            text = cat.category,
                            color = AppColor.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = "${cat.count} ${if (cat.count == 1) "payment" else "payments"}",
                            color = AppColor.TextMuted,
                            fontSize = 9.sp,
                        )
                      }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                      Text(
                          text = "$${"%.2f".format(cat.amount)}",
                          color = AppColor.TextPrimary,
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Bold,
                      )
                      Text(
                          text = "${cat.percentage}%",
                          color = Color(cat.colorHex),
                          fontSize = 10.sp,
                          fontWeight = FontWeight.SemiBold,
                      )
                    }
                  }
                }
              }
              if (rowCats.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
              }
            }
          }
        }
      }
    }

    // 3. AI Spending Q&A Intelligence Card
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1E1E1E),
        border = BorderStroke(1.dp, AppColor.Sky.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(AppColor.Sky)
            )
            Text(
                text = "AI Spending Assistant",
                color = AppColor.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
          }
          Surface(
              shape = RoundedCornerShape(50),
              color = AppColor.Sky.copy(alpha = 0.2f),
              border = BorderStroke(1.dp, AppColor.Sky.copy(alpha = 0.5f)),
              modifier = Modifier.clickable {
                cameraViewModel.onAskVoiceQuestionClicked()
              }
          ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text("🎤 Speak Question", color = AppColor.Sky, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }

        // Quick Preset Inquiry Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf(
              "Highest expense",
              "Spend on food",
              "Sent to friends",
              "Utilities bills",
              "Shopping spend",
              "Weekly outflow",
              "Total spent",
              "Average spend",
              "Last purchase",
              "Spent today",
          ).forEach { preset ->
            val isSelected = activeQaAnswer.query.equals(preset, ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) AppColor.Sky.copy(alpha = 0.25f) else AppColor.BgSurface,
                border = BorderStroke(1.dp, if (isSelected) AppColor.Sky else Color.Transparent),
                modifier = Modifier.clickable {
                  cameraViewModel.askSpendingAiQuestion(preset, speakOutput = true)
                }
            ) {
              Text(
                  text = preset,
                  color = if (isSelected) AppColor.Sky else AppColor.TextSecondary,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
              )
            }
          }
        }

        // Answer Presentation Box
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF141414),
            modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                  text = activeQaAnswer.headline,
                  color = AppColor.Lime,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
              )
              activeQaAnswer.metricValue?.let { metric ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = AppColor.Lime.copy(alpha = 0.15f)
                ) {
                  Text(
                      text = metric,
                      color = AppColor.Lime,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
            }
            Text(
                text = activeQaAnswer.detailedAnswer,
                color = AppColor.TextPrimary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                  text = "Tip: Ask \"spend on food\" on glasses",
                  color = AppColor.TextMuted,
                  fontSize = 9.sp
              )
              Text(
                  text = "🔊 Read Aloud",
                  color = AppColor.Sky,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.clickable {
                    cameraViewModel.voicePrompter.speak(activeQaAnswer.shortAnswer)
                  }
              )
            }
          }
        }
      }
    }
  }
}

// ==========================================
// SCREEN 3: SCANNER (Meta Glasses / Camera)
// ==========================================
@Composable
private fun WalletScannerScreen(
    mode: String,
    activeRail: String,
    ui: CameraUiState,
    wearablesUi: com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesUiState,
    cameraViewModel: CameraViewModel,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onSwitchMode: (String) -> Unit,
    onNavigateSettings: () -> Unit,
    onBack: () -> Unit,
    onSurfaceChanged: (android.view.Surface?) -> Unit,
) {
  val isGlasses = mode == "glasses"
  val accent = if (isGlasses) AppColor.Lime else AppColor.Indigo

  var livePreviewView by remember { mutableStateOf<PreviewView?>(null) }

  val scannerObjectPhotoLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.TakePicturePreview()
  ) { capturedBitmap ->
    if (capturedBitmap != null) {
      Log.i("WalletScannerScreen", "Scanner object photo captured: ${capturedBitmap.getWidth()}x${capturedBitmap.getHeight()}")
      cameraViewModel.onScanObjectClicked(bitmap = capturedBitmap, fromGlasses = false)
    }
  }

  val scannerBillPhotoLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.TakePicturePreview()
  ) { capturedBitmap ->
    if (capturedBitmap != null) {
      Log.i("WalletScannerScreen", "Scanner bill photo captured: ${capturedBitmap.getWidth()}x${capturedBitmap.getHeight()}")
      cameraViewModel.onScanBillClicked(bitmap = capturedBitmap, fromGlasses = false)
    }
  }

  Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.SpaceBetween,
  ) {
    // Top Bar
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColor.TextSecondary)
      }

      Surface(
          shape = RoundedCornerShape(50),
          color = AppColor.BgSurface,
      ) {
        Row(modifier = Modifier.padding(3.dp)) {
          listOf("glasses" to "Meta Glasses", "camera" to "Mobile Camera").forEach { (m, label) ->
            val active = mode == m
            Surface(
                shape = RoundedCornerShape(50),
                color = if (active) AppColor.PillActive else Color.Transparent,
                modifier = Modifier.clickable {
                  onSwitchMode(m)
                  if (m == "glasses") {
                    cameraViewModel.startGlassesCamera()
                  } else {
                    cameraViewModel.stopStreaming()
                  }
                },
            ) {
              Text(
                  text = label,
                  color = if (active) AppColor.TextInverted else AppColor.TextSecondary,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
              )
            }
          }
        }
      }

      Box(modifier = Modifier.size(24.dp))
    }

    // QR Reticle with Viewfinder Container
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
      // Live Video Sink (if glasses streaming) with ultra-fast frame sampling
      if (isGlasses && ui.hasStream) {
        AndroidView(
            factory = { context ->
              TextureView(context).apply {
                var lastScanMs = 0L
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                  override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                    onSurfaceChanged(Surface(st))
                  }

                  override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

                  override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    onSurfaceChanged(null)
                    return true
                  }

                  override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                    val now = System.currentTimeMillis()
                    // Sample every 90ms (11 times a second) directly from the live 24fps stream
                    if (now - lastScanMs >= 90L && ui.paymentState is PaymentState.Idle) {
                      lastScanMs = now
                      val tv = this@apply
                      val w = tv.width
                      val h = tv.height
                      if (w > 0 && h > 0) {
                        try {
                          // Higher resolution sampling (720-960px) provides 2x-4x more pixels for small QR codes
                          val sampleW = minOf(w, 960).coerceAtLeast(720)
                          val sampleH = (h * (sampleW.toFloat() / w)).toInt().coerceAtLeast(1)
                          val bmp = tv.getBitmap(sampleW, sampleH)
                          if (bmp != null) {
                            cameraViewModel.analyzeFrameForQr(bmp)
                          }
                        } catch (_: Throwable) {}
                      }
                    }
                  }
                }
              }
            },
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
        )

        if (!ui.hasReceivedFirstFrame) {
          Box(
              modifier = Modifier
                  .fillMaxSize()
                  .clip(RoundedCornerShape(24.dp))
                  .background(Color.Black.copy(alpha = 0.5f)),
              contentAlignment = Alignment.Center,
          ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              CircularProgressIndicator(color = AppColor.Lime, modifier = Modifier.size(32.dp))
              Text("Starting camera stream...", color = AppColor.TextPrimary, fontSize = 12.sp)
            }
          }
        }
      } else if (isGlasses) {
        // Glasses camera is not currently streaming: show interactive status card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(AppColor.BgSurface)
                .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
          val isRegistered = wearablesUi.registrationState == RegistrationState.REGISTERED
          val hasActiveDevice = wearablesUi.hasActiveDevice
          if (!isRegistered || !hasActiveDevice) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(20.dp),
            ) {
              GlassesVectorIcon(tint = AppColor.TextSecondary, modifier = Modifier.size(44.dp))
              Text(
                  text = "Ray-Ban Meta Not Connected",
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp,
                  color = AppColor.TextPrimary,
              )
              Text(
                  text = "Please turn on your glasses and connect in Settings",
                  fontSize = 11.sp,
                  color = AppColor.TextSecondary,
                  textAlign = TextAlign.Center,
              )
              Button(
                  onClick = onNavigateSettings,
                  shape = RoundedCornerShape(50),
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF383838)),
              ) {
                Text("Open Glasses Settings", color = AppColor.TextPrimary, fontSize = 12.sp)
              }
            }
          } else if (ui.isSessionStarting || ui.isStartingStream) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              CircularProgressIndicator(color = AppColor.Lime, modifier = Modifier.size(36.dp))
              Text(
                  text = "Connecting to Meta Glasses...",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium,
                  color = AppColor.TextPrimary,
              )
            }
          } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(20.dp),
            ) {
              GlassesVectorIcon(tint = AppColor.Lime, modifier = Modifier.size(44.dp))
              Text(
                  text = "Glasses Camera Ready",
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp,
                  color = AppColor.TextPrimary,
              )
              Text(
                  text = "Tap below to activate live POV stream",
                  fontSize = 11.sp,
                  color = AppColor.TextSecondary,
              )
              Button(
                  onClick = { cameraViewModel.startGlassesCamera() },
                  shape = RoundedCornerShape(50),
                  colors = ButtonDefaults.buttonColors(containerColor = AppColor.Lime),
              ) {
                Text("Start Glasses Camera", color = AppColor.TextInverted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      } else {
        // Phone Camera Mode: High-speed CameraX + ML Kit with Auto-Zoom (<50ms scanning)
        if (!hasCameraPermission) {
          Box(
              modifier = Modifier
                  .fillMaxSize()
                  .clip(RoundedCornerShape(24.dp))
                  .background(AppColor.BgSurface)
                  .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(24.dp)),
              contentAlignment = Alignment.Center,
          ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp),
            ) {
              CameraQrVectorIcon(accent = AppColor.Indigo, modifier = Modifier.size(44.dp))
              Text(
                  text = "Camera Permission Required",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = AppColor.TextPrimary,
              )
              Text(
                  text = "AirWallet needs permission to use your mobile camera to scan QR codes instantly.",
                  fontSize = 12.sp,
                  color = AppColor.TextSecondary,
                  textAlign = TextAlign.Center,
                  lineHeight = 16.sp,
              )
              Button(
                  onClick = onRequestCameraPermission,
                  shape = RoundedCornerShape(50),
                  colors = ButtonDefaults.buttonColors(containerColor = AppColor.Indigo),
                  modifier = Modifier.padding(top = 4.dp),
              ) {
                Text(
                    text = "Grant Camera Access",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        } else {
          val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
          var scannerInstance by remember { mutableStateOf<FastQrScanner?>(null) }

          DisposableEffect(lifecycleOwner) {
            onDispose {
              scannerInstance?.shutdown()
              scannerInstance = null
              livePreviewView = null
            }
          }

          LaunchedEffect(ui.paymentState) {
            if (ui.paymentState is PaymentState.Idle) {
              scannerInstance?.resetScanner()
            }
          }

          AndroidView(
              factory = { context ->
                PreviewView(context).apply {
                  implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                  scaleType = PreviewView.ScaleType.FILL_CENTER
                  livePreviewView = this
                  val scanner = FastQrScanner(
                      context = context,
                      lifecycleOwner = lifecycleOwner,
                      previewView = this,
                      onQrDetected = { payload ->
                        val details = QrPayloadParser.toPaymentDetails(payload, ui.selectedPaymentMethod)
                        if (details != null) {
                          cameraViewModel.onPaymentQrDetected(details)
                        }
                      }
                  )
                  scannerInstance = scanner
                  scanner.startScanning()
                }
              },
              modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
          )
        }
      }

      // Reticle Frame with Red Square Alignment Border and Active Laser Sweep
      Box(
          modifier = Modifier
              .size(204.dp)
              .border(3.dp, Color(0xFFFF3B30), RoundedCornerShape(14.dp)),
          contentAlignment = Alignment.Center,
      ) {
        ReticleCornerBracketsCanvas(color = Color(0xFFFF3B30), modifier = Modifier.fillMaxSize())

        if (ui.isStreaming || (!isGlasses && hasCameraPermission)) {
          val transition = rememberInfiniteTransition(label = "scanner_laser")
          val laserOffset by transition.animateFloat(
              initialValue = 0.06f,
              targetValue = 0.94f,
              animationSpec = infiniteRepeatable(
                  animation = tween(1600, easing = FastOutSlowInEasing),
                  repeatMode = RepeatMode.Reverse,
              ),
              label = "laser_y",
          )
          Box(
              modifier = Modifier
                  .fillMaxWidth(0.9f)
                  .height(2.dp)
                  .align(Alignment.TopCenter)
                  .offset(y = 200.dp * laserOffset)
                  .background(
                      Brush.horizontalGradient(
                          listOf(Color.Transparent, accent, Color.Transparent)
                      )
                  )
          )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
          Text(
              text = if (isGlasses) "Ray-Ban Meta (Left Rim Camera)" else if (!hasCameraPermission) "Camera Permission" else "Point Phone at QR Code",
              fontSize = 11.sp,
              color = AppColor.TextSecondary,
          )
          Text(
              text = if (ui.isStreaming || (!isGlasses && hasCameraPermission)) {
                if (isGlasses) "Align QR with Left Lens" else "Align QR with Red Square"
              } else if (!isGlasses && !hasCameraPermission) "Access Required" else "Scan QR Code",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = AppColor.TextPrimary,
              textAlign = TextAlign.Center,
          )
          if (ui.isCapturingPhoto) {
            Text(
                text = "Scanning...",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
          }
        }
      }
    }

    // Minimal Bottom Action Sheet
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppColor.BgSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Paying via", color = AppColor.TextSecondary, fontSize = 12.sp)
          Text(
              text = if (activeRail == "PayPal") "PayPal Vault (1-Tap)" else "UPI (atharv@okaxis)",
              color = AppColor.TextPrimary,
              fontWeight = FontWeight.SemiBold,
              fontSize = 12.sp,
          )
        }

        // Google Gemini Multimodal Triggers in Scanner (Real Live Camera Frame)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
              onClick = {
                val liveBmp = if (isGlasses) null else livePreviewView?.bitmap
                if (liveBmp != null) {
                  Log.i("WalletScannerScreen", "Scanning real frame from live CameraX preview: ${liveBmp.getWidth()}x${liveBmp.getHeight()}")
                  cameraViewModel.onScanObjectClicked(bitmap = liveBmp, fromGlasses = false)
                } else if (!isGlasses) {
                  scannerObjectPhotoLauncher.launch(null)
                } else {
                  cameraViewModel.onScanObjectClicked(bitmap = null, fromGlasses = true)
                }
              },
              modifier = Modifier.weight(1f).height(42.dp),
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = AppColor.Coral),
          ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
              Text("Scan Object", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }

          Button(
              onClick = {
                val liveBmp = if (isGlasses) null else livePreviewView?.bitmap
                if (liveBmp != null) {
                  Log.i("WalletScannerScreen", "Scanning real bill from live CameraX preview: ${liveBmp.getWidth()}x${liveBmp.getHeight()}")
                  cameraViewModel.onScanBillClicked(bitmap = liveBmp, fromGlasses = false)
                } else if (!isGlasses) {
                  scannerBillPhotoLauncher.launch(null)
                } else {
                  cameraViewModel.onScanBillClicked(bitmap = null, fromGlasses = true)
                }
              },
              modifier = Modifier.weight(1f).height(42.dp),
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = AppColor.Lime),
          ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(Icons.Default.Receipt, contentDescription = null, tint = AppColor.TextInverted, modifier = Modifier.size(16.dp))
              Text("Scan Bill", color = AppColor.TextInverted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }

        // Live Scanning Instructions & Guidance Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            border = BorderStroke(1.dp, Color(0xFF2C2C2E)),
            modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                  imageVector = Icons.Default.Info,
                  contentDescription = null,
                  tint = if (isGlasses) AppColor.Lime else AppColor.Indigo,
                  modifier = Modifier.size(15.dp),
              )
              Text(
                  text = if (isGlasses) "Glasses Camera Guidance" else "Scanning Instructions",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = AppColor.TextPrimary,
              )
            }
            if (isGlasses) {
              Text("• Left Lens: Camera is on your left frame rim — align QR with your left eye", fontSize = 11.sp, color = AppColor.Lime, fontWeight = FontWeight.SemiBold)
              Text("• Distance: Hold glasses 1 – 2 feet (30–60 cm) from QR code", fontSize = 11.sp, color = AppColor.TextSecondary)
              Text("• Angle: Turn head slightly right so the left camera points straight at the code", fontSize = 11.sp, color = AppColor.TextSecondary)
            } else {
              Text("• Distance: Hold phone 6 – 12 inches from QR code", fontSize = 11.sp, color = AppColor.TextSecondary)
              Text("• Sub-50ms: CameraX auto-focus and auto-zoom will instantly lock on", fontSize = 11.sp, color = AppColor.TextSecondary)
            }
          }
        }

        if (ui.isStreaming) {
          Button(
              onClick = { cameraViewModel.scanQrNow() },
              modifier = Modifier.fillMaxWidth().height(48.dp),
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = AppColor.Lime),
          ) {
            Text("Scan QR Code Now", color = AppColor.TextInverted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
          }
        } else if (ui.isSessionStarting || ui.isStartingStream) {
          Button(
              onClick = {},
              enabled = false,
              modifier = Modifier.fillMaxWidth().height(48.dp),
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF383838)),
          ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              CircularProgressIndicator(color = AppColor.TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
              Text("Starting Camera...", color = AppColor.TextPrimary, fontSize = 13.sp)
            }
          }
        } else {
          Button(
              onClick = { cameraViewModel.startGlassesCamera() },
              modifier = Modifier.fillMaxWidth().height(48.dp),
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = AppColor.Lime),
          ) {
            Text("Start Glasses Camera", color = AppColor.TextInverted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

// ==========================================
// SCREEN 4: SETTINGS & META GLASSES PAIRING
// ==========================================
@Composable
private fun WalletSettingsScreen(
    isConnected: Boolean,
    isPairing: Boolean,
    activeRail: String,
    doublePressScan: Boolean,
    onToggleDoublePress: () -> Unit,
    onTriggerConnectGlasses: () -> Unit,
    onSelectRail: (String) -> Unit,
    onOpenPayPalVaultSheet: () -> Unit,
    onBack: () -> Unit,
) {
  Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
      // Header
      Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onBack) {
          Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColor.TextSecondary)
        }
        Text("Settings", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColor.TextSecondary)
        Box(modifier = Modifier.size(24.dp))
      }

      // Ray-Ban | Meta Card (Screenshot 2 / TSX line 632)
      Surface(
          shape = RoundedCornerShape(24.dp),
          color = AppColor.BgSurface,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Box(
                  modifier = Modifier
                      .size(42.dp)
                      .clip(RoundedCornerShape(14.dp))
                      .background(if (isConnected) AppColor.Lime else Color(0xFF1E1E1E)),
                  contentAlignment = Alignment.Center,
              ) {
                GlassesVectorIcon(
                    tint = if (isConnected) AppColor.TextInverted else AppColor.TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
              }
              Column {
                Text("Ray-Ban | Meta", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColor.TextPrimary)
                Text(
                    text = if (isConnected) "Connected • 84% Battery"
                    else if (isPairing) "Pairing glasses..."
                    else "Disconnected",
                    fontSize = 11.sp,
                    color = AppColor.TextSecondary,
                )
              }
            }

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnected) AppColor.Lime
                        else if (isPairing) AppColor.Orange
                        else AppColor.TextMuted
                    )
            )
          }

          Button(
              onClick = onTriggerConnectGlasses,
              modifier = Modifier.fillMaxWidth().height(44.dp),
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(
                  containerColor = if (isConnected) Color(0xFF1E1E1E) else AppColor.Lime,
              ),
              border = if (isConnected) BorderStroke(1.dp, Color(0xFF383838)) else null,
          ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              if (isPairing) {
                CircularProgressIndicator(color = AppColor.TextInverted, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                Text("Connecting...", color = AppColor.TextInverted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              } else if (isConnected) {
                Text("Disconnect Meta Glasses", color = AppColor.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              } else {
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = AppColor.TextInverted, modifier = Modifier.size(16.dp))
                Text("Connect to Meta Glasses", color = AppColor.TextInverted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // Minimal Payment Methods (PayPal Vault & UPI)
      Surface(
          shape = RoundedCornerShape(24.dp),
          color = AppColor.BgSurface,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Payment Methods", fontSize = 11.sp, color = AppColor.TextSecondary, modifier = Modifier.padding(horizontal = 4.dp))

          // PayPal Vault
          Surface(
              shape = RoundedCornerShape(16.dp),
              color = if (activeRail == "PayPal") Color(0xFF1E1E1E) else Color.Transparent,
              modifier = Modifier.fillMaxWidth().clickable { onSelectRail("PayPal") },
          ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text("PayPal Vault", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AppColor.TextPrimary)
                Text("Connected • 1-Click", fontSize = 11.sp, color = AppColor.TextSecondary)
              }
              Box(
                  modifier = Modifier
                      .size(20.dp)
                      .clip(CircleShape)
                      .background(if (activeRail == "PayPal") AppColor.Lime else Color(0xFF383838)),
                  contentAlignment = Alignment.Center,
              ) {
                if (activeRail == "PayPal") {
                  Icon(Icons.Default.Check, contentDescription = null, tint = AppColor.TextInverted, modifier = Modifier.size(13.dp))
                }
              }
            }
          }

          // UPI
          Surface(
              shape = RoundedCornerShape(16.dp),
              color = if (activeRail == "UPI") Color(0xFF1E1E1E) else Color.Transparent,
              modifier = Modifier.fillMaxWidth().clickable { onSelectRail("UPI") },
          ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text("UPI", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AppColor.TextPrimary)
                Text("atharv@okaxis", fontSize = 11.sp, color = AppColor.TextSecondary)
              }
              Box(
                  modifier = Modifier
                      .size(20.dp)
                      .clip(CircleShape)
                      .background(if (activeRail == "UPI") AppColor.Lime else Color(0xFF383838)),
                  contentAlignment = Alignment.Center,
              ) {
                if (activeRail == "UPI") {
                  Icon(Icons.Default.Check, contentDescription = null, tint = AppColor.TextInverted, modifier = Modifier.size(13.dp))
                }
              }
            }
          }

          // Manage PayPal Vault Sheet Button
          Text(
              text = "Manage Vaulted Credentials →",
              color = AppColor.Sky,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              modifier = Modifier
                  .padding(top = 4.dp, start = 4.dp)
                  .clickable { onOpenPayPalVaultSheet() },
          )
        }
      }

      // Minimal Gesture Toggle
      Surface(
          shape = RoundedCornerShape(24.dp),
          color = AppColor.BgSurface,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text("Double-press temple", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = AppColor.TextPrimary)
            Text("Scan QR with glasses", fontSize = 11.sp, color = AppColor.TextSecondary)
          }

          Surface(
              shape = RoundedCornerShape(50),
              color = if (doublePressScan) AppColor.PillActive else Color(0xFF1E1E1E),
              modifier = Modifier.clickable { onToggleDoublePress() },
          ) {
            Text(
                text = if (doublePressScan) "On" else "Off",
                color = if (doublePressScan) AppColor.TextInverted else AppColor.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
          }
        }
      }
    }
  }
}

// ==========================================
// IN-APP NUMERIC KEYPAD COMPOSABLE
// ==========================================
@Composable
private fun InAppNumericKeypad(
    amountText: String,
    onAmountChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    currencySymbol: String = "$",
    presets: List<Double> = listOf(5.0, 10.0, 20.0, 50.0, 100.0),
) {
  Column(
      modifier = modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Quick Preset Chips Row
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      presets.forEach { preset ->
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF1E1E22),
            border = BorderStroke(1.dp, Color(0xFF383842)),
            modifier = Modifier.clickable {
              val currentVal = amountText.toDoubleOrNull() ?: 0.0
              val newVal = currentVal + preset
              onAmountChange(if (newVal % 1.0 == 0.0) newVal.toInt().toString() else "%.2f".format(newVal))
            }
        ) {
          Text(
              text = "+$currencySymbol${preset.toInt()}",
              color = AppColor.Lime,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }
      }

      // Clear chip
      if (amountText.isNotEmpty()) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF331D1D),
            border = BorderStroke(1.dp, AppColor.Coral.copy(alpha = 0.5f)),
            modifier = Modifier.clickable { onAmountChange("") }
        ) {
          Text(
              text = "Clear",
              color = AppColor.Coral,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }
      }
    }

    // Keypad Grid (3 columns, 4 rows)
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫")
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      keys.forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          row.forEach { key ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1F1F23),
                border = BorderStroke(1.dp, Color(0xFF323238)),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clickable {
                      when (key) {
                        "⌫" -> {
                          if (amountText.isNotEmpty()) {
                            onAmountChange(amountText.dropLast(1))
                          }
                        }
                        "." -> {
                          if (!amountText.contains(".") && amountText.length < 8) {
                            onAmountChange(if (amountText.isEmpty()) "0." else "$amountText.")
                          }
                        }
                        else -> { // Digit 0-9
                          if (amountText.length < 8) {
                            val parts = amountText.split(".")
                            if (parts.size < 2 || parts[1].length < 2) {
                              if (amountText == "0" && key != ".") {
                                onAmountChange(key)
                              } else {
                                onAmountChange(amountText + key)
                              }
                            }
                          }
                        }
                      }
                    },
            ) {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (key == "⌫") {
                  Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = "Backspace",
                      tint = AppColor.TextSecondary,
                      modifier = Modifier.size(18.dp)
                  )
                } else {
                  Text(
                      text = key,
                      color = AppColor.TextPrimary,
                      fontSize = 18.sp,
                      fontWeight = FontWeight.Bold,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

// SCREEN 5: SEND TO CONTACTS
// ==========================================
@Composable
private fun WalletContactsScreen(
    cameraViewModel: CameraViewModel,
    onSend: (WalletContact, Double) -> Unit,
    onBack: () -> Unit,
) {
  val contacts = remember {
    listOf(
        WalletContact("c2", "Maya Lin", "paypal.me/mayalin", "PayPal", "ML", AppColor.Sky),
        WalletContact("c1", "Alex Rivera", "alex@okaxis", "UPI", "AR", AppColor.Mint),
        WalletContact("c4", "Sara V.", "paypal.me/sarav", "PayPal", "SV", AppColor.Orange),
        WalletContact("c3", "Devon K.", "devon@ybl", "UPI", "DK", AppColor.Lavender),
    )
  }
  var selectedContact by remember { mutableStateOf(contacts[0]) }
  var amountText by remember { mutableStateOf("") }
  val scope = rememberCoroutineScope()
  val currSymbol = if (selectedContact.rail == "PayPal") "$" else "₹"
  val enteredAmount = amountText.toDoubleOrNull() ?: 0.0

  Column(
      modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Header
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColor.TextSecondary)
      }
      Text("Send to Friends", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColor.TextPrimary)
      Box(modifier = Modifier.size(24.dp))
    }

    // Contact Horizontal Picker Chips
    Text("SELECT RECIPIENT", color = AppColor.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      contacts.forEach { c ->
        val isSelected = selectedContact.id == c.id
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isSelected) Color(0xFF1E232A) else Color(0xFF161618),
            border = BorderStroke(1.dp, if (isSelected) c.color else Color(0xFF2C2C30)),
            modifier = Modifier.clickable { selectedContact = c }
        ) {
          Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(c.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
              Text(c.initials, color = c.color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Column {
              Text(c.name, color = AppColor.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              Text(c.rail, color = c.color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
          }
        }
      }
    }

    // Selected Contact Card
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AppColor.BgSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
          modifier = Modifier.padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(
              modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(selectedContact.color.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center,
          ) {
            Text(selectedContact.initials, color = selectedContact.color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
          }
          Column {
            Text(selectedContact.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColor.TextPrimary)
            Text(selectedContact.handle, fontSize = 11.sp, color = AppColor.TextSecondary)
          }
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = selectedContact.color.copy(alpha = 0.15f),
        ) {
          Text(
              text = selectedContact.rail,
              color = selectedContact.color,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }
    }

    // Amount Display Card
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppColor.BgSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
          modifier = Modifier.padding(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text("Amount to Send", fontSize = 11.sp, color = AppColor.TextSecondary)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
          Text("$currSymbol ", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (enteredAmount > 0) AppColor.Lime else AppColor.TextMuted)
          Text(
              text = if (amountText.isEmpty()) "0.00" else amountText,
              fontSize = 32.sp,
              fontWeight = FontWeight.Bold,
              color = if (enteredAmount > 0) AppColor.TextPrimary else AppColor.TextMuted,
          )
        }
      }
    }

    // Numeric Keypad
    InAppNumericKeypad(
        amountText = amountText,
        onAmountChange = { amountText = it },
        currencySymbol = currSymbol,
        presets = if (selectedContact.rail == "PayPal") listOf(5.0, 10.0, 25.0, 50.0, 100.0) else listOf(50.0, 100.0, 200.0, 500.0),
    )

    // Action Buttons
    if (selectedContact.rail == "PayPal") {
      Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Send Money Button (Instant PayPal Transfer)
        Button(
            onClick = {
              if (enteredAmount > 0) {
                val friend = cameraViewModel.defaultFriends.find { it.name.contains(selectedContact.name, ignoreCase = true) }
                    ?: com.meta.wearable.dat.externalsampleapps.cameraaccess.camera.FriendContact(selectedContact.id, selectedContact.name, selectedContact.handle, "PayPal")
                cameraViewModel.triggerPayPalPayout(friend, enteredAmount)
                onBack()
              }
            },
            enabled = enteredAmount > 0,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = AppColor.Lime),
        ) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(Icons.Default.Send, contentDescription = null, tint = AppColor.TextInverted, modifier = Modifier.size(16.dp))
            Text(
                text = if (enteredAmount > 0) "Send $${"%.2f".format(enteredAmount)} to ${selectedContact.name}" else "Enter Amount to Send",
                color = AppColor.TextInverted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
          }
        }

        // Request Money Button (PayPal Invoice)
        Button(
            onClick = {
              if (enteredAmount > 0) {
                val email = if (selectedContact.handle.contains("@")) selectedContact.handle else "${selectedContact.handle.replace("paypal.me/", "")}@sandbox.paypal.com"
                scope.launch {
                  cameraViewModel.payPalInvoiceService.createAndSendInvoice(
                      recipientEmail = email,
                      recipientName = selectedContact.name,
                      amount = enteredAmount,
                      note = "Payment Request from AirWallet"
                  )
                }
                onBack()
              }
            },
            enabled = enteredAmount > 0,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = AppColor.Sky),
        ) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text("✉️", fontSize = 14.sp)
            Text(
                text = if (enteredAmount > 0) "Request $${"%.2f".format(enteredAmount)} from ${selectedContact.name}" else "Enter Amount to Request",
                color = AppColor.TextInverted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    } else {
      // Send Button (UPI)
      Button(
          onClick = {
            if (enteredAmount > 0) {
              onSend(selectedContact, enteredAmount)
            }
          },
          enabled = enteredAmount > 0,
          modifier = Modifier.fillMaxWidth().height(50.dp).padding(bottom = 16.dp),
          shape = RoundedCornerShape(50),
          colors = ButtonDefaults.buttonColors(containerColor = AppColor.Lavender),
      ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(Icons.Default.Send, contentDescription = null, tint = AppColor.TextInverted, modifier = Modifier.size(16.dp))
          Text(
              text = if (enteredAmount > 0) "Send ₹${"%.2f".format(enteredAmount)} via ${selectedContact.rail}" else "Enter Amount on Keypad to Send",
              color = AppColor.TextInverted,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

// ==========================================
// CUSTOM VECTOR ICONS (Matching TSX Screenshot 3)
// ==========================================

@Composable
private fun CameraQrVectorIcon(accent: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val stroke = 2.dp.toPx()
    // Camera body
    drawRoundRect(
        color = Color(0xFFEAEAEA),
        topLeft = Offset(size.width * 0.12f, size.height * 0.25f),
        size = Size(size.width * 0.65f, size.height * 0.50f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
        style = Stroke(width = stroke),
    )
    // Camera lens circle
    drawCircle(
        color = Color(0xFFEAEAEA),
        radius = size.width * 0.12f,
        center = Offset(size.width * 0.44f, size.height * 0.50f),
        style = Stroke(width = stroke),
    )
    // Mini QR code block at bottom right
    val qrLeft = size.width * 0.60f
    val qrTop = size.height * 0.55f
    val qrSize = size.width * 0.35f
    drawRoundRect(
        color = Color(0xFF1E1E1E),
        topLeft = Offset(qrLeft, qrTop),
        size = Size(qrSize, qrSize),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
    )
    drawRoundRect(
        color = accent,
        topLeft = Offset(qrLeft, qrTop),
        size = Size(qrSize, qrSize),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx()),
    )
    // QR accent squares
    val dot = qrSize * 0.22f
    drawRect(color = accent, topLeft = Offset(qrLeft + qrSize * 0.18f, qrTop + qrSize * 0.18f), size = Size(dot, dot))
    drawRect(color = accent, topLeft = Offset(qrLeft + qrSize * 0.60f, qrTop + qrSize * 0.18f), size = Size(dot, dot))
    drawRect(color = accent, topLeft = Offset(qrLeft + qrSize * 0.18f, qrTop + qrSize * 0.60f), size = Size(dot, dot))
    drawRect(color = Color(0xFFEAEAEA), topLeft = Offset(qrLeft + qrSize * 0.60f, qrTop + qrSize * 0.60f), size = Size(dot, dot))
  }
}

@Composable
private fun GlassesQrVectorIcon(accent: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val stroke = 2.dp.toPx()
    // Left lens
    drawRoundRect(
        color = Color(0xFFEAEAEA),
        topLeft = Offset(size.width * 0.14f, size.height * 0.38f),
        size = Size(size.width * 0.30f, size.height * 0.24f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
        style = Stroke(width = stroke),
    )
    // Right lens
    drawRoundRect(
        color = Color(0xFFEAEAEA),
        topLeft = Offset(size.width * 0.56f, size.height * 0.38f),
        size = Size(size.width * 0.30f, size.height * 0.24f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
        style = Stroke(width = stroke),
    )
    // Bridge
    drawLine(
        color = Color(0xFFEAEAEA),
        start = Offset(size.width * 0.44f, size.height * 0.44f),
        end = Offset(size.width * 0.56f, size.height * 0.44f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    // Mini QR badge
    val qrLeft = size.width * 0.60f
    val qrTop = size.height * 0.55f
    val qrSize = size.width * 0.35f
    drawRoundRect(
        color = Color(0xFF1E1E1E),
        topLeft = Offset(qrLeft, qrTop),
        size = Size(qrSize, qrSize),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
    )
    drawRoundRect(
        color = accent,
        topLeft = Offset(qrLeft, qrTop),
        size = Size(qrSize, qrSize),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx()),
    )
    val dot = qrSize * 0.22f
    drawRect(color = accent, topLeft = Offset(qrLeft + qrSize * 0.18f, qrTop + qrSize * 0.18f), size = Size(dot, dot))
    drawRect(color = accent, topLeft = Offset(qrLeft + qrSize * 0.60f, qrTop + qrSize * 0.18f), size = Size(dot, dot))
    drawRect(color = accent, topLeft = Offset(qrLeft + qrSize * 0.18f, qrTop + qrSize * 0.60f), size = Size(dot, dot))
    drawRect(color = Color(0xFFEAEAEA), topLeft = Offset(qrLeft + qrSize * 0.60f, qrTop + qrSize * 0.60f), size = Size(dot, dot))
  }
}

@Composable
private fun ContactCardVectorIcon(accent: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val stroke = 2.dp.toPx()
    // Card outline
    drawRoundRect(
        color = Color(0xFFEAEAEA),
        topLeft = Offset(size.width * 0.14f, size.height * 0.25f),
        size = Size(size.width * 0.72f, size.height * 0.50f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
        style = Stroke(width = stroke),
    )
    // Avatar circle
    drawCircle(
        color = accent,
        radius = size.width * 0.08f,
        center = Offset(size.width * 0.34f, size.height * 0.44f),
    )
    // Lines
    drawLine(
        color = Color(0xFFEAEAEA),
        start = Offset(size.width * 0.52f, size.height * 0.42f),
        end = Offset(size.width * 0.72f, size.height * 0.42f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Color(0xFF8E8E93),
        start = Offset(size.width * 0.52f, size.height * 0.54f),
        end = Offset(size.width * 0.66f, size.height * 0.54f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
  }
}

@Composable
private fun ReceiptHistoryVectorIcon(accent: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val stroke = 2.dp.toPx()
    // Receipt document
    drawRoundRect(
        color = Color(0xFFEAEAEA),
        topLeft = Offset(size.width * 0.20f, size.height * 0.18f),
        size = Size(size.width * 0.50f, size.height * 0.64f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
        style = Stroke(width = stroke),
    )
    // Lines
    drawLine(
        color = accent,
        start = Offset(size.width * 0.32f, size.height * 0.36f),
        end = Offset(size.width * 0.58f, size.height * 0.36f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Color(0xFFEAEAEA),
        start = Offset(size.width * 0.32f, size.height * 0.50f),
        end = Offset(size.width * 0.58f, size.height * 0.50f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    // Corner accent tab
    drawRoundRect(
        color = accent,
        topLeft = Offset(size.width * 0.62f, size.height * 0.52f),
        size = Size(size.width * 0.20f, size.height * 0.22f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx()),
    )
  }
}

@Composable
private fun GlassesVectorIcon(tint: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val stroke = 2.dp.toPx()
    drawRoundRect(
        color = tint,
        topLeft = Offset(size.width * 0.08f, size.height * 0.35f),
        size = Size(size.width * 0.36f, size.height * 0.30f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
        style = Stroke(width = stroke),
    )
    drawRoundRect(
        color = tint,
        topLeft = Offset(size.width * 0.56f, size.height * 0.35f),
        size = Size(size.width * 0.36f, size.height * 0.30f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
        style = Stroke(width = stroke),
    )
    drawLine(
        color = tint,
        start = Offset(size.width * 0.44f, size.height * 0.48f),
        end = Offset(size.width * 0.56f, size.height * 0.48f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
  }
}

@Composable
private fun BillSplitVectorIcon(accent: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val stroke = 2.dp.toPx()
    // Bill document
    drawRoundRect(
        color = Color(0xFFEAEAEA),
        topLeft = Offset(size.width * 0.15f, size.height * 0.15f),
        size = Size(size.width * 0.70f, size.height * 0.70f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
        style = Stroke(width = stroke),
    )
    // Vertical dashed split line down the center
    drawLine(
        color = accent,
        start = Offset(size.width * 0.50f, size.height * 0.20f),
        end = Offset(size.width * 0.50f, size.height * 0.80f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    // Left & right person dots
    drawCircle(color = accent, radius = 2.5.dp.toPx(), center = Offset(size.width * 0.32f, size.height * 0.42f))
    drawCircle(color = accent, radius = 2.5.dp.toPx(), center = Offset(size.width * 0.68f, size.height * 0.42f))
  }
}

@Composable
private fun PayoutVectorIcon(accent: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val stroke = 2.dp.toPx()
    // Paper airplane / instant disbursement trajectory
    val path = Path().apply {
      moveTo(size.width * 0.15f, size.height * 0.50f)
      lineTo(size.width * 0.85f, size.height * 0.18f)
      lineTo(size.width * 0.55f, size.height * 0.85f)
      lineTo(size.width * 0.42f, size.height * 0.58f)
      close()
    }
    drawPath(path = path, color = accent, style = Stroke(width = stroke, join = StrokeJoin.Round))
  }
}

/**
 * Itemized PayPal Smart Receipt Dialog (PayPal Orders v2).
 * Displays rich line items, quantities, subtotal, tax breakdown, and PayPal Capture ID.
 */
@Composable
private fun PayPalSmartReceiptDialog(
    tx: WalletTransaction,
    cameraViewModel: CameraViewModel,
    onDismiss: () -> Unit,
) {
  val breakdown = remember(tx) {
    cameraViewModel.payPalPaymentService.generateSmartItemizedBreakdown(tx.merchant, tx.amount)
  }

  AlertDialog(
      onDismissRequest = onDismiss,
      containerColor = Color(0xFF1E1E1E),
      shape = RoundedCornerShape(24.dp),
      confirmButton = {
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = AppColor.Sky),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
          Text("Done", color = Color(0xFF121212), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
      },
      text = {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Top badge
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Box(
                  modifier = Modifier
                      .size(24.dp)
                      .clip(CircleShape)
                      .background(AppColor.Sky.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center
              ) {
                Text("P", color = AppColor.Sky, fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }
              Text("PayPal Orders v2", color = AppColor.Sky, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = AppColor.PillActive,
            ) {
              Text(
                  text = tx.category,
                  color = AppColor.TextPrimary,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
          }

          // Merchant & Amount
          Column {
            Text(tx.merchant, color = AppColor.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val captureId = tx.referenceId.ifEmpty { tx.id }
            Text("Capture ID: $captureId", color = AppColor.TextSecondary, fontSize = 10.sp)
          }

          Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2C2C2E)))

          // Itemized Line Items
          Text("ITEMIZED BREAKDOWN", color = AppColor.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
          breakdown.items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
              Text("${item.quantity}x ${item.name}", color = AppColor.TextPrimary, fontSize = 12.sp)
              Text("$${"%.2f".format(item.unitAmount * item.quantity)}", color = AppColor.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
          }

          Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2C2C2E)))

          // Subtotal, Tax, Tip, Total
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Items Subtotal", color = AppColor.TextSecondary, fontSize = 11.sp)
            Text("$${"%.2f".format(breakdown.itemTotal)}", color = AppColor.TextSecondary, fontSize = 11.sp)
          }
          if (breakdown.taxTotal > 0.0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Estimated Tax (8.25%)", color = AppColor.TextSecondary, fontSize = 11.sp)
              Text("$${"%.2f".format(breakdown.taxTotal)}", color = AppColor.TextSecondary, fontSize = 11.sp)
            }
          }
          if (breakdown.tipAmount > 0.0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Tip / Service", color = AppColor.TextSecondary, fontSize = 11.sp)
              Text("$${"%.2f".format(breakdown.tipAmount)}", color = AppColor.TextSecondary, fontSize = 11.sp)
            }
          }
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Grand Total", color = AppColor.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("$${"%.2f".format(breakdown.grandTotal)}", color = AppColor.Lime, fontSize = 16.sp, fontWeight = FontWeight.Bold)
          }

          // Source info
          Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFF141414),
              modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
          ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text("💳", fontSize = 14.sp)
              Column {
                Text(
                    text = if (tx.fundingSource.isNotBlank()) tx.fundingSource else "PayPal Vault Pre-Authorized",
                    color = AppColor.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Status: Captured & Settled via Orders v2",
                    color = AppColor.TextSecondary,
                    fontSize = 9.sp
                )
              }
            }
          }
        }
      }
  )
}

/**
 * Interactive Bill Splitting & Invoicing Dialog (PayPal Invoicing v2).
 * Allows custom total amount entry and multi-select of ANY friends with live math.
 */
@Composable
private fun PayPalBillSplitDialog(
    cameraViewModel: CameraViewModel,
    initialAmount: Double = 0.0,
    onDismiss: () -> Unit,
) {
  var billAmountText by remember {
    mutableStateOf(
        if (initialAmount > 0.0) {
          if (initialAmount % 1.0 == 0.0) initialAmount.toInt().toString() else "%.2f".format(initialAmount)
        } else {
          ""
        }
    )
  }
  val friends = cameraViewModel.defaultFriends
  // Set of selected friend IDs - start empty so wearer has full control to choose receivers
  val selectedFriendIds = remember { mutableStateListOf<String>() }
  val splitNote = "Dinner & Drinks Split"
  var isSending by remember { mutableStateOf(false) }
  var splitResult by remember { mutableStateOf<BillSplitResult?>(null) }
  var showKeypad by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  AlertDialog(
      onDismissRequest = onDismiss,
      containerColor = Color(0xFF1E1E1E),
      shape = RoundedCornerShape(24.dp),
      confirmButton = {
        if (splitResult != null) {
          Button(
              onClick = onDismiss,
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = AppColor.Sky),
              modifier = Modifier.fillMaxWidth().height(44.dp)
          ) {
            Text("Done", color = Color(0xFF121212), fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }
        } else {
          val totalAmt = billAmountText.toDoubleOrNull() ?: 0.0
          val totalPeople = selectedFriendIds.size + 1
          val perPerson = if (totalPeople > 0) totalAmt / totalPeople else 0.0
          Button(
              onClick = {
                if (totalAmt > 0 && selectedFriendIds.isNotEmpty() && !isSending) {
                  isSending = true
                  scope.launch {
                    val chosen = friends.filter { selectedFriendIds.contains(it.id) }
                    val splitFriends = chosen.map {
                      val email = if (it.handle.contains("paypal.me/")) {
                        "${it.handle.replace("paypal.me/", "")}@sandbox.paypal.com"
                      } else if (it.handle.contains("@")) {
                        "${it.name.lowercase().replace(" ", "")}@sandbox.paypal.com"
                      } else {
                        "${it.handle}@sandbox.paypal.com"
                      }
                      it.name to email
                    }
                    val result = cameraViewModel.payPalInvoiceService.splitBill(totalAmt, splitFriends, splitNote)
                    result.fold(
                        onSuccess = { res ->
                          splitResult = res
                          isSending = false
                          cameraViewModel.voicePrompter.speak("Sent PayPal invoices of $${"%.2f".format(res.perPersonAmount)} each to ${chosen.joinToString(" and ") { it.name }}.")
                        },
                        onFailure = {
                          isSending = false
                        }
                    )
                  }
                }
              },
              enabled = (billAmountText.toDoubleOrNull() ?: 0.0) > 0 && selectedFriendIds.isNotEmpty() && !isSending,
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = AppColor.Sky),
              modifier = Modifier.fillMaxWidth().height(48.dp)
          ) {
            if (isSending) {
              CircularProgressIndicator(color = Color(0xFF121212), modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
              Text(
                  text = if (selectedFriendIds.isEmpty()) "Select at least 1 friend" else "Send PayPal Invoices ($${"%.2f".format(perPerson)} ea)",
                  color = Color(0xFF121212),
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp
              )
            }
          }
        }
      },
      dismissButton = {
        if (splitResult == null) {
          TextButton(onClick = onDismiss) {
            Text("Cancel", color = AppColor.TextSecondary)
          }
        }
      },
      text = {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (splitResult != null) {
            // SUCCESS STATE
            val res = splitResult!!
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text("✅", fontSize = 28.sp)
              Text("Invoices Dispatched!", color = AppColor.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
              Text(
                  text = "Total $${"%.2f".format(res.billTotal)} split between ${res.friendsInvolved.size + 1} people",
                  color = AppColor.TextSecondary,
                  fontSize = 12.sp
              )
              Text(
                  text = "$${"%.2f".format(res.perPersonAmount)} per person",
                  color = AppColor.Lime,
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold
              )

              Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2C2C2E)))

              Text("PAYPAL INVOICES (v2)", color = AppColor.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
              res.invoices.forEach { inv ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF141414),
                    modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                      modifier = Modifier.padding(10.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column {
                      Text(inv.recipientName, color = AppColor.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                      Text(inv.invoiceId, color = AppColor.Sky, fontSize = 10.sp)
                    }
                    Text("$${"%.2f".format(inv.totalAmount)}", color = AppColor.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          } else {
            // INTERACTIVE SETUP STATE
            Column {
              Text("Split Bill & Invoicing", color = AppColor.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
              Text("PayPal Invoicing v2 • Choose receivers", color = AppColor.Sky, fontSize = 11.sp)
            }

            // Amount Input Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF141414),
                modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                  modifier = Modifier.padding(10.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text("Total Bill Amount", color = AppColor.TextSecondary, fontSize = 11.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                  Text("$ ", color = AppColor.TextSecondary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                  BasicTextField(
                      value = billAmountText,
                      onValueChange = { billAmountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                      textStyle = TextStyle(
                          color = AppColor.TextPrimary,
                          fontSize = 26.sp,
                          fontWeight = FontWeight.Bold,
                          textAlign = TextAlign.Center
                      ),
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      cursorBrush = SolidColor(AppColor.Sky),
                      decorationBox = { innerTextField ->
                        if (billAmountText.isEmpty()) {
                          Text(
                              "0.00",
                              color = AppColor.TextSecondary.copy(alpha = 0.5f),
                              fontSize = 26.sp,
                              fontWeight = FontWeight.Bold,
                              textAlign = TextAlign.Center
                          )
                        }
                        innerTextField()
                      },
                      modifier = Modifier.width(110.dp)
                  )
                }
                // Quick chips: adjustments & presets
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  listOf("+10" to 10.0, "+25" to 25.0, "+50" to 50.0).forEach { (label, delta) ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF242426),
                        modifier = Modifier.clickable {
                          val current = billAmountText.toDoubleOrNull() ?: 0.0
                          val updated = current + delta
                          billAmountText = if (updated % 1.0 == 0.0) updated.toInt().toString() else "%.2f".format(updated)
                        }
                    ) {
                      Text(
                          text = label,
                          color = AppColor.Sky,
                          fontSize = 11.sp,
                          fontWeight = FontWeight.SemiBold,
                          modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                      )
                    }
                  }
                  listOf("30", "60", "90").forEach { preset ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (billAmountText == preset) AppColor.Sky.copy(alpha = 0.25f) else Color(0xFF242426),
                        modifier = Modifier.clickable { billAmountText = preset }
                    ) {
                      Text(
                          text = "$$preset",
                          color = if (billAmountText == preset) AppColor.Sky else AppColor.TextSecondary,
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Medium,
                          modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                      )
                    }
                  }
                }

                // Keypad Toggle & Embedded Keypad
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (showKeypad) AppColor.Sky.copy(alpha = 0.2f) else Color(0xFF242426),
                    border = BorderStroke(1.dp, if (showKeypad) AppColor.Sky else Color(0xFF383838)),
                    modifier = Modifier.clickable { showKeypad = !showKeypad }
                ) {
                  Text(
                      text = if (showKeypad) "▲ Close Keypad" else "⌨ Tap to Type Amount (Keypad)",
                      color = if (showKeypad) AppColor.Sky else AppColor.TextSecondary,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                  )
                }

                if (showKeypad) {
                  InAppNumericKeypad(
                      amountText = billAmountText,
                      onAmountChange = { billAmountText = it },
                      currencySymbol = "$",
                      presets = listOf(20.0, 40.0, 60.0, 80.0, 100.0)
                  )
                }
              }
            }

            // Friend Selection Section with Select All & Clear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
              Text("SELECT RECEIVERS", color = AppColor.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Select All",
                    color = AppColor.Sky,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                      selectedFriendIds.clear()
                      selectedFriendIds.addAll(friends.map { it.id })
                    }
                )
                Text(
                    text = "Clear",
                    color = AppColor.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.clickable {
                      selectedFriendIds.clear()
                    }
                )
              }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              friends.forEach { f ->
                val isSelected = selectedFriendIds.contains(f.id)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) Color(0xFF252830) else Color(0xFF141414),
                    border = BorderStroke(1.dp, if (isSelected) AppColor.Sky else Color(0xFF2C2C2E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                          if (isSelected) selectedFriendIds.remove(f.id) else selectedFriendIds.add(f.id)
                        }
                ) {
                  Row(
                      modifier = Modifier.padding(8.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Box(
                          modifier = Modifier
                              .size(26.dp)
                              .clip(CircleShape)
                              .background(if (isSelected) AppColor.Sky else Color(0xFF333336)),
                          contentAlignment = Alignment.Center
                      ) {
                        Text(
                            text = if (isSelected) "✓" else "+",
                            color = if (isSelected) Color(0xFF121212) else AppColor.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                      }
                      Column {
                        Text(f.name, color = AppColor.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(f.handle, color = AppColor.TextSecondary, fontSize = 10.sp)
                      }
                    }
                    Text(
                        text = if (isSelected) "Included" else "Tap to Add",
                        color = if (isSelected) AppColor.Sky else AppColor.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                  }
                }
              }
            }

            // Live Math Summary Box
            val totalAmt = billAmountText.toDoubleOrNull() ?: 0.0
            val peopleCount = selectedFriendIds.size + 1
            val perPerson = if (peopleCount > 0) totalAmt / peopleCount else 0.0
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF18181A),
                modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                  modifier = Modifier.padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text("Split: You + ${selectedFriendIds.size} friends", color = AppColor.TextSecondary, fontSize = 11.sp)
                  Text("Each person's share:", color = AppColor.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("$${"%.2f".format(perPerson)}", color = AppColor.Lime, fontSize = 17.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
  )
}

// ==========================================
// DATA VISUALIZATION CANVASES
// ==========================================

@Composable
private fun CategorizedPieCanvas(
    categories: List<CategorySpend>,
    modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val strokeWidth = 22.dp.toPx()

    if (categories.isEmpty() || categories.all { it.amount <= 0.0 }) {
      drawCircle(
          color = Color(0xFF2C2C2E),
          radius = radius - strokeWidth / 2f,
          center = center,
          style = Stroke(width = strokeWidth)
      )
      return@Canvas
    }

    val totalAmount = categories.sumOf { it.amount }
    val gapAngle = if (categories.size > 1) 4f else 0f
    val totalGaps = gapAngle * categories.size
    val availableDegrees = 360f - totalGaps

    var currentAngle = -90f

    categories.forEach { cat ->
      val sweep = ((cat.amount / totalAmount) * availableDegrees).toFloat().coerceAtLeast(2f)
      drawArc(
          color = Color(cat.colorHex),
          startAngle = currentAngle,
          sweepAngle = sweep,
          useCenter = false,
          topLeft = Offset(center.x - radius + strokeWidth / 2f, center.y - radius + strokeWidth / 2f),
          size = Size((radius - strokeWidth / 2f) * 2f, (radius - strokeWidth / 2f) * 2f),
          style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
      )
      currentAngle += sweep + gapAngle
    }
  }
}

@Composable
private fun WeeklySpendLineCanvas(
    weeklyTrend: List<DaySpend>,
    lineColor: Color = AppColor.Lime,
    modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    if (weeklyTrend.isEmpty()) return@Canvas

    val w = size.width
    val h = size.height
    val paddingBottom = 16.dp.toPx()
    val paddingTop = 16.dp.toPx()
    val paddingHorizontal = 18.dp.toPx()
    val chartH = (h - paddingTop - paddingBottom).coerceAtLeast(10f)
    val chartW = (w - (paddingHorizontal * 2f)).coerceAtLeast(10f)

    val maxAmount = (weeklyTrend.maxOfOrNull { it.amount } ?: 10.0).coerceAtLeast(10.0)
    val count = weeklyTrend.size
    val stepX = if (count > 1) chartW / (count - 1) else chartW

    val points = weeklyTrend.mapIndexed { i, day ->
      val x = paddingHorizontal + (i * stepX)
      val ratio = (day.amount / maxAmount).toFloat().coerceIn(0f, 1f)
      val y = (paddingTop + chartH) - (ratio * chartH)
      Offset(x, y)
    }

    val baselineY = paddingTop + chartH
    drawLine(
        color = Color(0xFF2C2C2E),
        start = Offset(paddingHorizontal, baselineY),
        end = Offset(w - paddingHorizontal, baselineY),
        strokeWidth = 1.dp.toPx()
    )

    val linePath = Path()
    val fillPath = Path()

    linePath.moveTo(points.first().x, points.first().y)
    fillPath.moveTo(points.first().x, baselineY)
    fillPath.lineTo(points.first().x, points.first().y)

    for (i in 0 until points.size - 1) {
      val p0 = points[i]
      val p1 = points[i + 1]
      val cx = (p0.x + p1.x) / 2f
      linePath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
      fillPath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
    }

    fillPath.lineTo(points.last().x, baselineY)
    fillPath.close()

    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(lineColor.copy(alpha = 0.30f), lineColor.copy(alpha = 0.03f), Color.Transparent),
            startY = paddingTop,
            endY = baselineY
        )
    )

    drawPath(
        path = linePath,
        color = lineColor,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    points.forEachIndexed { index, pt ->
      val day = weeklyTrend[index]
      if (day.isToday) {
        drawCircle(
            color = AppColor.Sky.copy(alpha = 0.35f),
            radius = 7.dp.toPx(),
            center = pt
        )
        drawCircle(
            color = AppColor.Sky,
            radius = 4.dp.toPx(),
            center = pt
        )
      } else if (day.amount > 0) {
        drawCircle(
            color = lineColor.copy(alpha = 0.25f),
            radius = 5.dp.toPx(),
            center = pt
        )
        drawCircle(
            color = lineColor,
            radius = 3.dp.toPx(),
            center = pt
        )
      } else {
        drawCircle(
            color = Color(0xFF3A3A3C),
            radius = 2.dp.toPx(),
            center = pt
        )
      }
    }
  }
}

@Composable
private fun ReticleCornerBracketsCanvas(color: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val stroke = 3.dp.toPx()
    val cornerLen = 24.dp.toPx()
    val w = size.width
    val h = size.height

    // Top Left
    drawLine(color = color, start = Offset(0f, 0f), end = Offset(cornerLen, 0f), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color = color, start = Offset(0f, 0f), end = Offset(0f, cornerLen), strokeWidth = stroke, cap = StrokeCap.Round)

    // Top Right
    drawLine(color = color, start = Offset(w, 0f), end = Offset(w - cornerLen, 0f), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color = color, start = Offset(w, 0f), end = Offset(w, cornerLen), strokeWidth = stroke, cap = StrokeCap.Round)

    // Bottom Left
    drawLine(color = color, start = Offset(0f, h), end = Offset(cornerLen, h), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color = color, start = Offset(0f, h), end = Offset(0f, h - cornerLen), strokeWidth = stroke, cap = StrokeCap.Round)

    // Bottom Right
    drawLine(color = color, start = Offset(w, h), end = Offset(w - cornerLen, h), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color = color, start = Offset(w, h), end = Offset(w, h - cornerLen), strokeWidth = stroke, cap = StrokeCap.Round)
  }
}

@Composable
fun VoiceQaListeningModal(
    liveTranscript: String,
    onQuestionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  val transition = rememberInfiniteTransition(label = "pulse_mic")
  val pulseScale by transition.animateFloat(
      initialValue = 0.95f,
      targetValue = 1.18f,
      animationSpec = infiniteRepeatable(
          animation = tween(900, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
      ),
      label = "mic_scale"
  )
  val pulseAlpha by transition.animateFloat(
      initialValue = 0.20f,
      targetValue = 0.65f,
      animationSpec = infiniteRepeatable(
          animation = tween(900, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
      ),
      label = "mic_alpha"
  )

  Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
      Surface(
          shape = RoundedCornerShape(28.dp),
          color = AppColor.BgSurface,
          border = BorderStroke(1.dp, AppColor.Sky.copy(alpha = 0.4f)),
          shadowElevation = 16.dp,
          modifier = Modifier
              .fillMaxWidth(0.92f)
              .clickable(enabled = false) {}
              .padding(vertical = 16.dp)
      ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          // Top Header Row
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Box(
                  modifier = Modifier
                      .size(28.dp)
                      .background(AppColor.Sky.copy(alpha = 0.2f), CircleShape),
                  contentAlignment = Alignment.Center
              ) {
                Text("✦", color = AppColor.Sky, fontSize = 14.sp)
              }
              Text(
                  text = "AI Voice Assistant",
                  color = AppColor.TextPrimary,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold
              )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
              Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Close",
                  tint = AppColor.TextSecondary
              )
            }
          }

          // Pulsing Mic Graphic
          Box(
              modifier = Modifier
                  .size(110.dp)
                  .padding(8.dp),
              contentAlignment = Alignment.Center,
          ) {
            Box(
                modifier = Modifier
                    .size((85 * pulseScale).dp)
                    .background(AppColor.Sky.copy(alpha = pulseAlpha * 0.3f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .background(AppColor.Sky.copy(alpha = 0.2f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        Brush.linearGradient(listOf(AppColor.Sky, AppColor.Indigo)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
              Icon(
                  imageVector = Icons.Default.Mic,
                  contentDescription = "Listening",
                  tint = Color.White,
                  modifier = Modifier.size(26.dp)
              )
            }
          }

          Text(
              text = "Listening on Smart Glasses & Phone...",
              color = AppColor.Sky,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold
          )

          // Live Transcription Display Box
          Surface(
              shape = RoundedCornerShape(16.dp),
              color = AppColor.BgCanvas,
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
              modifier = Modifier.fillMaxWidth()
          ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
              if (liveTranscript.isNotBlank()) {
                Text(
                    text = "\"$liveTranscript\"",
                    color = AppColor.TextPrimary,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center
                )
              } else {
                Text(
                    text = "Speak your question or tap a suggestion below",
                    color = AppColor.TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
              }
            }
          }

          // Prompt Suggestion Chips
          Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
                text = "Suggested Questions",
                color = AppColor.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            listOf(
                "What was my highest expense?",
                "What is my 7-day burn forecast?",
                "How much did I spend this week?",
                "Compare PayPal vs UPI spend"
            ).forEach { prompt ->
              Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = AppColor.BgCanvas,
                  border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                  modifier = Modifier
                      .fillMaxWidth()
                      .clickable { onQuestionSelected(prompt) }
              ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Text("💡", fontSize = 12.sp)
                  Text(
                      text = prompt,
                      color = AppColor.TextPrimary,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Medium
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

// ==========================================
// SCREEN 9: GOOGLE GEMINI 2.0 FLASH MULTIMODAL DIALOGS
// ====================================================

@Composable
fun GeminiResultDialog(
    cameraViewModel: CameraViewModel,
    ui: CameraUiState,
    onDismiss: () -> Unit,
    onOpenApiKeyDialog: () -> Unit,
) {
  val context = LocalContext.current
  val objResult = ui.geminiObjectResult
  val billResult = ui.geminiBillResult
  val isLoading = ui.isGeminiLoading
  val activeTab = ui.geminiActiveTab

  val rescanObjectPhotoLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.TakePicturePreview()
  ) { capturedBitmap ->
    if (capturedBitmap != null) {
      Log.i("GeminiDialog", "Object photo captured: ${capturedBitmap.getWidth()}x${capturedBitmap.getHeight()}")
      cameraViewModel.onScanObjectClicked(bitmap = capturedBitmap, fromGlasses = false)
    }
  }

  val rescanBillPhotoLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.TakePicturePreview()
  ) { capturedBitmap ->
    if (capturedBitmap != null) {
      Log.i("GeminiDialog", "Bill photo captured: ${capturedBitmap.getWidth()}x${capturedBitmap.getHeight()}")
      cameraViewModel.onScanBillClicked(bitmap = capturedBitmap, fromGlasses = false)
    }
  }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (!isGranted) {
      Log.w("GeminiDialog", "Camera permission denied by user")
    }
  }

  val safeRescanObjectPhoto = {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      try {
        rescanObjectPhotoLauncher.launch(null)
      } catch (e: Exception) {
        Log.e("GeminiDialog", "Cannot launch camera preview: ${e.message}")
      }
    } else {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  val safeRescanBillPhoto = {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      try {
        rescanBillPhotoLauncher.launch(null)
      } catch (e: Exception) {
        Log.e("GeminiDialog", "Cannot launch camera preview: ${e.message}")
      }
    } else {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(24.dp),
        color = AppColor.BgSurface,
        border = BorderStroke(1.dp, Color(0xFF383838)),
        shadowElevation = 24.dp,
    ) {
      Column(
          modifier = Modifier
              .padding(20.dp)
              .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Surface(
                shape = CircleShape,
                color = AppColor.Coral.copy(alpha = 0.2f),
                modifier = Modifier.size(34.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AppColor.Coral,
                    modifier = Modifier.size(18.dp)
                )
              }
            }
            Column {
              Text(
                  text = "Google Gemini 2.0 Flash",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = AppColor.TextPrimary,
              )
              Text(
                  text = "Multimodal Visual AI • 1-Tap PayPal",
                  fontSize = 11.sp,
                  color = AppColor.TextSecondary,
              )
            }
          }

          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            IconButton(onClick = onOpenApiKeyDialog, modifier = Modifier.size(32.dp)) {
              Icon(Icons.Default.Key, contentDescription = "API Key", tint = AppColor.TextSecondary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = AppColor.TextSecondary, modifier = Modifier.size(18.dp))
            }
          }
        }

        // Mode Switcher Pills: Object vs Bill
        Surface(
            shape = RoundedCornerShape(50),
            color = AppColor.BgCanvas,
            modifier = Modifier.fillMaxWidth(),
        ) {
          Row(modifier = Modifier.padding(3.dp)) {
            val isObj = activeTab == "object"
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isObj) AppColor.Coral else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                      if (ui.hasSession || ui.isSessionActive || ui.isStreaming) cameraViewModel.onScanObjectClicked(fromGlasses = true)
                      else safeRescanObjectPhoto()
                    },
            ) {
              Row(
                  modifier = Modifier.padding(vertical = 8.dp),
                  horizontalArrangement = Arrangement.Center,
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = if (isObj) Color.White else AppColor.TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Scan Object",
                    color = if (isObj) Color.White else AppColor.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
              }
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = if (!isObj) AppColor.Lime else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                      if (ui.hasSession || ui.isSessionActive || ui.isStreaming) cameraViewModel.onScanBillClicked(fromGlasses = true)
                      else safeRescanBillPhoto()
                    },
            ) {
              Row(
                  modifier = Modifier.padding(vertical = 8.dp),
                  horizontalArrangement = Arrangement.Center,
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.Default.Receipt, contentDescription = null, tint = if (!isObj) AppColor.TextInverted else AppColor.TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Scan Bill",
                    color = if (!isObj) AppColor.TextInverted else AppColor.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }
        }

        // Main Body: Loading vs Results
        if (isLoading) {
          Column(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 32.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            CircularProgressIndicator(
                color = if (activeTab == "object") AppColor.Coral else AppColor.Lime,
                strokeWidth = 3.dp,
                modifier = Modifier.size(42.dp),
            )
            Text(
                text = ui.geminiLoadingPrompt.ifBlank { "Processing frame with Google Gemini 2.0 Flash..." },
                color = AppColor.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Multimodal inference • Analyzing brand, price & merchant",
                color = AppColor.TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
          }
        } else if (activeTab == "object" && objResult != null) {
          // Object Result Card
          Surface(
              shape = RoundedCornerShape(16.dp),
              color = Color(0xFF1E1E1E),
              border = BorderStroke(1.dp, AppColor.Coral.copy(alpha = 0.35f)),
              modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AppColor.Coral.copy(alpha = 0.2f),
                ) {
                  Text(
                      text = "${objResult.brand} • ${objResult.category}",
                      color = AppColor.Coral,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AppColor.Lime.copy(alpha = 0.15f),
                ) {
                  Text(
                      text = "PayPal Vault Ready",
                      color = AppColor.Lime,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
              }

              Text(
                  text = objResult.productName,
                  fontSize = 17.sp,
                  fontWeight = FontWeight.Bold,
                  color = AppColor.TextPrimary,
              )

              Text(
                  text = objResult.shortDescription,
                  fontSize = 12.sp,
                  color = AppColor.TextSecondary,
                  lineHeight = 16.sp,
              )

              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.Bottom,
              ) {
                Column {
                  Text("Best Price", color = AppColor.TextSecondary, fontSize = 11.sp)
                  Text(
                      text = "$${"%.2f".format(objResult.estimatedPrice)}",
                      fontSize = 24.sp,
                      fontWeight = FontWeight.Bold,
                      color = AppColor.TextPrimary,
                  )
                }
                Text(
                    text = "at ${objResult.merchantName}",
                    color = AppColor.Sky,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
              }
            }
          }

          // 1-Tap Purchase Button
          Button(
              onClick = { cameraViewModel.executeGeminiObjectPurchase() },
              modifier = Modifier.fillMaxWidth().height(48.dp),
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = AppColor.Coral),
          ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
              Text(
                  text = "1-Tap Buy ($${"%.2f".format(objResult.estimatedPrice)}) with PayPal",
                  color = Color.White,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
              )
            }
          }

          // Secondary Actions: Open Browser & Rescan
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
                onClick = {
                  try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(objResult.shoppingUrl))
                    context.startActivity(intent)
                  } catch (e: Exception) {
                    Log.e("GeminiDialog", "Cannot open URL: ${e.message}")
                  }
                },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color(0xFF383838)),
            ) {
              Text("Merchant Page", color = AppColor.TextPrimary, fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = {
                  if (ui.hasSession || ui.isSessionActive || ui.isStreaming) cameraViewModel.onScanObjectClicked(fromGlasses = true)
                  else safeRescanObjectPhoto()
                },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color(0xFF383838)),
            ) {
              Text("Scan Again", color = AppColor.TextPrimary, fontSize = 11.sp)
            }
          }
        } else if (activeTab == "bill" && billResult != null) {
          // Bill Result Card
          Surface(
              shape = RoundedCornerShape(16.dp),
              color = Color(0xFF1E1E1E),
              border = BorderStroke(1.dp, AppColor.Lime.copy(alpha = 0.35f)),
              modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                    text = billResult.merchantName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.TextPrimary,
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AppColor.Sky.copy(alpha = 0.2f),
                ) {
                  Text(
                      text = "Invoicing v2",
                      color = AppColor.Sky,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
              }

              // Itemized list
              Column(
                  modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .background(Color(0xFF161616))
                      .padding(10.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                billResult.items.forEach { item ->
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(
                        text = "${item.quantity}x ${item.name}",
                        color = AppColor.TextPrimary,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "$${"%.2f".format(item.unitAmount * item.quantity)}",
                        color = AppColor.TextSecondary,
                        fontSize = 11.sp,
                    )
                  }
                }
              }

              // Totals
              Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Subtotal", color = AppColor.TextSecondary, fontSize = 11.sp)
                  Text("$${"%.2f".format(billResult.subtotal)}", color = AppColor.TextSecondary, fontSize = 11.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Tax & Tip", color = AppColor.TextSecondary, fontSize = 11.sp)
                  Text("$${"%.2f".format(billResult.taxTotal + billResult.tip)}", color = AppColor.TextSecondary, fontSize = 11.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Grand Total", color = AppColor.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                  Text("$${"%.2f".format(billResult.grandTotal)}", color = AppColor.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Suggested Split (2 people)", color = AppColor.Lime, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                  Text("$${"%.2f".format(billResult.suggestedSplitPerPerson)} each", color = AppColor.Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          // Split Bill Button
          Button(
              onClick = { cameraViewModel.executeGeminiBillSplit() },
              modifier = Modifier.fillMaxWidth().height(48.dp),
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = AppColor.Lime),
          ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(Icons.Default.Send, contentDescription = null, tint = AppColor.TextInverted, modifier = Modifier.size(18.dp))
              Text(
                  text = "Split ($${"%.2f".format(billResult.grandTotal)}) via PayPal Invoices",
                  color = AppColor.TextInverted,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
              )
            }
          }

          // Secondary Rescan
          OutlinedButton(
              onClick = {
                if (ui.hasSession || ui.isSessionActive || ui.isStreaming) cameraViewModel.onScanBillClicked(fromGlasses = true)
                else safeRescanBillPhoto()
              },
              modifier = Modifier.fillMaxWidth().height(40.dp),
              shape = RoundedCornerShape(50),
              border = BorderStroke(1.dp, Color(0xFF383838)),
          ) {
            Text("Scan Another Bill", color = AppColor.TextPrimary, fontSize = 11.sp)
          }
        }
      }
    }
  }
}

@Composable
fun GeminiApiKeyDialog(
    cameraViewModel: CameraViewModel,
    onDismiss: () -> Unit,
) {
  var keyText by remember { mutableStateOf(cameraViewModel.geminiFlashService.getApiKey()) }

  AlertDialog(
      onDismissRequest = onDismiss,
      containerColor = AppColor.BgSurface,
      title = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(Icons.Default.Key, contentDescription = null, tint = AppColor.Coral, modifier = Modifier.size(20.dp))
          Text("Google Gemini 2.0 Flash API Key", color = AppColor.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
              text = "Status: Pre-configured & Active (Google Gemini Flash). The API key is securely embedded app-side so neither you nor judges need to enter credentials or select services.",
              color = AppColor.Lime,
              fontSize = 12.sp,
              lineHeight = 16.sp,
          )
          OutlinedTextField(
              value = keyText,
              onValueChange = { keyText = it },
              placeholder = { Text("Pre-configured on app-side (Active)", color = AppColor.TextMuted, fontSize = 11.sp) },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = AppColor.Lime,
                  unfocusedBorderColor = Color(0xFF383838),
                  focusedTextColor = AppColor.TextPrimary,
                  unfocusedTextColor = AppColor.TextPrimary,
              ),
          )
          Text(
              text = "Live multimodal processing ready for Scan Object & Scan Bill on both glasses and mobile.",
              color = AppColor.TextSecondary,
              fontSize = 11.sp,
          )
        }
      },
      confirmButton = {
        Button(
            onClick = {
              cameraViewModel.geminiFlashService.setApiKey(keyText)
              onDismiss()
            },
            colors = ButtonDefaults.buttonColors(containerColor = AppColor.Coral),
            shape = RoundedCornerShape(50),
        ) {
          Text("Save Key", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) {
          Text("Cancel", color = AppColor.TextSecondary, fontSize = 12.sp)
        }
      },
  )
}
