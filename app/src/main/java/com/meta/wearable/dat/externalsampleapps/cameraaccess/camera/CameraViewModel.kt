/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// CameraViewModel - DAT camera lifecycle, capture, and recording
//
// Drives the camera screen by exercising the SDK's camera lifecycle as explicit steps: create and
// start a DeviceSession, add and start a Stream, capture a photo or record video, stop the stream,
// end the session. Owns the screen-scoped pieces — the DeviceSession, its Stream, the on-device
// HEVC preview decoder, and the passthrough video recorder. The UI binds to the SDK's own
// DeviceSessionState / StreamState so the sample shows the real state machine.

package com.meta.wearable.dat.externalsampleapps.cameraaccess.camera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.view.Surface
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicBoolean
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.Stream
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoFrame
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.DeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.core.types.DeviceType
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.removeDisplay
import com.meta.wearable.dat.display.types.DisplayConfiguration
import com.meta.wearable.dat.display.types.DisplayError
import com.meta.wearable.dat.display.types.DisplayState
import com.meta.wearable.dat.display.views.Alignment
import com.meta.wearable.dat.display.views.ButtonStyle
import com.meta.wearable.dat.display.views.CornerRadius
import com.meta.wearable.dat.display.views.Direction
import com.meta.wearable.dat.display.views.FlexBoxBackground
import com.meta.wearable.dat.display.views.IconName
import com.meta.wearable.dat.display.views.ImageSize
import com.meta.wearable.dat.display.views.TextColor
import com.meta.wearable.dat.display.views.TextStyle
import com.meta.wearable.dat.speech.Speech
import com.meta.wearable.dat.speech.SpeechConfiguration
import com.meta.wearable.dat.speech.addSpeech
import com.meta.wearable.dat.speech.types.SpeechError
import com.meta.wearable.dat.speech.types.SpeechState
import com.meta.wearable.dat.speech.types.TranscriptionResult
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.PaymentDetails
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.PaymentState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.QrCodeAnalyzer
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.SpeechConfirmationListener
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.VoicePrompter
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PaymentMethod
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PayPalVaultAccount
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PayPalVaultRepository
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PayPalPaymentService
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PayPalInvoiceService
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PayPalPayoutService
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PayPalVaultStatus
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.BillSplitResult
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PayPalPayoutBatch
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PayPalInvoice
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PayPalOrderItem
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.paypal.PayPalItemizedBreakdown
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.AudioInputHandler
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.HevcDecoder
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.HevcParameterSetCollector
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.RecordingResult
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamingService
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.VideoRecorder
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.ai.CategorySpend
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.ai.DaySpend
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.TransactionRepository
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.WalletTransaction
import com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.ai.FinancialAiEngine
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FriendContact(
    val id: String,
    val name: String,
    val handle: String,
    val rail: String, // "PayPal" or "UPI"
)

class CameraViewModel(
    application: Application,
    private val wearablesViewModel: WearablesViewModel,
) : AndroidViewModel(application) {

  val defaultFriends = listOf(
      FriendContact("c1", "Alex Rivera", "alex@okaxis", "UPI"),
      FriendContact("c2", "Maya Lin", "paypal.me/mayalin", "PayPal"),
      FriendContact("c3", "Devon K.", "devon@ybl", "UPI"),
      FriendContact("c4", "Sara V.", "paypal.me/sarav", "PayPal"),
  )

  companion object {
    private const val TAG = "CameraAccess:CameraViewModel"
    private const val FRAME_RATE = 15
    private const val KEYFRAME_WAIT_STEP_MS = 25L
    private const val KEYFRAME_WAIT_MAX_MS = 500L
  }

  private val deviceSelector: DeviceSelector = wearablesViewModel.deviceSelector

  private val _uiState = MutableStateFlow(CameraUiState())
  val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

  private var session: DeviceSession? = null
  private var camera: Camera? = null
  private var stream: Stream? = null

  // Recording pieces. The single compressed-HEVC stream feeds both the on-screen decoder and the
  // passthrough MP4 writer.
  private val audioInputHandler = AudioInputHandler(application)
  private val videoRecorder = VideoRecorder(application, viewModelScope)

  // Per-frame work (byte copy, NAL parsing, MediaMuxer writes, decoder feed) runs at frame rate and
  // must stay off the main thread. A single-threaded dispatcher keeps frames serialized so the
  // MediaMuxer/MediaCodec see in-order calls from one consistent thread.
  private val frameDispatcher = Dispatchers.Default.limitedParallelism(1)

  // Guards decoder create/teardown so a frame can't bind a new decoder to a Surface that
  // setSurface(null) just released — the @Volatile refs alone can't fix that check-then-act.
  private val decoderLock = Any()

  // @Volatile: single refs shared by the frame-collector and main threads, nulled at teardown.
  @Volatile private var hevcDecoder: HevcDecoder? = null
  @Volatile private var decoderSurface: Surface? = null

  // Accumulates the stream's HEVC parameter sets (VPS/SPS/PPS) so a recording (or decoder) started
  // mid-stream can be primed with a complete format. The SDK emits the VPS once at stream start, so
  // a partial set yields an unfinalizable file ("Missing codec specific data"). Internally
  // synchronized.
  private val csdCollector = HevcParameterSetCollector()

  private var sessionStateJob: Job? = null
  private var sessionDeviceInfoJob: Job? = null
  private var sessionErrorJob: Job? = null
  private var videoJob: Job? = null
  private var streamStateJob: Job? = null
  private var streamErrorJob: Job? = null
  @Volatile private var speech: Speech? = null
  private var speechStateJob: Job? = null
  private var speechErrorJob: Job? = null
  private var speechTranscriptionsJob: Job? = null

  val voicePrompter = VoicePrompter(application)

  /** Red square alignment reticle bitmap displayed on the smart glasses HUD (parallax-compensated for left-temple camera) */
  val redSquareReticleBitmap: Bitmap by lazy {
    val boxWidth = 188f
    val boxHeight = 188f
    val bitmapWidth = 540
    val bitmapHeight = 200
    // Parallax compensation between left camera and right waveguide display:
    // Display width is 600px. Bitmap is 540px wide (centered in FlexBox column => left margin 30px).
    // Placing the box at left = 16f positions the square at x = 30 + 16 = 46px on the display,
    // with its center at x = 140px (160px leftward shift from screen center 300px).
    // This perfectly matches the optical baseline disparity (~13-14° angle) at 1 to 2 feet (30-60 cm) distance.
    val left = 16f
    val top = 6f
    val right = left + boxWidth
    val bottom = top + boxHeight

    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val redPaint = android.graphics.Paint().apply {
      color = android.graphics.Color.parseColor("#FF3B30")
      style = android.graphics.Paint.Style.STROKE
      strokeWidth = 7f
      isAntiAlias = true
    }
    val rect = android.graphics.RectF(left, top, right, bottom)
    canvas.drawRoundRect(rect, 16f, 16f, redPaint)

    // Corner bracket accents in solid red
    val cornerPaint = android.graphics.Paint().apply {
      color = android.graphics.Color.parseColor("#FF3B30")
      style = android.graphics.Paint.Style.STROKE
      strokeWidth = 14f
      strokeCap = android.graphics.Paint.Cap.ROUND
      isAntiAlias = true
    }
    val cornerLen = 30f
    // Top-left
    canvas.drawLine(left + 4f, top + 4f, left + 4f + cornerLen, top + 4f, cornerPaint)
    canvas.drawLine(left + 4f, top + 4f, left + 4f + cornerLen, top + 4f, cornerPaint)
    // Top-right
    canvas.drawLine(right - 4f, top + 4f, right - 4f - cornerLen, top + 4f, cornerPaint)
    canvas.drawLine(right - 4f, top + 4f, right - 4f, top + 4f + cornerLen, cornerPaint)
    // Bottom-left
    canvas.drawLine(left + 4f, bottom - 4f, left + 4f + cornerLen, bottom - 4f, cornerPaint)
    canvas.drawLine(left + 4f, bottom - 4f, left + 4f, bottom - 4f - cornerLen, cornerPaint)
    // Bottom-right
    canvas.drawLine(right - 4f, bottom - 4f, right - 4f - cornerLen, bottom - 4f, cornerPaint)
    canvas.drawLine(right - 4f, bottom - 4f, right - 4f, bottom - 4f - cornerLen, cornerPaint)

    bitmap
  }

  fun toggleGlassesAnalyticsMode() {
    _uiState.update {
      val next = if (it.glassesAnalyticsMode == GlassesAnalyticsMode.PIE_CHART) {
        GlassesAnalyticsMode.LINE_GRAPH
      } else {
        GlassesAnalyticsMode.PIE_CHART
      }
      it.copy(glassesAnalyticsMode = next)
    }
    renderGlassesScreen()
  }

  fun generateGlassesPieChartBitmap(categories: List<CategorySpend>, totalSpent: Double): Bitmap {
    val width = 540
    val height = 180
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Center coordinates for pie on the left side
    val cx = 95f
    val cy = 90f
    val radius = 72f
    val strokeWidth = 24f

    val paint = android.graphics.Paint().apply {
      style = android.graphics.Paint.Style.STROKE
      this.strokeWidth = strokeWidth
      strokeCap = android.graphics.Paint.Cap.ROUND
      isAntiAlias = true
    }

    val textPaint = android.graphics.Paint().apply {
      color = android.graphics.Color.WHITE
      textSize = 20f
      isFakeBoldText = true
      isAntiAlias = true
      textAlign = android.graphics.Paint.Align.CENTER
    }

    val subTextPaint = android.graphics.Paint().apply {
      color = android.graphics.Color.parseColor("#8E8E93")
      textSize = 12f
      isAntiAlias = true
      textAlign = android.graphics.Paint.Align.CENTER
    }

    val oval = android.graphics.RectF(cx - radius + strokeWidth / 2f, cy - radius + strokeWidth / 2f, cx + radius - strokeWidth / 2f, cy + radius - strokeWidth / 2f)

    if (categories.isEmpty() || categories.all { it.amount <= 0.0 }) {
      paint.color = android.graphics.Color.parseColor("#3A3A3C")
      canvas.drawCircle(cx, cy, radius - strokeWidth / 2f, paint)
      canvas.drawText("$0", cx, cy + 7f, textPaint)
    } else {
      val total = categories.sumOf { it.amount }
      val gap = if (categories.size > 1) 4f else 0f
      val available = 360f - (gap * categories.size)
      var start = -90f

      categories.forEach { cat ->
        val sweep = ((cat.amount / total) * available).toFloat().coerceAtLeast(3f)
        paint.color = android.graphics.Color.parseColor(when (cat.category) {
          "Food & Dining" -> "#FF7043"
          "Friends & Transfer" -> "#42A5F5"
          "Utilities & Bills" -> "#66BB6A"
          "Shopping" -> "#AB47BC"
          "Travel" -> "#26A69A"
          else -> "#78909C"
        })
        canvas.drawArc(oval, start, sweep, false, paint)
        start += sweep + gap
      }

      // Center text in donut
      canvas.drawText("$${totalSpent.toInt()}", cx, cy + 5f, textPaint)
      canvas.drawText("total", cx, cy + 20f, subTextPaint)
    }

    // Legend on the right side
    val legendLeft = 205f
    var legendY = 38f
    val legendPaint = android.graphics.Paint().apply {
      color = android.graphics.Color.WHITE
      textSize = 15f
      isFakeBoldText = true
      isAntiAlias = true
      textAlign = android.graphics.Paint.Align.LEFT
    }
    val legendSubPaint = android.graphics.Paint().apply {
      color = android.graphics.Color.parseColor("#8E8E93")
      textSize = 14f
      isAntiAlias = true
      textAlign = android.graphics.Paint.Align.RIGHT
    }
    val dotPaint = android.graphics.Paint().apply {
      style = android.graphics.Paint.Style.FILL
      isAntiAlias = true
    }

    categories.take(4).forEach { cat ->
      dotPaint.color = android.graphics.Color.parseColor(when (cat.category) {
        "Food & Dining" -> "#FF7043"
        "Friends & Transfer" -> "#42A5F5"
        "Utilities & Bills" -> "#66BB6A"
        "Shopping" -> "#AB47BC"
        "Travel" -> "#26A69A"
        else -> "#78909C"
      })
      canvas.drawCircle(legendLeft, legendY - 5f, 6f, dotPaint)
      canvas.drawText(cat.category, legendLeft + 14f, legendY, legendPaint)
      canvas.drawText("$${cat.amount.toInt()} (${cat.percentage}%)", width - 20f, legendY, legendSubPaint)
      legendY += 34f
    }

    return bitmap
  }

  fun generateGlassesLineGraphBitmap(weeklyTrend: List<DaySpend>, weeklySpent: Double): Bitmap {
    val width = 540
    val height = 180
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val paddingLeft = 35f
    val paddingRight = 35f
    val paddingTop = 40f
    val paddingBottom = 40f
    val chartW = width - paddingLeft - paddingRight
    val chartH = height - paddingTop - paddingBottom
    val baselineY = paddingTop + chartH

    // Title at the top
    val titlePaint = android.graphics.Paint().apply {
      color = android.graphics.Color.parseColor("#C6F135")
      textSize = 17f
      isFakeBoldText = true
      isAntiAlias = true
      textAlign = android.graphics.Paint.Align.LEFT
    }
    canvas.drawText("Past 7 Days: $${"%.2f".format(weeklySpent)}", paddingLeft, 24f, titlePaint)

    // Baseline
    val gridPaint = android.graphics.Paint().apply {
      color = android.graphics.Color.parseColor("#3A3A3C")
      strokeWidth = 2f
      style = android.graphics.Paint.Style.STROKE
    }
    canvas.drawLine(paddingLeft, baselineY, width - paddingRight, baselineY, gridPaint)

    if (weeklyTrend.isEmpty()) return bitmap

    val maxAmount = (weeklyTrend.maxOfOrNull { it.amount } ?: 10.0).coerceAtLeast(10.0)
    val stepX = chartW / (weeklyTrend.size - 1).coerceAtLeast(1)

    val points = weeklyTrend.mapIndexed { i, day ->
      val x = paddingLeft + (i * stepX)
      val ratio = (day.amount / maxAmount).toFloat().coerceIn(0f, 1f)
      val y = baselineY - (ratio * chartH)
      android.graphics.PointF(x, y)
    }

    // Path
    val linePath = android.graphics.Path()
    val fillPath = android.graphics.Path()

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

    // Fill
    val fillPaint = android.graphics.Paint().apply {
      style = android.graphics.Paint.Style.FILL
      color = android.graphics.Color.parseColor("#33C6F135") // 20% alpha lime
      isAntiAlias = true
    }
    canvas.drawPath(fillPath, fillPaint)

    // Line
    val linePaint = android.graphics.Paint().apply {
      style = android.graphics.Paint.Style.STROKE
      strokeWidth = 4.5f
      strokeCap = android.graphics.Paint.Cap.ROUND
      strokeJoin = android.graphics.Paint.Join.ROUND
      color = android.graphics.Color.parseColor("#C6F135") // Lime
      isAntiAlias = true
    }
    canvas.drawPath(linePath, linePaint)

    // Dots & Labels
    val dotPaint = android.graphics.Paint().apply {
      style = android.graphics.Paint.Style.FILL
      isAntiAlias = true
    }
    val dayLabelPaint = android.graphics.Paint().apply {
      color = android.graphics.Color.parseColor("#8E8E93")
      textSize = 13f
      isAntiAlias = true
      textAlign = android.graphics.Paint.Align.CENTER
    }
    val amtLabelPaint = android.graphics.Paint().apply {
      color = android.graphics.Color.WHITE
      textSize = 12f
      isFakeBoldText = true
      isAntiAlias = true
      textAlign = android.graphics.Paint.Align.CENTER
    }

    points.forEachIndexed { i, pt ->
      val day = weeklyTrend[i]
      if (day.isToday) {
        dotPaint.color = android.graphics.Color.parseColor("#42A5F5") // Sky Blue for today
        canvas.drawCircle(pt.x, pt.y, 7f, dotPaint)
      } else if (day.amount > 0) {
        dotPaint.color = android.graphics.Color.parseColor("#C6F135") // Lime
        canvas.drawCircle(pt.x, pt.y, 5f, dotPaint)
      } else {
        dotPaint.color = android.graphics.Color.parseColor("#3A3A3C")
        canvas.drawCircle(pt.x, pt.y, 3f, dotPaint)
      }

      // Day label
      dayLabelPaint.color = if (day.isToday) android.graphics.Color.parseColor("#42A5F5") else android.graphics.Color.parseColor("#8E8E93")
      canvas.drawText(day.dayName, pt.x, height - 12f, dayLabelPaint)

      // Value label if amount > 0
      if (day.amount > 0) {
        canvas.drawText("$${day.amount.toInt()}", pt.x, pt.y - 10f, amtLabelPaint)
      }
    }

    return bitmap
  }

  private var speechListener: SpeechConfirmationListener? = null
  private var qrScanningJob: Job? = null
  private val isScanningQrFrame = AtomicBoolean(false)
  private var userStoppedSession = false
  private var userStoppedStream = false

  // PayPal Commerce & Developer Infrastructure
  val payPalVaultRepository = PayPalVaultRepository(application)
  val payPalPaymentService = PayPalPaymentService(payPalVaultRepository)
  val payPalInvoiceService = PayPalInvoiceService(application)
  val payPalPayoutService = PayPalPayoutService(application)

  // On-Device Transaction History Persistence
  val transactionRepository = TransactionRepository(application)

  // LiteRT (TensorFlow Lite) On-Device AI Financial Intelligence Engine
  val financialAiEngine = FinancialAiEngine(application)

  // Meta Muse Spark Multimodal Visual AI Engine (Cloud / Edge Hybrid)
  val museSparkService = com.meta.wearable.dat.externalsampleapps.cameraaccess.payment.ai.MuseSparkService(application)

  // Display capability for payment lifecycle
  private var display: Display? = null
  private var displayStateJob: Job? = null
  private var autoDismissJob: Job? = null
  private var keypadAmountString: String = ""
  private val displayRenderMutex = Mutex()
  @Volatile private var isMicPermissionGranted = false

  init {
    FastQrScanner.prewarm()
    videoRecorder.setAudioInputHandler(audioInputHandler)

    // Observe PayPal Vault status
    viewModelScope.launch {
      payPalVaultRepository.vaultStatus.collect { status ->
        _uiState.update { it.copy(paypalVaultStatus = status) }
      }
    }

    // Mirror the recorder's intent/elapsed into UI state.
    viewModelScope.launch {
      videoRecorder.isRecording.collect { recording ->
        _uiState.update { it.copy(isRecording = recording) }
      }
    }
    viewModelScope.launch {
      videoRecorder.recordingElapsedSeconds.collect { seconds ->
        _uiState.update { it.copy(recordingElapsedSeconds = seconds) }
      }
    }
    // Stop a recording gracefully if the mic is interrupted (e.g. a phone call).
    viewModelScope.launch {
      audioInputHandler.wasInterrupted.collect { interrupted ->
        if (interrupted && _uiState.value.isRecording) {
          Log.w(TAG, "Audio interrupted — stopping recording")
          stopVideoRecording()
        }
      }
    }


    // Track wearable availability and auto-reconnect session when glasses reconnect via Bluetooth
    viewModelScope.launch {
      wearablesViewModel.uiState.collect { wearablesState ->
        val hasDevice = wearablesState.hasActiveDevice && wearablesState.isRegistered
        if (hasDevice && !userStoppedSession && !_uiState.value.hasSession && !_uiState.value.isBusy) {
          Log.i(TAG, "Active device reconnected via Bluetooth! Automatically resuming glasses display session...")
          startGlassesDisplayHome()
        }
      }
    }

    // Manage QR scanning loop and enforce stream shutdown when not in SCAN_QR mode
    viewModelScope.launch {
      _uiState.collect { camState ->
        if (camState.glassesDisplayScreen != GlassesDisplayScreen.SCAN_QR) {
          if (camState.isStreaming && camState.streamState != StreamState.STOPPING && camState.streamState != StreamState.STOPPED) {
            stopStreaming()
          }
          stopQrScanningLoop()
        } else if (camState.isStreaming && camState.paymentState is PaymentState.Idle && camState.isAutoScanningQr) {
          startQrScanningLoop()
        } else if (!camState.isStreaming || camState.paymentState !is PaymentState.Idle) {
          stopQrScanningLoop()
        }
      }
    }
  }

  // MARK: - Surface

  fun setSurface(surface: Surface?) {
    synchronized(decoderLock) {
      decoderSurface = surface
      if (surface == null) {
        hevcDecoder?.stop()
        hevcDecoder = null
      }
    }
  }

  // MARK: - Lifecycle step 1: session & Display flow

  /**
   * Called when app enters foreground or on resume.
   * Clears any stale session flags and ensures the smart glasses HUD is actively connected and displayed.
   */
  fun onAppResumed() {
    Log.i(TAG, "onAppResumed: Foregrounded - resetting stop flags and ensuring glasses display session...")
    userStoppedSession = false
    userStoppedStream = false
    val wearablesState = wearablesViewModel.uiState.value
    val hasDevice = wearablesState.hasActiveDevice && wearablesState.isRegistered
    if (hasDevice) {
      if (!_uiState.value.hasSession && !_uiState.value.isBusy) {
        Log.i(TAG, "onAppResumed: Active device connected, starting glasses display session")
        startGlassesDisplayHome()
      } else if (_uiState.value.isSessionActive) {
        val s = session
        if (s != null && isDisplayCapableDevice(s)) {
          if (display == null || _uiState.value.displayState != DisplayState.STARTED) {
            Log.i(TAG, "onAppResumed: Session active but display not running, attaching display capability")
            attachDisplayCapability(s)
          } else {
            renderGlassesScreen()
          }
        }
      }
    }
  }

  /**
   * Primary entry point when app starts or when returning to Home:
   * Starts device session if not running and attaches Display capability to show the 3-button Home screen.
   */
  fun startGlassesDisplayHome() {
    userStoppedSession = false
    userStoppedStream = false
    _uiState.update { it.copy(glassesDisplayScreen = GlassesDisplayScreen.HOME) }
    if (!_uiState.value.hasSession) {
      Log.i(TAG, "startGlassesDisplayHome: Starting device session for Display Home")
      startSession()
    } else if (_uiState.value.isSessionActive) {
      val s = session
      if (s != null) {
        if (isDisplayCapableDevice(s)) {
          if (display == null) {
            attachDisplayCapability(s)
          } else {
            renderGlassesScreen()
          }
        } else {
          Log.i(TAG, "startGlassesDisplayHome: Non-display device connected, activating voice-only home mode")
        }
      }
      startSpeechListening()
    }
  }

  /**
   * Triggered when user selects 'Scan QR' from glasses display or app.
   * Starts camera stream to scan QR codes with a live HUD and Return to Home option.
   */
  fun onScanQrClicked() {
    Log.i(TAG, "onScanQrClicked: Starting camera session for QR scanning")
    userStoppedStream = false
    _uiState.update {
      it.copy(
          glassesDisplayScreen = GlassesDisplayScreen.SCAN_QR,
          paymentState = PaymentState.Idle
      )
    }
    renderGlassesScreen()
    if (!_uiState.value.isSessionActive) {
      startSession()
    } else if (!_uiState.value.isStreaming && !_uiState.value.isStartingStream) {
      startStreaming()
    } else {
      startQrScanningLoop()
    }
    if (!isDisplayCapableDevice()) {
      voicePrompter.speak("Camera active. Align with your left eye—the camera is on your left frame.")
    }
  }

  /**
   * Triggered when user selects 'View Analytics' from glasses display.
   * Renders real-time LiteRT financial analytics onto the glasses display.
   */
  fun onViewAnalyticsClicked() {
    Log.i(TAG, "onViewAnalyticsClicked: Rendering on-device financial analytics on glasses")
    userStoppedStream = true
    stopQrScanningLoop()
    if (_uiState.value.isStreaming || _uiState.value.streamState != StreamState.STOPPED || camera != null) {
      stopStreaming()
    }
    _uiState.update {
      it.copy(
          glassesDisplayScreen = GlassesDisplayScreen.ANALYTICS,
          paymentState = PaymentState.Idle,
          aiQaAnswer = null
      )
    }
    renderGlassesScreen()
    startSpeechListening()
    val txns = transactionRepository.transactions.value
    val bal = transactionRepository.balance.value
    val analysis = financialAiEngine.analyzeFinances(txns, bal)
    voicePrompter.speak(analysis.aiSummary)
  }

  /**
   * Evaluates a spending or financial analytics question using on-device AI.
   * Updates glasses display and speaks the concise response via VoicePrompter.
   */
  fun askSpendingAiQuestion(question: String, speakOutput: Boolean = true) {
    Log.i(TAG, "askSpendingAiQuestion: \"$question\"")
    val txns = transactionRepository.transactions.value
    val bal = transactionRepository.balance.value
    val answer = financialAiEngine.answerSpendingQuestion(question, txns, bal)

    _uiState.update {
      it.copy(
          aiQaAnswer = answer,
          glassesDisplayScreen = GlassesDisplayScreen.ANALYTICS,
          isVoiceQaListening = false,
          paymentState = PaymentState.Idle
      )
    }
    renderGlassesScreen()
    if (speakOutput) {
      voicePrompter.speak(answer.shortAnswer)
    }
    startSpeechListening()
  }

  fun onAskVoiceQuestionClicked() {
    Log.i(TAG, "onAskVoiceQuestionClicked: Displaying voice QA placeholder screen")
    userStoppedStream = true
    stopQrScanningLoop()
    if (_uiState.value.isStreaming || _uiState.value.streamState != StreamState.STOPPED || camera != null) {
      stopStreaming()
    }
    _uiState.update {
      it.copy(
          isVoiceQaListening = true,
          glassesDisplayScreen = GlassesDisplayScreen.VOICE_QA_LISTENING,
          liveSpeechTranscription = ""
      )
    }
    renderGlassesScreen()
    voicePrompter.speak("Listening. Ask what was your highest expense, or total spent.")
    startSpeechListening(forceRestart = true)
  }

  fun dismissVoiceQaListening() {
    Log.i(TAG, "dismissVoiceQaListening: Returning to Analytics screen")
    userStoppedStream = true
    stopQrScanningLoop()
    if (_uiState.value.isStreaming || _uiState.value.streamState != StreamState.STOPPED || camera != null) {
      stopStreaming()
    }
    _uiState.update {
      it.copy(
          isVoiceQaListening = false,
          glassesDisplayScreen = GlassesDisplayScreen.ANALYTICS
      )
    }
    renderGlassesScreen()
  }

  /**
   * Triggered when user selects 'Send Money to Friend' from glasses display.
   * Renders the list of contacts on the glasses display.
   */
  fun onSendMoneyClicked() {
    Log.i(TAG, "onSendMoneyClicked: Displaying contacts list on glasses")
    userStoppedStream = true
    stopQrScanningLoop()
    if (_uiState.value.isStreaming || _uiState.value.streamState != StreamState.STOPPED || camera != null) {
      stopStreaming()
    }
    _uiState.update {
      it.copy(
          glassesDisplayScreen = GlassesDisplayScreen.CONTACTS,
          paymentState = PaymentState.Idle
      )
    }
    renderGlassesScreen()
    voicePrompter.speak("Select a friend to send money.")
  }

  /**
   * Triggered when a contact is selected on glasses display to start entering amount.
   */
  fun onSendToContactSelected(friend: FriendContact) {
    Log.i(TAG, "Contact selected: ${friend.name} (${friend.rail})")
    userStoppedStream = true
    stopQrScanningLoop()
    if (_uiState.value.isStreaming || _uiState.value.streamState != StreamState.STOPPED || camera != null) {
      stopStreaming()
    }
    keypadAmountString = ""
    val isPayPal = friend.rail.equals("PayPal", ignoreCase = true)
    val details = PaymentDetails(
        payeeName = friend.name,
        payeeVpa = friend.handle,
        amount = null,
        currency = if (isPayPal) "USD" else "INR",
        paymentMethod = if (isPayPal) PaymentMethod.PAYPAL_VAULT else PaymentMethod.UPI,
        note = "Transfer to ${friend.name}"
    )
    _uiState.update {
      it.copy(
          glassesDisplayScreen = GlassesDisplayScreen.PAYMENT,
          paymentState = PaymentState.EnterAmount(details),
          keypadAmount = ""
      )
    }
    renderGlassesScreen()
    voicePrompter.speak("Enter amount to send to ${friend.name}.")
  }

  /**
   * AI-Powered Bill Splitting with PayPal Invoicing v2.
   * Opens interactive friend selector on glasses HUD to allow custom receiver selection.
   */
  fun openGlassesBillSplitSelection(amount: Double = 0.0) {
    val finalAmount = if (amount > 0.0) amount else 0.0
    Log.i(TAG, "openGlassesBillSplitSelection: Opening friend selector for amount=$$finalAmount")
    _uiState.update {
      it.copy(
          glassesDisplayScreen = GlassesDisplayScreen.BILL_SPLIT,
          billSplitAmount = finalAmount,
          billSplitSelectedFriendIds = emptySet(),
          recentBillSplitResult = null,
      )
    }
    renderGlassesScreen()
    if (finalAmount > 0.0) {
      voicePrompter.speak("Select friends to split $${"%.2f".format(finalAmount)} with, or say their names, for example Maya, Alex, or Sara.")
    } else {
      voicePrompter.speak("Select friends to split with. Tap +$10 or say an amount to set the bill total.")
    }
    startSpeechListening(forceRestart = true)
  }

  fun adjustBillSplitAmount(delta: Double) {
    val currentAmt = _uiState.value.billSplitAmount
    val updated = maxOf(0.0, currentAmt + delta)
    Log.i(TAG, "adjustBillSplitAmount: delta=$delta, new amount=$$updated")
    _uiState.update { it.copy(billSplitAmount = updated) }
    renderGlassesScreen()
    val totalPeople = _uiState.value.billSplitSelectedFriendIds.size + 1
    val share = if (totalPeople > 0) updated / totalPeople else 0.0
    voicePrompter.speak("Amount adjusted to $${"%.2f".format(updated)}. $${"%.2f".format(share)} each.")
  }

  fun setBillSplitAmount(amount: Double) {
    if (amount <= 0.0) return
    Log.i(TAG, "setBillSplitAmount: new amount=$$amount")
    _uiState.update { it.copy(billSplitAmount = amount) }
    renderGlassesScreen()
    val totalPeople = _uiState.value.billSplitSelectedFriendIds.size + 1
    val share = amount / totalPeople
    voicePrompter.speak("Amount set to $${"%.2f".format(amount)}. $${"%.2f".format(share)} each.")
  }

  // MARK: - Meta Muse Spark Multimodal Visual AI Workflows

  /**
   * Scans a physical product in view using Meta Muse Spark multimodal visual AI.
   * Identifies product details, best merchant prices, and enables 1-tap PayPal capture.
   */
  fun onScanObjectClicked(bitmap: Bitmap? = null, fromGlasses: Boolean = true) {
    Log.i(TAG, "onScanObjectClicked: Triggering Meta Muse Spark visual product search")
    _uiState.update {
      it.copy(
          isMuseSparkLoading = true,
          museSparkLoadingPrompt = "Analyzing product with Meta Muse Spark...",
          glassesDisplayScreen = GlassesDisplayScreen.SCAN_OBJECT,
          showMuseSparkDialog = true,
          museSparkActiveTab = "object",
          museSparkObjectResult = null,
      )
    }
    renderGlassesScreen()
    voicePrompter.speak("Looking up product with Muse Spark.")

    viewModelScope.launch(Dispatchers.Default) {
      var targetBitmap: Bitmap? = bitmap
      if (targetBitmap == null && stream != null && _uiState.value.isStreaming) {
        try {
          stream?.capturePhoto()?.onSuccess { photoData ->
            targetBitmap = decodePhoto(photoData)
          }
        } catch (e: Exception) {
          Log.w(TAG, "Could not capture glasses photo for Muse Spark: ${e.message}")
        }
      }

      val result = museSparkService.identifyObject(targetBitmap)
      withContext(Dispatchers.Main) {
        result.onSuccess { obj ->
          Log.i(TAG, "Muse Spark found: ${obj.productName} for $${obj.estimatedPrice}")
          _uiState.update {
            it.copy(
                isMuseSparkLoading = false,
                museSparkObjectResult = obj,
            )
          }
          renderGlassesScreen()
          voicePrompter.speak("Found ${obj.productName} for $${obj.estimatedPrice.toInt()} dollars at ${obj.merchantName}. Tap to buy with PayPal.")
        }.onFailure { err ->
          Log.e(TAG, "Muse Spark object identification error: ${err.message}")
          _uiState.update { it.copy(isMuseSparkLoading = false) }
          renderGlassesScreen()
        }
      }
    }
  }

  /**
   * Scans a physical paper restaurant bill or store receipt using Meta Muse Spark.
   * Extracts itemized breakdown, tax, tip, and prepares PayPal Invoicing v2 split.
   */
  fun onScanBillClicked(bitmap: Bitmap? = null, fromGlasses: Boolean = true) {
    Log.i(TAG, "onScanBillClicked: Triggering Meta Muse Spark receipt parsing")
    _uiState.update {
      it.copy(
          isMuseSparkLoading = true,
          museSparkLoadingPrompt = "Parsing paper bill with Meta Muse Spark...",
          glassesDisplayScreen = GlassesDisplayScreen.SCAN_BILL,
          showMuseSparkDialog = true,
          museSparkActiveTab = "bill",
          museSparkBillResult = null,
      )
    }
    renderGlassesScreen()
    voicePrompter.speak("Scanning bill with Muse Spark.")

    viewModelScope.launch(Dispatchers.Default) {
      var targetBitmap: Bitmap? = bitmap
      if (targetBitmap == null && stream != null && _uiState.value.isStreaming) {
        try {
          stream?.capturePhoto()?.onSuccess { photoData ->
            targetBitmap = decodePhoto(photoData)
          }
        } catch (e: Exception) {
          Log.w(TAG, "Could not capture glasses photo for Muse Spark bill: ${e.message}")
        }
      }

      val result = museSparkService.parseBill(targetBitmap)
      withContext(Dispatchers.Main) {
        result.onSuccess { bill ->
          Log.i(TAG, "Muse Spark parsed bill: ${bill.merchantName} (Total: $${bill.grandTotal})")
          _uiState.update {
            it.copy(
                isMuseSparkLoading = false,
                museSparkBillResult = bill,
            )
          }
          renderGlassesScreen()
          voicePrompter.speak("Parsed bill from ${bill.merchantName}. Total is $${bill.grandTotal.toInt()} dollars. Tap to split with friends.")
        }.onFailure { err ->
          Log.e(TAG, "Muse Spark bill parsing error: ${err.message}")
          _uiState.update { it.copy(isMuseSparkLoading = false) }
          renderGlassesScreen()
        }
      }
    }
  }

  fun dismissMuseSparkDialog() {
    _uiState.update { it.copy(showMuseSparkDialog = false) }
  }

  fun executeMuseSparkObjectPurchase() {
    val objResult = _uiState.value.museSparkObjectResult ?: return
    viewModelScope.launch(Dispatchers.Main) {
      val amount = objResult.estimatedPrice
      val payee = objResult.merchantName
      val note = "Purchased ${objResult.productName} via Meta Muse Spark"

      val result = payPalPaymentService.executePayment(
          payee = payee,
          amount = amount,
          currency = objResult.currency,
          note = note,
      )

      result.onSuccess { orderResult ->
        val tx = WalletTransaction(
            id = orderResult.transactionId,
            merchant = payee,
            rail = "PayPal",
            amount = amount,
            timestamp = System.currentTimeMillis(),
            status = "SUCCESS",
            fundingSource = "PayPal Vault",
            note = note,
            category = objResult.category,
            referenceId = orderResult.transactionId,
        )
        transactionRepository.addTransaction(tx)
        voicePrompter.speak("Paid $${"%.2f".format(amount)} to $payee with PayPal Vault.")
        _uiState.update { it.copy(showMuseSparkDialog = false) }
        returnToGlassesHome()
      }.onFailure { err ->
        Log.e(TAG, "Muse Spark purchase error: ${err.message}")
      }
    }
  }

  fun executeMuseSparkBillSplit() {
    val bill = _uiState.value.museSparkBillResult ?: return
    _uiState.update { it.copy(showMuseSparkDialog = false) }
    openGlassesBillSplitSelection(bill.grandTotal)
  }

  fun openBillSplitKeypad() {
    val currentAmt = _uiState.value.billSplitAmount
    keypadAmountString = if (currentAmt > 0.0) {
      if (currentAmt % 1.0 == 0.0) currentAmt.toInt().toString() else "%.2f".format(currentAmt)
    } else {
      ""
    }
    _uiState.update { it.copy(glassesDisplayScreen = GlassesDisplayScreen.BILL_SPLIT_KEYPAD) }
    renderGlassesScreen()
    voicePrompter.speak("Enter total bill amount on keypad, or say the amount aloud.")
  }

  fun submitBillSplitKeypad() {
    val parsed = keypadAmountString.toDoubleOrNull() ?: 0.0
    _uiState.update {
      it.copy(
          billSplitAmount = parsed,
          glassesDisplayScreen = GlassesDisplayScreen.BILL_SPLIT,
      )
    }
    renderGlassesScreen()
    if (parsed > 0.0) {
      val totalPeople = _uiState.value.billSplitSelectedFriendIds.size + 1
      val perPerson = parsed / totalPeople
      voicePrompter.speak("Bill total set to $${"%.2f".format(parsed)}. $${"%.2f".format(perPerson)} each.")
    }
  }

  fun cancelBillSplitKeypad() {
    _uiState.update { it.copy(glassesDisplayScreen = GlassesDisplayScreen.BILL_SPLIT) }
    renderGlassesScreen()
  }

  fun toggleBillSplitFriend(friendId: String) {
    val currentSet = _uiState.value.billSplitSelectedFriendIds.toMutableSet()
    val friend = defaultFriends.find { it.id == friendId } ?: return
    val added: Boolean
    if (currentSet.contains(friendId)) {
      currentSet.remove(friendId)
      added = false
    } else {
      currentSet.add(friendId)
      added = true
    }
    _uiState.update { it.copy(billSplitSelectedFriendIds = currentSet) }
    renderGlassesScreen()
    val totalPeople = currentSet.size + 1
    val share = _uiState.value.billSplitAmount / totalPeople
    if (added) {
      voicePrompter.speak("Added ${friend.name}. Total $totalPeople people, $$${"%.2f".format(share)} each.")
    } else {
      voicePrompter.speak("Removed ${friend.name}. Total $totalPeople people.")
    }
  }

  fun confirmBillSplit(note: String = "Dinner & Drinks Split") {
    val selectedIds = _uiState.value.billSplitSelectedFriendIds
    val amount = _uiState.value.billSplitAmount
    if (amount <= 0.0) {
      voicePrompter.speak("Please enter or say a bill amount first.")
      return
    }
    if (selectedIds.isEmpty()) {
      voicePrompter.speak("Please select at least one friend to split with.")
      return
    }
    val friends = defaultFriends.filter { selectedIds.contains(it.id) }
    triggerBillSplit(amount, friends, note)
  }

  /**
   * AI-Powered Bill Splitting with PayPal Invoicing v2.
   * Splits a group expense with the specified friends and dispatches authentic PayPal invoices.
   */
  fun triggerBillSplit(
      totalAmount: Double = 0.0,
      friends: List<FriendContact> = emptyList(),
      note: String = "Dinner & Drinks Split"
  ) {
    val finalAmount = if (totalAmount > 0.0) totalAmount else (_uiState.value.billSplitAmount.takeIf { it > 0.0 } ?: 50.0)
    if (friends.isEmpty()) {
      openGlassesBillSplitSelection(finalAmount)
      return
    }
    Log.i(TAG, "triggerBillSplit: Amount=$$finalAmount with ${friends.map { it.name }}")
    voicePrompter.speak("Splitting $$finalAmount with ${friends.joinToString(" and ") { it.name }}. Creating PayPal invoices.")
    viewModelScope.launch {
      val splitFriends = friends.map {
        val email = if (it.handle.contains("paypal.me/")) {
          "${it.handle.replace("paypal.me/", "")}@sandbox.paypal.com"
        } else if (it.handle.contains("@")) {
          "${it.name.lowercase().replace(" ", "")}@sandbox.paypal.com"
        } else {
          "${it.handle}@sandbox.paypal.com"
        }
        it.name to email
      }
      val result = payPalInvoiceService.splitBill(finalAmount, splitFriends, note)
      result.fold(
          onSuccess = { splitResult ->
            _uiState.update {
              it.copy(
                  recentBillSplitResult = splitResult,
                  glassesDisplayScreen = GlassesDisplayScreen.BILL_SPLIT,
              )
            }
            renderGlassesScreen()
            val perPerson = "%.2f".format(splitResult.perPersonAmount)
            voicePrompter.speak("Sent PayPal invoices of $$perPerson each to ${friends.joinToString(" and ") { it.name }}.")
          },
          onFailure = { err ->
            Log.e(TAG, "Bill split failed: ${err.message}")
            voicePrompter.speak("Bill split request failed. Please try again.")
          }
      )
    }
  }

  /**
   * Instant Peer-to-Peer Transfer using PayPal Payouts v1 API.
   * Disburses funds immediately to a contact's PayPal account from the smart glasses.
   */
  fun triggerPayPalPayout(
      friend: FriendContact,
      amount: Double,
      note: String = "AirWallet Peer Payout"
  ) {
    Log.i(TAG, "triggerPayPalPayout: Disbursing $$amount to ${friend.name} via PayPal Payouts v1")
    voicePrompter.speak("Sending $$amount to ${friend.name} via PayPal Payouts.")
    viewModelScope.launch {
      val email = if (friend.handle.contains("@")) friend.handle else "${friend.handle.replace("paypal.me/", "")}@sandbox.paypal.com"
      val result = payPalPayoutService.sendPayout(email, friend.name, amount, note)
      result.fold(
          onSuccess = { batch ->
            _uiState.update {
              it.copy(recentPayoutBatch = batch)
            }
            transactionRepository.addTransaction(
                WalletTransaction(
                    id = batch.batchId,
                    merchant = friend.name,
                    rail = "PayPal",
                    amount = amount,
                    timestamp = batch.timestamp,
                    status = "SUCCESS",
                    fundingSource = "PayPal Balance Payout",
                    note = note,
                    category = "Friends & Transfer",
                    itemizedSummary = "1x Peer Payout to ${friend.name} ($${"%.2f".format(amount)})",
                    referenceId = batch.batchId,
                )
            )
            voicePrompter.speak("PayPal payout of $$amount successfully sent to ${friend.name}. Batch confirmed.")
          },
          onFailure = { err ->
            Log.e(TAG, "Payout failed: ${err.message}")
            voicePrompter.speak("PayPal payout failed. ${err.message ?: ""}")
          }
      )
    }
  }

  /**
   * Universal Return to Home handler present on every glasses display screen and at the end of every flow.
   */
  fun returnToGlassesHome() {
    Log.i(TAG, "returnToGlassesHome: Resetting to Home display screen")
    userStoppedSession = false
    userStoppedStream = true
    autoDismissJob?.cancel()
    autoDismissJob = null
    speechListener?.stopListening()
    stopQrScanningLoop()
    val wasStreaming = _uiState.value.isStreaming || _uiState.value.streamState != StreamState.STOPPED || camera != null
    if (wasStreaming) {
      stopStreaming()
    }
    keypadAmountString = ""
    _uiState.update {
      it.copy(
          glassesDisplayScreen = GlassesDisplayScreen.HOME,
          paymentState = PaymentState.Idle,
          keypadAmount = "",
          aiQaAnswer = null
      )
    }
    viewModelScope.launch {
      if (wasStreaming) {
        // Wait until stream actually transitions to STOPPED before rendering display
        var waited = 0
        while (waited < 1500 && (_uiState.value.streamState != StreamState.STOPPED || camera != null)) {
          delay(100)
          waited += 100
        }
        delay(600) // critical 600ms settling delay for Meta glasses firmware to cleanly dispose the BTC RFCOMM lease
      }
      val activeSession = session
      if (activeSession != null && _uiState.value.isSessionActive) {
        if (isDisplayCapableDevice(activeSession)) {
          if (display != null && _uiState.value.displayState == DisplayState.STARTED) {
            renderGlassesScreen()
            startSpeechListening()
          } else {
            attachDisplayCapability(activeSession)
          }
        } else {
          voicePrompter.speak("Air Wallet Home.")
          startSpeechListening()
        }
      }
    }
  }

  /**
   * Ends the glasses display session and returns glasses to their default system state.
   */
  fun exitWalletDisplaySession() {
    Log.i(TAG, "exitWalletDisplaySession: Ending display session on glasses")
    userStoppedStream = true
    userStoppedSession = true
    stopQrScanningLoop()
    if (_uiState.value.isStreaming || _uiState.value.streamState != StreamState.STOPPED || camera != null) {
      stopStreaming()
    }
    voicePrompter.speak("Exiting Air Wallet.")
    endAllSessions()
  }

  /**
   * High-level entry point to activate Ray-Ban Meta glasses camera stream for scanning.
   */
  fun startGlassesCamera() {
    onScanQrClicked()
  }

  /** Creates and starts a [DeviceSession] (no stream yet), with retry for Bluetooth discovery. */
  fun startSession(retryCount: Int = 0) {
    userStoppedSession = false
    userStoppedStream = false
    if (_uiState.value.hasSession) return
    Wearables.createSession(deviceSelector)
        .onSuccess { created ->
          Log.i(TAG, "createSession succeeded on attempt $retryCount")
          session = created
          // Subscribe before start() so no initial transitions are missed.
          observeSession(created)
          _uiState.update { it.copy(sessionState = DeviceSessionState.STARTING) }
          created.start()
        }
        .onFailure { error, _ ->
          Log.w(TAG, "Failed to start session (attempt $retryCount): ${error.description}")
          cleanupSession()
          if (!userStoppedSession && retryCount < 6) {
            viewModelScope.launch {
              kotlinx.coroutines.delay(1500)
              if (!userStoppedSession && !_uiState.value.hasSession && !_uiState.value.isBusy) {
                Log.i(TAG, "Retrying startSession... attempt ${retryCount + 1}")
                startSession(retryCount + 1)
              }
            }
          }
        }
  }

  /**
   * Ends the device session. The stream and any in-progress recording are not torn down here
   * directly — stopping the session drives the SDK's stream to a terminal state, which the
   * stream-state collector observes (see [onStreamTerminated]) to stop recording and release stream
   * resources.
   */
  fun endSession() {
    userStoppedSession = true
    val current = session ?: return
    _uiState.update { it.copy(sessionState = DeviceSessionState.STOPPING) }
    current.stop()
  }

  fun isDisplayCapableDevice(targetSession: DeviceSession? = session): Boolean {
    if (display != null) return true
    if (_uiState.value.isDisplayCapable) return true
    val s = targetSession ?: session
    if (s != null) {
      val device = s.deviceInfo.value
      if (device.deviceType == DeviceType.META_RAYBAN_DISPLAY || device.name.contains("Display", ignoreCase = true)) {
        return true
      }
      if (device.deviceType != DeviceType.UNKNOWN && device.isDisplayCapable()) {
        return true
      }
    }
    // Check devices known to WearablesViewModel
    val devices = wearablesViewModel.uiState.value.devices
    for (deviceId in devices) {
      val meta = Wearables.devicesMetadata[deviceId]?.value
      if (meta != null) {
        if (meta.deviceType == DeviceType.META_RAYBAN_DISPLAY ||
            meta.name.contains("Display", ignoreCase = true) ||
            meta.isDisplayCapable()) {
          return true
        }
      }
    }
    return false
  }

  private fun observeSession(session: DeviceSession) {
    sessionDeviceInfoJob?.cancel()
    sessionDeviceInfoJob = viewModelScope.launch {
      session.deviceInfo.collect { device ->
        val capable = device.deviceType == DeviceType.META_RAYBAN_DISPLAY || (device.deviceType != DeviceType.UNKNOWN && device.isDisplayCapable())
        Log.i(TAG, "Device info updated: name=${device.name}, type=${device.deviceType}, isDisplayCapable=$capable")
        _uiState.update { it.copy(isDisplayCapable = capable) }
        if (capable && display == null && session.state.value == DeviceSessionState.STARTED) {
          attachDisplayCapability(session)
        }
      }
    }

    sessionStateJob = viewModelScope.launch {
      session.state.collect { state ->
        _uiState.update { it.copy(sessionState = state) }
        when (state) {
          DeviceSessionState.STARTED -> {
            _uiState.update { it.copy(isDeviceInStandby = false, deviceStatusMessage = null) }
            if (isDisplayCapableDevice(session)) {
              if (_uiState.value.glassesDisplayScreen == GlassesDisplayScreen.SCAN_QR) {
                Log.i(TAG, "Device session STARTED in SCAN_QR mode - attaching display HUD and starting stream")
                attachDisplayCapability(session)
                if (stream == null && !_uiState.value.isStartingStream) {
                  startStreaming()
                }
              } else {
                Log.i(TAG, "Device session STARTED - attaching display capability for Home screen")
                attachDisplayCapability(session)
                if (_uiState.value.isStreaming || _uiState.value.streamState != StreamState.STOPPED || camera != null) {
                  stopStreaming()
                }
              }
            } else {
              attachSpeechCapability(session)
              Log.i(TAG, "Device session STARTED on non-display Ray-Ban Meta glasses (Voice/Camera mode)")
              if (_uiState.value.glassesDisplayScreen == GlassesDisplayScreen.SCAN_QR) {
                if (stream == null && !_uiState.value.isStartingStream) {
                  startStreaming()
                }
              } else {
                voicePrompter.speak("AirWallet connected. Ready to pay by voice.")
              }
            }
          }
          DeviceSessionState.STOPPED -> {
            cleanupSession()
            if (!userStoppedSession) {
              Log.i(TAG, "DeviceSession stopped; waiting for active device...")
              viewModelScope.launch {
                delay(3000)
                if (!userStoppedSession && !_uiState.value.hasSession && !_uiState.value.isBusy && wearablesViewModel.uiState.value.hasActiveDevice) {
                  startSession()
                }
              }
            }
          }
          else -> {}
        }
      }
    }
    sessionErrorJob = viewModelScope.launch {
      session.errors.collect { error ->
        // All session errors surface through the snackbar, including
        // DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED, which the SDK delivers as a one-shot event.
        Log.i(TAG, "Session notice: ${error.description}")
        if (error != DeviceSessionError.SESSION_ENDED_BY_DEVICE &&
            error != DeviceSessionError.NO_ELIGIBLE_DEVICE &&
            error != DeviceSessionError.SESSION_ALREADY_STOPPED) {
          wearablesViewModel.setRecentError(error.getLocalizedDescription(getApplication()))
        }
      }
    }
  }

  /**
   * Attaches the Speech capability to the started device session.
   * On-device speech-to-text runs on the glasses and delivers transcriptions to the app.
   */
  private fun attachSpeechCapability(session: DeviceSession) {
    if (speech != null) {
      startSpeechListening()
      return
    }
    val speechResult = session.addSpeech(SpeechConfiguration())
    speechResult.fold(
        onSuccess = { createdSpeech ->
          Log.i(TAG, "Attached Speech capability successfully")
          speech = createdSpeech
          observeSpeech(createdSpeech)
          startSpeechListening()
        },
        onFailure = { error, _ ->
          Log.w(TAG, "Failed to attach Speech capability: ${error.description}")
        }
    )
  }

  private fun observeSpeech(speech: Speech) {
    speechStateJob?.cancel()
    speechStateJob = viewModelScope.launch {
      speech.state.collect { state ->
        Log.d(TAG, "SpeechState: $state")
        _uiState.update { it.copy(speechState = state) }
        when (state) {
          SpeechState.STARTING -> {
            Log.d(TAG, "Speech is STARTING")
          }
          SpeechState.STARTED -> {
            Log.i(TAG, "Speech is STARTED - listening for transcriptions")
          }
          SpeechState.STOPPING -> {
            Log.d(TAG, "Speech is STOPPING")
          }
          SpeechState.STOPPED -> {
            Log.d(TAG, "Speech is STOPPED")
            if (!userStoppedSession && _uiState.value.isSessionActive && session != null && _uiState.value.glassesDisplayScreen != GlassesDisplayScreen.SCAN_QR && !_uiState.value.isStreaming) {
              Log.i(TAG, "Speech stopped while session is active; restarting speech listening...")
              viewModelScope.launch {
                delay(1000)
                if (!userStoppedSession && _uiState.value.isSessionActive && _uiState.value.glassesDisplayScreen != GlassesDisplayScreen.SCAN_QR) {
                  startSpeechListening(forceRestart = true)
                }
              }
            }
          }
        }
      }
    }

    speechErrorJob?.cancel()
    speechErrorJob = viewModelScope.launch {
      speech.errors.collect { error ->
        if (error != null) {
          handleSpeechError(error)
        }
      }
    }

    speechTranscriptionsJob?.cancel()
    speechTranscriptionsJob = viewModelScope.launch {
      speech.transcriptions.collect { result ->
        if (result != null) {
          handleTranscriptionResult(result)
        }
      }
    }
  }

  /**
   * Handles every [SpeechError] enum value exhaustively.
   */
  private fun handleSpeechError(error: SpeechError) {
    com.meta.wearable.dat.externalsampleapps.cameraaccess.error.AppErrorHandler.handleSpeechError(error)
  }

  private var lastPartialText = ""
  private var lastPartialTime = 0L
  private var lastProcessedCommand = ""
  private var lastProcessedCommandTime = 0L

  /**
   * Handles partial versus final transcriptions from the smart glasses.
   * Does not assume raw audio is available to the app — transcriptions arrive as text events.
   */
  private fun handleTranscriptionResult(result: TranscriptionResult) {
    val rawText = result.text.trim()
    Log.d(TAG, "Transcription: \"$rawText\", isFinal=${result.isFinal}, confidence=${result.confidence}")

    val now = System.currentTimeMillis()

    // If the engine emits empty text on isFinal, fall back to our buffered partial if recent
    val text = if (rawText.isNotEmpty()) {
      lastPartialText = rawText
      lastPartialTime = now
      rawText
    } else if (result.isFinal && lastPartialText.isNotEmpty() && (now - lastPartialTime < 3000L)) {
      val buffered = lastPartialText
      lastPartialText = ""
      buffered
    } else {
      return
    }

    // Wearer is speaking: stop TTS prompts immediately so the mic hears clearly without speaker bleed
    voicePrompter.stop()

    _uiState.update { it.copy(liveSpeechTranscription = text) }

    val cleanText = text.replace(Regex("[^a-zA-Z0-9 ]"), " ").replace(Regex("\\s+"), " ").lowercase().trim()
    if (cleanText.isEmpty()) return

    val currentPaymentState = _uiState.value.paymentState
    if (currentPaymentState is PaymentState.Processing) {
      Log.d(TAG, "Ignoring speech transcription while payment is processing")
      return
    }

    // Determine if cleanText contains an immediate actionable command
    val hasExit = cleanText.contains("exit") || cleanText.contains("close wallet") || cleanText.contains("quit") || cleanText == "leave"
    val hasHome = cleanText in setOf("home", "go home", "main menu", "return to home", "back to home") || cleanText == "back" || cleanText == "cancel"
    val hasScan = cleanText.contains("scan") || cleanText.contains("qr") || cleanText.contains("camera")
    val hasAnalytics = cleanText.contains("analytic") || cleanText.contains("chart") || cleanText.contains("spending analytic")
    val hasSend = cleanText.contains("send") || cleanText.contains("friend") || cleanText.contains("transfer")
    val hasSplit = cleanText.contains("split") || cleanText.contains("invoice")
    val hasConfirm = cleanText in setOf("pay", "yes", "yeah", "yep", "sure", "proceed", "confirm", "approve", "ok", "okay", "done", "do it", "confirm payment", "pay now")
    val hasCancel = cleanText in setOf("no", "stop", "dismiss", "dont", "do not")
    val hasParsedAmount = QrCodeAnalyzer.parseSpokenAmount(cleanText) != null
    val hasFriendName = cleanText.contains("alex") || cleanText.contains("maya") || cleanText.contains("sara") || cleanText.contains("devon")

    val isImmediateAction = hasExit || hasHome || hasScan || hasAnalytics || hasSend || hasSplit || hasConfirm || hasCancel || hasParsedAmount || hasFriendName

    val isDuplicate = (cleanText == lastProcessedCommand && (now - lastProcessedCommandTime < 1800L))
    if (!isDuplicate && (result.isFinal || isImmediateAction)) {
      lastProcessedCommand = cleanText
      lastProcessedCommandTime = now
      lastPartialText = ""
      processVoiceCommand(cleanText)
    }
  }

  /**
   * Processes voice commands from speech recognition to drive screen navigation & payment steps:
   * 1. Home screen actions ("scan qr", "view analytics", "send money to friend", "exit wallet")
   * 2. Return to home ("home", "back to home", "cancel")
   * 3. Contact selection ("Alex", "Maya", "Devon", "Sara")
   * 4. Setting amount (numbers or words)
   * 5. Confirming payment ("yes", "pay", etc.)
   */
  private fun processVoiceCommand(command: String) {
    val lower = command.lowercase().trim()
    Log.i(TAG, "Processing voice command: \"$lower\"")
    val currentPaymentState = _uiState.value.paymentState
    val currentScreen = _uiState.value.glassesDisplayScreen

    // 0. Top Priority: Exit wallet (Available everywhere)
    if (lower.contains("exit") || lower.contains("close wallet") || lower.contains("quit") || lower == "leave") {
      exitWalletDisplaySession()
      return
    }

    // 1. High Priority: Return to Home / Cancel (Available across all screens)
    if (lower == "home" || lower.contains("return to home") || lower.contains("back to home") || lower == "main menu" ||
        (currentScreen != GlassesDisplayScreen.HOME && (lower == "back" || lower == "cancel"))) {
      returnToGlassesHome()
      return
    }

    // 2. Payment Flow Voice Handlers (TOP PRIORITY: handles voice confirmation and amount entry)
    if (currentPaymentState is PaymentState.ConfirmPayment) {
      val isCancel = lower.contains("no") || lower.contains("cancel") ||
          lower.contains("stop") || lower.contains("dismiss") || lower.contains("back") ||
          lower.contains("don't") || lower.contains("dont")
      val isEdit = lower.contains("change") || lower.contains("edit") ||
          (lower.contains("amount") && !lower.contains("pay"))

      val parsedAmount = QrCodeAnalyzer.parseSpokenAmount(lower)

      val isConfirm = !isCancel && (
          lower.contains("yes") || lower.contains("yeah") || lower.contains("yep") ||
          lower.contains("pay") || lower.contains("proceed") ||
          lower.contains("confirm") || lower.contains("sure") ||
          lower.contains("send") || lower.contains("done") ||
          lower.contains("ok") || lower.contains("okay") ||
          lower.contains("approve") || lower.contains("go ahead") ||
          lower.contains("do it")
      )

      if (isCancel) {
        Log.i(TAG, "Voice cancelled payment in ConfirmPayment")
        returnToGlassesHome()
        return
      } else if (isEdit && (parsedAmount == null || parsedAmount <= 0.0)) {
        Log.i(TAG, "Voice requested change amount in ConfirmPayment")
        editPaymentAmount(currentPaymentState.details)
        return
      } else if (parsedAmount != null && parsedAmount > 0.0) {
        Log.i(TAG, "Voice updated amount in ConfirmPayment to: $parsedAmount")
        setPaymentAmount(parsedAmount)
        return
      } else if (isConfirm) {
        Log.i(TAG, "Voice confirmed payment in ConfirmPayment -> executing confirmPayment()")
        confirmPayment()
        return
      }
    }

    if (currentPaymentState is PaymentState.EnterAmount) {
      val isCancel = lower.contains("no") || lower.contains("cancel") ||
          lower.contains("stop") || lower.contains("dismiss") || lower.contains("back") ||
          lower.contains("don't") || lower.contains("dont")
      val parsedAmount = QrCodeAnalyzer.parseSpokenAmount(lower)
      val isConfirm = !isCancel && (
          lower.contains("yes") || lower.contains("yeah") || lower.contains("yep") ||
          lower.contains("pay") || lower.contains("proceed") ||
          lower.contains("confirm") || lower.contains("sure") ||
          lower.contains("send") || lower.contains("done") ||
          lower.contains("ok") || lower.contains("okay") ||
          lower.contains("approve") || lower.contains("submit")
      )

      if (isCancel) {
        Log.i(TAG, "Voice cancelled payment in EnterAmount")
        returnToGlassesHome()
        return
      } else if (parsedAmount != null && parsedAmount > 0.0) {
        Log.i(TAG, "Voice parsed amount in EnterAmount: $parsedAmount")
        setPaymentAmount(parsedAmount)
        return
      } else if (isConfirm) {
        Log.i(TAG, "Voice confirmed in EnterAmount -> executing onKeypadSubmit()")
        onKeypadSubmit()
        return
      }
    }

    if (currentPaymentState is PaymentState.Success) {
      if (lower.contains("split")) {
        val amt = currentPaymentState.details.amount ?: (if (currentPaymentState.details.currency == "USD") 15.0 else 100.0)
        Log.i(TAG, "Voice requested bill split after payment success with amount $$amt")
        dismissPayment()
        openGlassesBillSplitSelection(amt)
        return
      } else if (lower.contains("done") || lower.contains("exit") || lower.contains("close") || lower.contains("home")) {
        returnToGlassesHome()
        return
      } else if (lower.contains("scan") || lower.contains("rescan") || lower.contains("next")) {
        onScanQrClicked()
        return
      }
    }

    // 3. Screen-Specific Commands
    when (currentScreen) {
      GlassesDisplayScreen.HOME -> {
        if (lower.contains("object") || lower.contains("product") || lower.contains("buy this") || lower.contains("what is this") || lower.contains("shop")) {
          onScanObjectClicked()
          return
        } else if (lower.contains("bill") || lower.contains("receipt") || lower.contains("check")) {
          onScanBillClicked()
          return
        } else if (lower.contains("scan") || lower.contains("qr") || lower.contains("camera")) {
          onScanQrClicked()
          return
        } else if (lower.contains("analytic") || lower.contains("chart") || lower == "spending analytics" || lower == "view analytics") {
          onViewAnalyticsClicked()
          return
        } else if (lower.contains("split") || lower.contains("invoice")) {
          val parsedAmt = QrCodeAnalyzer.parseSpokenAmount(lower) ?: 0.0
          openGlassesBillSplitSelection(parsedAmt)
          return
        } else if (lower.contains("friend") || lower.contains("send") || lower.contains("contact") || lower.contains("payout")) {
          onSendMoneyClicked()
          return
        }
      }
      GlassesDisplayScreen.SCAN_OBJECT -> {
        if (lower.contains("buy") || lower.contains("pay") || lower.contains("confirm")) {
          executeMuseSparkObjectPurchase()
          return
        } else if (lower.contains("rescan") || lower.contains("again")) {
          onScanObjectClicked()
          return
        } else if (lower.contains("back") || lower.contains("cancel") || lower.contains("home")) {
          returnToGlassesHome()
          return
        }
      }
      GlassesDisplayScreen.SCAN_BILL -> {
        if (lower.contains("split") || lower.contains("confirm") || lower.contains("invoice")) {
          executeMuseSparkBillSplit()
          return
        } else if (lower.contains("rescan") || lower.contains("again")) {
          onScanBillClicked()
          return
        } else if (lower.contains("back") || lower.contains("cancel") || lower.contains("home")) {
          returnToGlassesHome()
          return
        }
      }
      GlassesDisplayScreen.SCAN_QR -> {
        if (lower.contains("cancel") || lower.contains("back") || lower.contains("home")) {
          returnToGlassesHome()
          return
        }
      }
      GlassesDisplayScreen.ANALYTICS -> {
        if (lower.contains("back") || lower.contains("cancel") || lower.contains("done") || lower.contains("home")) {
          returnToGlassesHome()
          return
        } else if (lower.contains("line graph") || lower.contains("weekly graph") || lower.contains("trend graph")) {
          _uiState.update { it.copy(glassesAnalyticsMode = GlassesAnalyticsMode.LINE_GRAPH) }
          renderGlassesScreen()
          voicePrompter.speak("Displaying weekly spend line graph.")
          return
        } else if (lower.contains("pie chart") || lower.contains("pie") || lower.contains("category chart") || lower.contains("categories")) {
          _uiState.update { it.copy(glassesAnalyticsMode = GlassesAnalyticsMode.PIE_CHART) }
          renderGlassesScreen()
          voicePrompter.speak("Displaying category pie chart.")
          return
        }
      }
      GlassesDisplayScreen.CONTACTS -> {
        if (lower.contains("alex")) {
          defaultFriends.find { it.name.contains("Alex", ignoreCase = true) }?.let { onSendToContactSelected(it) }
          return
        } else if (lower.contains("maya")) {
          defaultFriends.find { it.name.contains("Maya", ignoreCase = true) }?.let { onSendToContactSelected(it) }
          return
        } else if (lower.contains("devon")) {
          defaultFriends.find { it.name.contains("Devon", ignoreCase = true) }?.let { onSendToContactSelected(it) }
          return
        } else if (lower.contains("sara")) {
          defaultFriends.find { it.name.contains("Sara", ignoreCase = true) }?.let { onSendToContactSelected(it) }
          return
        } else if (lower.contains("back") || lower.contains("cancel") || lower.contains("home")) {
          returnToGlassesHome()
          return
        }
      }
      GlassesDisplayScreen.BILL_SPLIT -> {
        val parsedAmt = QrCodeAnalyzer.parseSpokenAmount(lower)
        if (lower.contains("back") || lower.contains("cancel") || lower.contains("done") || lower.contains("home")) {
          returnToGlassesHome()
          return
        } else if (lower.contains("send") || lower.contains("confirm") || lower.contains("pay") || lower.contains("yes") || lower.contains("invoice")) {
          confirmBillSplit()
          return
        } else if (lower.contains("add") || lower.contains("plus") || lower.contains("more")) {
          val delta = parsedAmt ?: 10.0
          adjustBillSplitAmount(delta)
          return
        } else if (lower.contains("minus") || lower.contains("less") || lower.contains("subtract")) {
          val delta = parsedAmt ?: 10.0
          adjustBillSplitAmount(-delta)
          return
        } else if (parsedAmt != null && parsedAmt > 0.0 && (lower.contains("set") || lower.contains("make") || lower.contains("amount") || lower.contains("dollar"))) {
          setBillSplitAmount(parsedAmt)
          return
        } else if (lower.contains("maya")) {
          defaultFriends.find { it.name.contains("Maya", ignoreCase = true) }?.let { toggleBillSplitFriend(it.id) }
          return
        } else if (lower.contains("sara")) {
          defaultFriends.find { it.name.contains("Sara", ignoreCase = true) }?.let { toggleBillSplitFriend(it.id) }
          return
        } else if (lower.contains("alex")) {
          defaultFriends.find { it.name.contains("Alex", ignoreCase = true) }?.let { toggleBillSplitFriend(it.id) }
          return
        } else if (lower.contains("devon")) {
          defaultFriends.find { it.name.contains("Devon", ignoreCase = true) }?.let { toggleBillSplitFriend(it.id) }
          return
        } else if (lower.contains("split") || lower.contains("another")) {
          val nextAmt = parsedAmt ?: (_uiState.value.billSplitAmount.takeIf { it > 0.0 } ?: 50.0)
          openGlassesBillSplitSelection(nextAmt)
          return
        } else if (parsedAmt != null && parsedAmt > 0.0) {
          setBillSplitAmount(parsedAmt)
          return
        }
      }
      GlassesDisplayScreen.BILL_SPLIT_KEYPAD -> {
        val parsedAmt = QrCodeAnalyzer.parseSpokenAmount(lower)
        if (lower.contains("cancel") || lower.contains("back")) {
          cancelBillSplitKeypad()
          return
        } else if (lower.contains("done") || lower.contains("confirm") || lower.contains("set") || lower.contains("submit") || lower.contains("yes")) {
          submitBillSplitKeypad()
          return
        } else if (parsedAmt != null && parsedAmt > 0.0) {
          keypadAmountString = if (parsedAmt % 1.0 == 0.0) parsedAmt.toInt().toString() else "%.2f".format(parsedAmt)
          submitBillSplitKeypad()
          return
        }
      }
      GlassesDisplayScreen.PAYMENT -> {
        if (lower.contains("cancel") || lower.contains("back") || lower.contains("no")) {
          returnToGlassesHome()
          return
        } else if (lower.contains("pay") || lower.contains("yes") || lower.contains("confirm") || lower.contains("ok")) {
          confirmPayment()
          return
        }
      }
      GlassesDisplayScreen.VOICE_QA_LISTENING -> {
        if (lower.contains("back") || lower.contains("cancel") || lower.contains("done")) {
          dismissVoiceQaListening()
          return
        }
      }
    }

    // 4. Voice Bill Splitting shortcut with named contacts (across any screen)
    if (lower.contains("split")) {
      val parsedAmt = QrCodeAnalyzer.parseSpokenAmount(lower) ?: (_uiState.value.billSplitAmount.takeIf { it > 0.0 } ?: 50.0)
      val selectedFriends = mutableListOf<FriendContact>()
      if (lower.contains("maya")) defaultFriends.find { it.name.contains("Maya", ignoreCase = true) }?.let { selectedFriends.add(it) }
      if (lower.contains("sara")) defaultFriends.find { it.name.contains("Sara", ignoreCase = true) }?.let { selectedFriends.add(it) }
      if (lower.contains("alex")) defaultFriends.find { it.name.contains("Alex", ignoreCase = true) }?.let { selectedFriends.add(it) }
      if (lower.contains("devon")) defaultFriends.find { it.name.contains("Devon", ignoreCase = true) }?.let { selectedFriends.add(it) }

      if (selectedFriends.isNotEmpty()) {
        Log.i(TAG, "Voice Bill Split with selected receivers: amount=$$parsedAmt with ${selectedFriends.map { it.name }}")
        triggerBillSplit(parsedAmt, selectedFriends)
        return
      } else {
        Log.i(TAG, "Voice Bill Split without named receivers: opening interactive selector for $$parsedAmt")
        openGlassesBillSplitSelection(parsedAmt)
        return
      }
    }

    // 5. Voice Payouts with PayPal Payouts v1 (across any screen)
    if (lower.contains("payout") || (lower.contains("send") && lower.contains("paypal"))) {
      val parsedAmt = QrCodeAnalyzer.parseSpokenAmount(lower) ?: 15.0
      val targetFriend = when {
        lower.contains("maya") -> defaultFriends.find { it.name.contains("Maya", ignoreCase = true) }
        lower.contains("sara") -> defaultFriends.find { it.name.contains("Sara", ignoreCase = true) }
        lower.contains("alex") -> defaultFriends.find { it.name.contains("Alex", ignoreCase = true) }
        else -> defaultFriends.firstOrNull { it.rail == "PayPal" }
      }
      if (targetFriend != null) {
        Log.i(TAG, "Voice PayPal Payout command recognized: amount=$$parsedAmt to ${targetFriend.name}")
        triggerPayPalPayout(targetFriend, parsedAmt)
        return
      }
    }

    // 6. Hands-Free Spending & Analytics AI Q&A detection
    val isSpendingQuery = lower.contains("average") || lower.contains("avg") ||
        lower.contains("week") || lower.contains("weekly") ||
        lower.contains("month") || lower.contains("monthly") ||
        lower.contains("day") || lower.contains("daily") ||
        lower.contains("highest") || lower.contains("biggest") || lower.contains("most expensive") || lower.contains("largest") ||
        lower.contains("lowest") || lower.contains("smallest") || lower.contains("cheapest") ||
        lower.contains("last") || lower.contains("recent") || lower.contains("latest") ||
        lower.contains("today") || lower.contains("spent today") ||
        lower.contains("spend") || lower.contains("spent") || lower.contains("expense") ||
        lower.contains("purchase") || lower.contains("cost") ||
        lower.contains("how much") || lower.contains("balance") || lower.contains("budget") ||
        lower.contains("burn") || lower.contains("forecast") || lower.contains("score") || lower.contains("health")

    if (isSpendingQuery || currentScreen == GlassesDisplayScreen.VOICE_QA_LISTENING || currentScreen == GlassesDisplayScreen.ANALYTICS) {
      Log.i(TAG, "Voice spending analytical question detected: \"$command\"")
      askSpendingAiQuestion(command, speakOutput = true)
      return
    }
  }

  /**
   * Requests MICROPHONE permission before starting Speech on the smart glasses.
   */
  fun startSpeechListening(forceRestart: Boolean = false) {
    if (_uiState.value.glassesDisplayScreen == GlassesDisplayScreen.SCAN_QR || _uiState.value.isStreaming) {
      Log.d(TAG, "startSpeechListening: camera stream is active - suppressing speech to preserve BT bandwidth")
      return
    }
    if (!_uiState.value.isSessionActive || session == null) {
      Log.d(TAG, "startSpeechListening: session is inactive, skipping speech")
      return
    }
    val currentState = _uiState.value.speechState
    if (!forceRestart && (currentState == SpeechState.STARTED || currentState == SpeechState.STARTING)) {
      Log.d(TAG, "startSpeechListening: speech is already $currentState - skipping redundant start")
      return
    }
    val speechInstance = speech
    if (speechInstance == null) {
      Log.i(TAG, "startSpeechListening: speech capability is null, checking active session to attach...")
      val s = session
      if (s != null && _uiState.value.isSessionActive && _uiState.value.glassesDisplayScreen != GlassesDisplayScreen.SCAN_QR) {
        attachSpeechCapability(s)
      }
      return
    }

    if (isMicPermissionGranted) {
      startSpeechInstanceInternal(speechInstance, forceRestart)
      return
    }

    viewModelScope.launch(Dispatchers.IO) {
      Wearables.checkPermissionStatus(Permission.MICROPHONE)
          .onSuccess { status ->
            Log.i(TAG, "MICROPHONE permission status: $status")
            withContext(Dispatchers.Main) {
              when (status) {
                PermissionStatus.Granted -> {
                  isMicPermissionGranted = true
                  startSpeechInstanceInternal(speechInstance, forceRestart)
                }
                is PermissionStatus.Denied -> {
                  Log.w(TAG, "MICROPHONE permission is DENIED in Meta View app")
                  _uiState.update { it.copy(showMicPermissionRedirectConfirm = true) }
                }
              }
            }
          }
          .onFailure { error, _ ->
            Log.w(TAG, "Check MICROPHONE permission failed: ${error.description} (device may be reconnecting)")
            // Suppress error banner on UI to avoid spamming user during reconnections
          }
    }
  }

  private fun startSpeechInstanceInternal(speechInstance: Speech, forceRestart: Boolean) {
    if (_uiState.value.speechState == SpeechState.STARTED && !forceRestart) {
      Log.d(TAG, "Speech already in STARTED state, skipping speech.start()")
      return
    }
    Log.i(TAG, "Starting/ensuring speech recognition on smart glasses...")
    val startResult = speechInstance.start()
    startResult.fold(
        onSuccess = { Log.i(TAG, "speechInstance.start() succeeded") },
        onFailure = { error, _ ->
          if (error == SpeechError.ALREADY_LISTENING || error.name == "INVALID_STATE") {
            Log.d(TAG, "Speech already listening on glasses ($error)")
          } else {
            Log.e(TAG, "speechInstance.start() failed: ${error.description}")
            handleSpeechError(error)
          }
        }
    )
  }

  fun confirmMicPermissionRedirect(
      requestPermission: suspend (Permission) -> PermissionStatus,
  ) {
    _uiState.update { it.copy(showMicPermissionRedirectConfirm = false) }
    viewModelScope.launch(Dispatchers.IO) {
      val status = requestPermission(Permission.MICROPHONE)
      withContext(Dispatchers.Main) {
        if (status == PermissionStatus.Granted) {
          isMicPermissionGranted = true
          val speechInstance = speech ?: return@withContext
          val startResult = speechInstance.start()
          startResult.onFailure { error, _ ->
            handleSpeechError(error)
          }
        }
      }
    }
  }

  fun cancelMicPermissionRedirect() {
    _uiState.update { it.copy(showMicPermissionRedirectConfirm = false) }
  }

  fun stopSpeechListening() {
    speech?.stop()
  }

  private fun cleanupSession() {
    sessionDeviceInfoJob?.cancel()
    sessionDeviceInfoJob = null
    speechStateJob?.cancel()
    speechStateJob = null
    speechErrorJob?.cancel()
    speechErrorJob = null
    speechTranscriptionsJob?.cancel()
    speechTranscriptionsJob = null
    speech?.stop()
    speech = null
    sessionStateJob?.cancel()
    sessionStateJob = null
    sessionErrorJob?.cancel()
    sessionErrorJob = null
    displayStateJob?.cancel()
    displayStateJob = null
    display = null
    session = null
    _uiState.update { it.copy(sessionState = DeviceSessionState.IDLE) }
  }

  // MARK: - Lifecycle step 2: stream (preview)

  /**
   * Starts the camera stream (preview). Requires an active session. Camera permission is checked
   * first (a query, no redirect); if it isn't granted, the actual request — which redirects to the
   * Meta AI app — is deferred to [confirmCameraPermissionRedirect] so the app-switch is confirmed.
   */
  fun startStreaming() {
    userStoppedStream = false
    if (_uiState.value.glassesDisplayScreen != GlassesDisplayScreen.SCAN_QR) {
      Log.w(TAG, "startStreaming blocked: current screen is ${_uiState.value.glassesDisplayScreen}, not SCAN_QR")
      return
    }
    if (!_uiState.value.isSessionActive) {
      if (!_uiState.value.hasSession) {
        Log.i(TAG, "startStreaming called but device session not active yet; initiating startSession()")
        startSession()
        return
      }
      wearablesViewModel.setRecentError(
          getApplication<Application>().getString(R.string.error_start_session_first)
      )
      return
    }
    if (stream != null || _uiState.value.isStartingStream) return
    _uiState.update { it.copy(isStartingStream = true) }
    viewModelScope.launch(Dispatchers.IO) {
      try {
        Wearables.checkPermissionStatus(Permission.CAMERA)
            .onSuccess { status ->
              withContext(Dispatchers.Main) {
                if (status == PermissionStatus.Granted) {
                  beginStream()
                } else {
                  _uiState.update { it.copy(showCameraPermissionRedirectConfirm = true) }
                }
              }
            }
            .onFailure { error, _ ->
              Log.e(TAG, "Failed to check camera permission: ${error.description}")
              withContext(Dispatchers.Main) {
                wearablesViewModel.setRecentError(error.getLocalizedDescription(getApplication()))
              }
            }
      } finally {
        withContext(Dispatchers.Main) {
          _uiState.update { it.copy(isStartingStream = false) }
        }
      }
    }
  }

  /** Confirmed from the permission prompt: requests camera access, then starts the stream. */
  fun confirmCameraPermissionRedirect(
      requestPermission: suspend (Permission) -> PermissionStatus,
  ) {
    _uiState.update { it.copy(showCameraPermissionRedirectConfirm = false) }
    if (!_uiState.value.isSessionActive || stream != null) return
    viewModelScope.launch(Dispatchers.IO) {
      val status = requestPermission(Permission.CAMERA)
      withContext(Dispatchers.Main) {
        if (status == PermissionStatus.Granted) {
          beginStream()
        } else {
          wearablesViewModel.setRecentError(
              getApplication<Application>().getString(R.string.error_camera_permission_denied)
          )
        }
      }
    }
  }

  fun cancelCameraPermissionRedirect() {
    _uiState.update { it.copy(showCameraPermissionRedirectConfirm = false) }
  }

  private fun beginStream() {
    val current = session ?: return
    if (_uiState.value.glassesDisplayScreen != GlassesDisplayScreen.SCAN_QR) {
      Log.w(TAG, "beginStream blocked: current screen is ${_uiState.value.glassesDisplayScreen}, not SCAN_QR")
      return
    }
    if (stream != null) return
    // Foreground service keeps the stream/recording alive while backgrounded.
    StreamingService.start(getApplication())
    current
        .addCamera(
            StreamConfiguration(
                videoQuality = VideoQuality.LOW,
                frameRate = FRAME_RATE,
                // Compressed HEVC so frames feed both the on-screen decoder and the passthrough
                // MP4 writer.
                compressVideo = true,
            )
        )
        .onSuccess { addedCamera ->
          camera = addedCamera
          val added = addedCamera.stream
          stream = added
          // Subscribe before start() so no initial transitions are missed.
          setupStreamListeners(added)
          _uiState.update { it.copy(streamState = StreamState.STARTING) }
          added.start().onFailure { error, _ ->
            Log.e(TAG, "Failed to start stream: ${error.description}")
            wearablesViewModel.setRecentError(error.getLocalizedDescription(getApplication()))
            // A failed start leaves the stream attached and the FGS running — tear both down so the
            // UI doesn't stick at STARTING, matching the addCamera() failure path below.
            clearStreamResources()
          }
        }
        .onFailure { error, _ ->
          Log.e(TAG, "Failed to add camera: ${error.description}")
          StreamingService.stop(getApplication())
          wearablesViewModel.setRecentError(error.getLocalizedDescription(getApplication()))
        }
  }

  /** Stops the camera stream but keeps the [DeviceSession] connected. */
  fun stopStreaming() {
    userStoppedStream = true
    stopQrScanningLoop()
    if (_uiState.value.streamState == StreamState.STOPPING || _uiState.value.streamState == StreamState.STOPPED) {
      return
    }
    val current = camera
    if (current == null) {
      if (stream != null) {
        clearStreamResources()
      }
      return
    }
    _uiState.update { it.copy(streamState = StreamState.STOPPING) }
    current.stop()
    // The stream-state collector converges teardown when STOPPED/CLOSED arrives.
  }

  private fun setupStreamListeners(stream: Stream) {
    videoJob =
        viewModelScope.launch(frameDispatcher) {
          stream.videoStream.collect { handleVideoFrame(it) }
        }
    streamStateJob = viewModelScope.launch {
      // state replays its current value (STOPPED) on subscribe, and we subscribe before start().
      var hasBeenActive = false
      stream.state.collect { state ->
        _uiState.update { it.copy(streamState = state) }
        val isTerminal = state == StreamState.STOPPED || state == StreamState.CLOSED
        if (!isTerminal) {
          hasBeenActive = true
          if (state == StreamState.STREAMING) {
            Log.i(TAG, "StreamState is STREAMING - starting QR scanning loop")
            startQrScanningLoop()
          }
        } else if (hasBeenActive) {
          hasBeenActive = false
          stopQrScanningLoop()
          onStreamTerminated()
        }
      }
    }
    streamErrorJob = viewModelScope.launch {
      stream.errorStream.collect { error ->
        Log.e(TAG, "Stream error: ${error.description}")
        wearablesViewModel.setRecentError(error.getLocalizedDescription(getApplication()))
      }
    }
  }

  private fun handleVideoFrame(videoFrame: VideoFrame) {
    if (!videoFrame.isCompressed) return

    val buffer = videoFrame.buffer
    val width = videoFrame.width
    val height = videoFrame.height
    val presentationTimeUs = videoFrame.presentationTimeUs

    val byteArray = ByteArray(buffer.remaining())
    val originalPosition = buffer.position()
    buffer.get(byteArray)
    buffer.position(originalPosition)

    // Accumulate the parameter sets so a recording (or decoder) started after stream start can be
    // primed with a complete VPS+SPS+PPS set.
    csdCollector.offer(byteArray)

    // Append to the recorder (no-op unless recording); keeps writing while backgrounded.
    videoRecorder.writeCompressedFrame(
        byteArray,
        presentationTimeUs,
        width,
        height,
        videoFrame.isCodecConfig,
    )

    // Lazily create the decoder once a Surface is available; it renders directly to it. Prime it
    // with the cached config in case the surface arrived after the config frame. Guarded so a
    // concurrent setSurface(null) can't leave a decoder bound to a released Surface.
    synchronized(decoderLock) {
      val surface = decoderSurface
      if (hevcDecoder == null && surface != null) {
        hevcDecoder =
            HevcDecoder().also { decoder ->
              decoder.start(width, height, surface)
              csdCollector.complete()?.let { decoder.decodeFrame(it, 0) }
            }
      }
      // Feed under the lock so teardown can't null the decoder between check and feed.
      hevcDecoder?.decodeFrame(byteArray, presentationTimeUs)
    }

    if (!videoFrame.isCodecConfig && !_uiState.value.hasReceivedFirstFrame) {
      _uiState.update { it.copy(hasReceivedFirstFrame = true) }
    }
  }

  private fun onStreamTerminated() {
    // Finalize an in-progress recording before releasing the foreground service / wake lock, so the
    // MP4 mux on Dispatchers.IO isn't cut off when the stream stops while backgrounded.
    viewModelScope.launch {
      if (_uiState.value.isRecording) {
        stopVideoRecording()
      }
      clearStreamResources()
    }
  }

  @Volatile private var isClearingStream = false

  private fun clearStreamResources() {
    if (isClearingStream) return
    isClearingStream = true
    try {
      videoJob?.cancel()
      videoJob = null
      streamStateJob?.cancel()
      streamStateJob = null
      streamErrorJob?.cancel()
      streamErrorJob = null
      synchronized(decoderLock) {
        hevcDecoder?.stop()
        hevcDecoder = null
      }
      csdCollector.reset()
      StreamingService.stop(getApplication())
      // STOPPED is restartable, so only stop() detaches the capability; without it the next
      // addCamera() fails with "a capability of this type is already active". Stopping the camera
      // cascades to its stream child.
      stopCamera(camera)
      camera = null
      stream = null
      _uiState.update { it.copy(streamState = StreamState.STOPPED, hasReceivedFirstFrame = false) }
    } finally {
      isClearingStream = false
    }
  }

  /**
   * Stops the camera by closing it. Accepting a [java.io.Closeable] parameter satisfies the
   * AutoCloseableUse detector, which skips methods that take an AutoCloseable argument — the camera
   * outlives any single `use {}` block, so it is stopped explicitly here rather than auto-closed.
   */
  private fun stopCamera(capability: java.io.Closeable?) {
    try {
      (capability as? com.meta.wearable.dat.camera.Camera)?.stop()
    } catch (e: Exception) {
      Log.w(TAG, "Exception during camera.stop(): ${e.message}")
    }
    try {
      capability?.close()
    } catch (e: Exception) {
      Log.w(TAG, "Exception during camera.close(): ${e.message}")
    }
  }

  // MARK: - Capture

  fun capturePhoto() {
    if (_uiState.value.isCapturingPhoto || !_uiState.value.isStreaming) return
    _uiState.update { it.copy(isCapturingPhoto = true) }
    viewModelScope.launch {
      stream
          ?.capturePhoto()
          ?.onSuccess { photoData ->
            // Decode/rotate is CPU-bound and blocking; keep it off the main thread so capture
            // doesn't jank the UI. Resumes on main for the state update.
            val bitmap = withContext(Dispatchers.Default) { decodePhoto(photoData) }
            if (bitmap != null) {
              _uiState.update {
                it.copy(isCapturingPhoto = false, activePreview = CapturePreview.Photo(bitmap))
              }
            } else {
              _uiState.update { it.copy(isCapturingPhoto = false) }
              wearablesViewModel.setRecentError(
                  getApplication<Application>().getString(R.string.error_photo_capture_failed)
              )
            }
          }
          ?.onFailure { error, _ ->
            Log.e(TAG, "Failed to capture photo: ${error.description}")
            _uiState.update { it.copy(isCapturingPhoto = false) }
            wearablesViewModel.setRecentError(error.getLocalizedDescription(getApplication()))
          } ?: _uiState.update { it.copy(isCapturingPhoto = false) }
    }
  }

  // MARK: - Recording

  fun toggleRecording(requestRecordAudioPermission: suspend () -> Boolean) {
    if (_uiState.value.isRecording) {
      viewModelScope.launch { stopVideoRecording() }
    } else {
      startVideoRecording(requestRecordAudioPermission)
    }
  }

  fun startVideoRecording(requestRecordAudioPermission: suspend () -> Boolean) {
    if (!_uiState.value.isStreaming || _uiState.value.isRecording) return
    viewModelScope.launch {
      // Request the mic permission only when sound-in-video is on; record video-only if it's off or
      // the user declines. The prompt appears in context on the first record with the mic on.
      val includeAudio = _uiState.value.includeAudioInStream && requestRecordAudioPermission()
      if (!_uiState.value.isStreaming || _uiState.value.isRecording) return@launch
      // If the user wanted sound but denied the mic, reflect it so the mic icon doesn't stay "on".
      if (_uiState.value.includeAudioInStream && !includeAudio) {
        _uiState.update { it.copy(includeAudioInStream = false) }
      }
      videoRecorder.setIncludeAudio(includeAudio)
      videoRecorder.startRecording(csdCollector.complete())
    }
  }

  suspend fun stopVideoRecording() {
    if (!_uiState.value.isRecording) return
    // The writer starts on the first keyframe. If stop lands just before that frame, wait briefly
    // so even a quick recording finalizes to a file instead of being discarded.
    var waited = 0L
    while (!videoRecorder.hasStartedWriting.value && waited < KEYFRAME_WAIT_MAX_MS) {
      delay(KEYFRAME_WAIT_STEP_MS)
      waited += KEYFRAME_WAIT_STEP_MS
    }
    when (val result = videoRecorder.stopRecording()) {
      is RecordingResult.Completed ->
          _uiState.update { it.copy(activePreview = CapturePreview.Video(result.uri)) }
      RecordingResult.NoRecording ->
          wearablesViewModel.setRecentError(
              getApplication<Application>().getString(R.string.error_recording_too_short)
          )
      RecordingResult.Failed ->
          wearablesViewModel.setRecentError(
              getApplication<Application>().getString(R.string.error_recording_save_failed)
          )
    }
  }

  fun toggleMic() {
    if (!_uiState.value.isStreaming || _uiState.value.isRecording) return
    _uiState.update { it.copy(includeAudioInStream = !it.includeAudioInStream) }
  }

  // MARK: - Dismissers

  fun dismissCapturePreview() {
    val preview = _uiState.value.activePreview
    _uiState.update { it.copy(activePreview = null) }
    if (preview is CapturePreview.Video) {
      // The clip lives in the cache dir, exposed as a FileProvider content URI; delete through the
      // resolver so it resolves back to the real cache file (the URI's path is the provider
      // mapping,
      // not a filesystem path). Photos are held in memory — nothing on disk to clean up.
      viewModelScope.launch(Dispatchers.IO) {
        runCatching {
          getApplication<Application>().contentResolver.delete(preview.uri, null, null)
        }
            .onFailure { Log.w(TAG, "Failed to delete temp recording", it) }
      }
    }
  }

  /**
   * Directly analyzes a frame sampled from the live video stream (runs ~8x/second).
   * Achieves near-instantaneous (< 10ms) QR recognition without waiting for photo capture!
   */
  fun analyzeFrameForQr(bitmap: Bitmap) {
    if (_uiState.value.paymentState !is PaymentState.Idle) {
      bitmap.recycle()
      return
    }
    if (!isScanningQrFrame.compareAndSet(false, true)) {
      bitmap.recycle()
      return
    }
    viewModelScope.launch(Dispatchers.Default) {
      try {
        val qrText = QrCodeAnalyzer.decodeBitmap(bitmap)
        if (!qrText.isNullOrBlank()) {
          Log.i(TAG, "QR code recognized from live video stream: $qrText")
          val paymentDetails = QrCodeAnalyzer.parsePaymentQr(
              qrContent = qrText,
              preferredMethod = _uiState.value.selectedPaymentMethod,
          )
          if (paymentDetails != null) {
            withContext(Dispatchers.Main) {
              onPaymentQrDetected(paymentDetails)
            }
          }
        }
      } catch (e: Exception) {
        Log.d(TAG, "Live video frame QR decode error: ${e.message}")
      } finally {
        bitmap.recycle()
        isScanningQrFrame.set(false)
      }
    }
  }

  // MARK: - Photo decoding

  private fun decodePhoto(photo: PhotoData): Bitmap? =
      when (photo) {
        is PhotoData.Bitmap -> photo.bitmap
        is PhotoData.HEIC -> decodeWithOrientation(photo.data)
      }

  /**
   * Reliable decoder for captured still photo fallback.
   * Smart glasses still photo is high-resolution (up to 3024x4032).
   * Scales down to 1280px max dimension for fast decoding and memory safety,
   * avoiding compound cropping that cuts off QR codes.
   */
  private fun decodePhotoForScanning(photo: PhotoData): Bitmap? {
    val fullBitmap = decodePhoto(photo) ?: return null
    val maxDim = maxOf(fullBitmap.width, fullBitmap.height)
    if (maxDim > 2048) {
      val scale = 2048f / maxDim
      val scaledW = (fullBitmap.width * scale).toInt().coerceAtLeast(1)
      val scaledH = (fullBitmap.height * scale).toInt().coerceAtLeast(1)
      val scaled = Bitmap.createScaledBitmap(fullBitmap, scaledW, scaledH, true)
      if (scaled != fullBitmap) {
        fullBitmap.recycle()
      }
      return scaled
    }
    return fullBitmap
  }

  // The glasses store orientation in an EXIF tag that BitmapFactory/ImageDecoder don't apply for
  // HEIC, so read TAG_ORIENTATION and rotate — otherwise the preview and shared image are sideways.
  private fun decodeWithOrientation(data: ByteBuffer): Bitmap? {
    val buffer = data.duplicate().apply { rewind() }
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    if (bitmap == null || bitmap.width == 0 || bitmap.height == 0) {
      bitmap?.recycle()
      Log.e(TAG, "Failed to decode captured photo")
      return null
    }

    val matrix = exifOrientationMatrix(bytes)
    if (matrix.isIdentity) return bitmap

    // Rotating allocates a second full-size bitmap; recycle the source, and fall back to the
    // unrotated image if the device is too low on memory to make the copy.
    return try {
      Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
        bitmap.recycle()
      }
    } catch (e: OutOfMemoryError) {
      Log.e(TAG, "Failed to rotate captured photo", e)
      bitmap
    }
  }

  private fun exifOrientationMatrix(bytes: ByteArray): Matrix {
    val orientation =
        try {
          ByteArrayInputStream(bytes).use { input ->
            ExifInterface(input)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
          }
        } catch (e: IOException) {
          Log.w(TAG, "Failed to read EXIF orientation", e)
          ExifInterface.ORIENTATION_NORMAL
        }
    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.postRotate(90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.postRotate(270f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    }
    return matrix
  }

  // MARK: - Payment & QR Code Scanning

  private fun startQrScanningLoop() {
    if (qrScanningJob?.isActive == true) return
    qrScanningJob = viewModelScope.launch(Dispatchers.Default) {
      val startTimeMs = System.currentTimeMillis()
      var scanLoopAttempts = 0
      while (isActive) {
        if (_uiState.value.isStreaming &&
            _uiState.value.glassesDisplayScreen == GlassesDisplayScreen.SCAN_QR &&
            _uiState.value.paymentState is PaymentState.Idle) {
          scanLoopAttempts++

          val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000
          // If searching for > 6 seconds without a hit, give wearer a supportive voice instruction reminder
          if (elapsedSec in 6..7 && scanLoopAttempts % 12 == 0) {
            if (!isDisplayCapableDevice()) {
              voicePrompter.speak("Remember, camera is on your left frame. Aim with your left eye, 1 to 2 feet away.")
            }
          }
        }
        delay(250)
      }
    }
  }

  private fun stopQrScanningLoop() {
    qrScanningJob?.cancel()
    qrScanningJob = null
  }

  fun togglePayPalVaultSheet(show: Boolean) {
    _uiState.update { it.copy(showPayPalVaultSheet = show) }
  }

  fun selectPaymentMethod(method: PaymentMethod) {
    _uiState.update { it.copy(selectedPaymentMethod = method) }
  }

  fun linkDefaultSandboxVault() {
    payPalVaultRepository.linkDefaultSandboxVault()
  }

  fun linkCustomPayPalAccount(email: String, name: String, funding: String = "PayPal Balance & Visa •••• 4242") {
    payPalVaultRepository.linkCustomAccount(email, name, funding)
  }

  fun unlinkPayPalAccount() {
    payPalVaultRepository.unlinkVault()
  }

  fun scanQrNow() {
    if (!_uiState.value.isStreaming || _uiState.value.isCapturingPhoto) return
    viewModelScope.launch(Dispatchers.Default) {
      scanQrFrameInternal()
    }
  }

  private suspend fun scanQrFrameInternal() {
    val currentStream = stream ?: return
    if (!_uiState.value.isStreaming) return
    if (!isScanningQrFrame.compareAndSet(false, true)) return
    try {
      currentStream.capturePhoto()
          .onSuccess { photoData ->
            val bitmap = decodePhotoForScanning(photoData) ?: return@onSuccess
            val qrText = QrCodeAnalyzer.decodeBitmap(bitmap)
            if (!qrText.isNullOrBlank()) {
              Log.i(TAG, "QR code recognized: $qrText")
              val paymentDetails = QrCodeAnalyzer.parsePaymentQr(
                  qrContent = qrText,
                  preferredMethod = _uiState.value.selectedPaymentMethod,
              )
              if (paymentDetails != null) {
                withContext(Dispatchers.Main) {
                  onPaymentQrDetected(paymentDetails)
                }
              }
            }
          }
          .onFailure { error, _ ->
            Log.d(TAG, "QR scan capturePhoto failure: ${error.description}")
          }
    } catch (e: Exception) {
      Log.d(TAG, "QR scan attempt error: ${e.message}")
    } finally {
      isScanningQrFrame.set(false)
    }
  }

  fun onPaymentQrDetected(details: PaymentDetails) {
    if (_uiState.value.paymentState !is PaymentState.Idle) return
    val displayCapable = isDisplayCapableDevice()
    Log.i(TAG, "Payment QR detected: ${details.payeeName}. Method=${details.paymentMethod}, isDisplayCapable=$displayCapable")

    // Stop QR scanning and shut down camera stream immediately for payment UI
    userStoppedStream = true
    stopQrScanningLoop()
    val wasStreaming = _uiState.value.isStreaming || _uiState.value.streamState != StreamState.STOPPED || camera != null
    if (wasStreaming) {
      stopStreaming()
    }
    keypadAmountString = ""

    val currWord = if (details.currency == "USD") "dollars" else "rupees"
    val amount = details.amount
    if (amount == null || amount <= 0.0) {
      _uiState.update {
        it.copy(
            glassesDisplayScreen = GlassesDisplayScreen.PAYMENT,
            paymentState = PaymentState.EnterAmount(details),
            keypadAmount = ""
        )
      }
      viewModelScope.launch {
        if (displayCapable) {
          transitionFromCameraToDisplayCapability(details)
        } else {
          if (wasStreaming) delay(600)
          val railPrompt = if (details.paymentMethod == PaymentMethod.PAYPAL_VAULT) "via PayPal" else ""
          voicePrompter.speak("Found payee ${details.payeeName} $railPrompt. Please say amount to pay, for example 15 $currWord.")
        }
        startSpeechListening()
      }
    } else {
      _uiState.update {
        it.copy(
            glassesDisplayScreen = GlassesDisplayScreen.PAYMENT,
            paymentState = PaymentState.ConfirmPayment(details),
            keypadAmount = ""
        )
      }
      viewModelScope.launch {
        if (displayCapable) {
          transitionFromCameraToDisplayCapability(details)
          initSpeechConfirmation()
        } else {
          if (wasStreaming) delay(600)
          promptForConfirmation(details)
        }
      }
    }
  }

  private suspend fun transitionFromCameraToDisplayCapability(details: PaymentDetails) {
    Log.i(TAG, "Payment QR detected. Updating glasses display to payment screen...")
    if (!isDisplayCapableDevice()) {
      Log.i(TAG, "transitionFromCameraToDisplayCapability skipped: device lacks display.")
      return
    }
    // 1. Stop QR scanning loop so we don't process redundant QR codes while payment is in progress
    stopQrScanningLoop()

    // 2. Wait until stream actually transitions to STOPPED before rendering payment display
    var waited = 0
    while (waited < 1500 && (_uiState.value.streamState != StreamState.STOPPED || camera != null)) {
      delay(100)
      waited += 100
    }
    delay(600) // critical 600ms settling delay for Meta glasses firmware to cleanly dispose the BTC RFCOMM lease

    // 3. Attach Display capability to the active session or render payment UI if already active
    val currentSession = session
    if (currentSession != null && _uiState.value.isSessionActive) {
      if (display != null && _uiState.value.displayState == DisplayState.STARTED) {
        Log.i(TAG, "Display already active. Rendering payment UI immediately on glasses...")
        renderGlassesScreen()
      } else {
        Log.i(TAG, "Attaching display capability for payment...")
        attachDisplayCapability(currentSession)
      }
    } else {
      Log.i(TAG, "Session is not active or null. Starting session for payment...")
      startSession()
    }
  }

  private fun attachDisplayCapability(session: DeviceSession) {
    if (!isDisplayCapableDevice(session)) {
      Log.i(TAG, "attachDisplayCapability skipped: Connected Meta glasses lack display capability. Running in voice-only mode.")
      _uiState.update { it.copy(isDisplayCapable = false) }
      return
    }
    val currentDisplay = display
    if (currentDisplay != null) {
      if (_uiState.value.displayState == DisplayState.STARTED) {
        Log.i(TAG, "Display capability already active and STARTED, rendering glasses UI...")
        renderGlassesScreen()
        return
      } else if (_uiState.value.displayState == DisplayState.STARTING) {
        Log.i(TAG, "Display capability is STARTING, waiting for device...")
        return
      } else {
        Log.i(TAG, "Display capability is in ${_uiState.value.displayState}; cleaning up dead handle to re-attach fresh...")
        try {
          currentDisplay.stop()
          currentDisplay.close()
        } catch (ignored: Throwable) {}
        this.display = null
        try {
          session.removeDisplay()
        } catch (ignored: Throwable) {}
      }
    }

    Log.i(TAG, "Attempting to attach Display capability to session...")
    val result = try {
      session.addDisplay(DisplayConfiguration())
    } catch (t: Throwable) {
      Log.w(TAG, "Exception calling session.addDisplay (device has no display coordinator): ${t.message}")
      _uiState.update { it.copy(isDisplayCapable = false) }
      return
    }
    result.fold(
        onSuccess = { createdDisplay ->
          Log.i(TAG, "Display capability attached successfully")
          _uiState.update { it.copy(isDisplayCapable = true) }
          display = createdDisplay
          observeDisplay(createdDisplay)
        },
        onFailure = { error, _ ->
          Log.w(TAG, "Failed to attach Display capability: ${error.description}")
          if (error == DeviceSessionError.CAPABILITY_ALREADY_ADDED) {
            Log.w(TAG, "Display capability already attached on device session, removing and re-attaching cleanly...")
            _uiState.update { it.copy(isDisplayCapable = true) }
            viewModelScope.launch {
              try {
                session.removeDisplay()
              } catch (ignored: Throwable) {}
              delay(350)
              session.addDisplay(DisplayConfiguration()).onSuccess { retriedDisplay ->
                Log.i(TAG, "Display capability recovered and attached successfully")
                display = retriedDisplay
                observeDisplay(retriedDisplay)
              }
            }
          } else {
            _uiState.update { it.copy(isDisplayCapable = false) }
          }
        }
    )
  }

  private fun observeDisplay(display: Display) {
    displayStateJob?.cancel()
    displayStateJob = viewModelScope.launch {
      display.state.collect { state ->
        Log.i(TAG, "DisplayState changed to: $state")
        _uiState.update { it.copy(displayState = state) }
        when (state) {
          DisplayState.STARTED -> {
            Log.i(TAG, "Display is STARTED. Rendering glasses UI screen...")
            renderGlassesScreen()
            // Sequentially attach Speech capability once Display is established (avoiding concurrent BLE capability collisions)
            viewModelScope.launch {
              delay(500)
              val s = session
              if (s != null && speech == null && _uiState.value.isSessionActive && _uiState.value.glassesDisplayScreen != GlassesDisplayScreen.SCAN_QR) {
                Log.i(TAG, "Sequentially attaching Speech capability now that Display is active...")
                attachSpeechCapability(s)
              }
            }
          }
          DisplayState.CLOSED -> {
            Log.i(TAG, "Display closed. Resetting display reference.")
            this@CameraViewModel.display = null
            val s = session
            if (!userStoppedSession && s != null && isDisplayCapableDevice(s) && _uiState.value.isSessionActive) {
              Log.i(TAG, "Display closed unexpectedly during active session. Scheduling auto-reattach...")
              viewModelScope.launch {
                delay(1000)
                if (this@CameraViewModel.display == null && !userStoppedSession && session != null && _uiState.value.isSessionActive) {
                  Log.i(TAG, "Auto-reattaching display capability after unexpected close...")
                  attachDisplayCapability(s)
                }
              }
            }
          }
          DisplayState.STOPPED -> {
            Log.i(TAG, "Display is STOPPED (initial or disconnected state)")
            val s = session
            if (!userStoppedSession && s != null && isDisplayCapableDevice(s) && _uiState.value.isSessionActive) {
              Log.i(TAG, "Display is STOPPED during active session. Scheduling auto-recovery...")
              viewModelScope.launch {
                delay(1200)
                if (!userStoppedSession && session != null && _uiState.value.isSessionActive && _uiState.value.displayState != DisplayState.STARTED && _uiState.value.displayState != DisplayState.STARTING) {
                  Log.i(TAG, "Auto-recovering stopped display capability...")
                  attachDisplayCapability(s)
                }
              }
            }
          }
          else -> {}
        }
      }
    }
  }

  /**
   * Declaratively renders glasses screens onto the Meta smart glasses display using FlexBox DSL.
   * Handles:
   * 1. HOME: 3 buttons { Scan QR, View Analytics, Send Money to Friend }
   * 2. SCAN_QR: Live QR viewfinder cue with 'Return to Home' button
   * 3. ANALYTICS: On-device LiteRT financial intelligence with 'Return to Home' button
   * 4. CONTACTS: Contact list for peer transfers with 'Return to Home' button
   * 5. PAYMENT: Dynamic amount keypad, confirmation, and success with 'Return to Home' button
   */
  fun renderGlassesScreen() {
    val activeDisplay = display ?: return
    if (_uiState.value.displayState != DisplayState.STARTED) return

    viewModelScope.launch(Dispatchers.Main) {
      displayRenderMutex.withLock {
        val currentScreen = _uiState.value.glassesDisplayScreen
        val currentPaymentState = _uiState.value.paymentState
        Log.i(TAG, "renderGlassesScreen: sending content for screen=$currentScreen, paymentState=${currentPaymentState::class.simpleName}")

        activeDisplay.sendContent {
          flexBox(direction = Direction.COLUMN, gap = 8) {
            when (currentScreen) {
              GlassesDisplayScreen.HOME -> {
                flexBox(direction = Direction.COLUMN, gap = 2, background = FlexBoxBackground.CARD, padding = 8) {
                  text("Air Wallet", style = TextStyle.HEADING)
                  val activeVault = payPalVaultRepository.vaultStatus.value
                  val railStatus = if (activeVault is PayPalVaultStatus.Vaulted) "1-Tap PayPal Vault Active" else "Fast Pay Active"
                  text(railStatus, style = TextStyle.META, color = TextColor.SECONDARY)
                }

                button(
                    label = "Scan QR Code",
                    style = ButtonStyle.PRIMARY,
                    iconName = IconName.FOUR_CORNER_FRAME,
                    onClick = {
                      Log.i(TAG, "Scan QR clicked on glasses display")
                      viewModelScope.launch(Dispatchers.Main) { onScanQrClicked() }
                    }
                )
                button(
                    label = "Send Money to Friend",
                    style = ButtonStyle.SECONDARY,
                    iconName = IconName.PAPER_AIRPLANE,
                    onClick = {
                      Log.i(TAG, "Send Money to Friend clicked on glasses display")
                      viewModelScope.launch(Dispatchers.Main) { onSendMoneyClicked() }
                    }
                )
                button(
                    label = "Spending Analytics",
                    style = ButtonStyle.SECONDARY,
                    iconName = IconName.META_AI,
                    onClick = {
                      Log.i(TAG, "View Analytics clicked on glasses display")
                      viewModelScope.launch(Dispatchers.Main) { onViewAnalyticsClicked() }
                    }
                )
                button(
                    label = "Split Bill & Invoice",
                    style = ButtonStyle.SECONDARY,
                    iconName = IconName.SLIDERS_HORIZONTAL,
                    onClick = {
                      Log.i(TAG, "Split Bill & Invoice clicked on glasses display")
                      viewModelScope.launch(Dispatchers.Main) { openGlassesBillSplitSelection(0.0) }
                    }
                )
                button(
                    label = "Scan Object (Muse Spark)",
                    style = ButtonStyle.SECONDARY,
                    iconName = IconName.META_AI,
                    onClick = {
                      Log.i(TAG, "Scan Object clicked on glasses display")
                      viewModelScope.launch(Dispatchers.Main) { onScanObjectClicked() }
                    }
                )
                button(
                    label = "Scan Bill (Muse Spark)",
                    style = ButtonStyle.SECONDARY,
                    iconName = IconName.SLIDERS_HORIZONTAL,
                    onClick = {
                      Log.i(TAG, "Scan Bill clicked on glasses display")
                      viewModelScope.launch(Dispatchers.Main) { onScanBillClicked() }
                    }
                )
                button(
                    label = "Exit Wallet",
                    style = ButtonStyle.OUTLINE,
                    onClick = {
                      Log.i(TAG, "Exit Wallet clicked on glasses display")
                      viewModelScope.launch(Dispatchers.Main) { exitWalletDisplaySession() }
                    }
                )
              }
              GlassesDisplayScreen.SCAN_QR -> {
                text("Air Wallet • Scan QR", style = TextStyle.HEADING)
                image(null, redSquareReticleBitmap)
                flexBox(direction = Direction.COLUMN, gap = 2, background = FlexBoxBackground.CARD, padding = 8) {
                  text("Camera is on LEFT frame", style = TextStyle.BODY)
                  text("Align QR with left lens • 1-2 ft", style = TextStyle.META, color = TextColor.SECONDARY)
                }
                button(
                    label = "Return to Home",
                    style = ButtonStyle.OUTLINE,
                    iconName = IconName.ARROW_LEFT,
                    onClick = {
                      Log.i(TAG, "Return to Home clicked on glasses display (SCAN_QR)")
                      viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                    }
                )
              }
              GlassesDisplayScreen.ANALYTICS -> {
                val qaAnswer = _uiState.value.aiQaAnswer
                if (qaAnswer != null) {
                  text("Spending AI Insights", style = TextStyle.HEADING)
                  flexBox(direction = Direction.COLUMN, gap = 4, background = FlexBoxBackground.CARD, padding = 10) {
                    text(qaAnswer.headline, style = TextStyle.BODY)
                    text(qaAnswer.shortAnswer, style = TextStyle.BODY)
                    qaAnswer.metricValue?.let {
                      text("Amount: $it", style = TextStyle.META, color = TextColor.SECONDARY)
                    }
                  }
                  button(
                      label = "View Spending Charts",
                      style = ButtonStyle.SECONDARY,
                      iconName = IconName.LIGHT_BULB,
                      onClick = {
                        Log.i(TAG, "Switching to Charts Overview on glasses")
                        _uiState.update { it.copy(aiQaAnswer = null) }
                        viewModelScope.launch(Dispatchers.Main) { renderGlassesScreen() }
                      }
                  )
                  button(
                      label = "Return to Home",
                      style = ButtonStyle.PRIMARY,
                      iconName = IconName.ARROW_LEFT,
                      onClick = {
                        Log.i(TAG, "Return to Home clicked on glasses display (ANALYTICS_QA)")
                        viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                      }
                  )
                } else {
                  val txns = transactionRepository.transactions.value
                  val bal = transactionRepository.balance.value
                  val analysis = financialAiEngine.analyzeFinances(txns, bal)
                  val mode = _uiState.value.glassesAnalyticsMode

                  if (mode == GlassesAnalyticsMode.PIE_CHART) {
                    text("Category Pie Chart", style = TextStyle.HEADING)
                    val pieBmp = generateGlassesPieChartBitmap(analysis.categoryBreakdown, analysis.totalSpent)
                    image(null, pieBmp)
                    flexBox(direction = Direction.COLUMN, gap = 2, background = FlexBoxBackground.CARD, padding = 6) {
                      text("Top: ${analysis.topCategory} ($${"%.2f".format(analysis.topCategoryAmount)})", style = TextStyle.BODY)
                      val catSummary = analysis.categoryBreakdown.take(2).joinToString(" • ") { "${it.category.split(" ").first()}: $${it.amount.toInt()}" }
                      if (catSummary.isNotBlank()) {
                        text(catSummary, style = TextStyle.META, color = TextColor.SECONDARY)
                      }
                    }
                    button(
                        label = "Show Line Graph",
                        style = ButtonStyle.SECONDARY,
                        iconName = IconName.TWO_ARROWS_CLOCKWISE,
                        onClick = {
                          Log.i(TAG, "Toggling to Line Graph on glasses")
                          toggleGlassesAnalyticsMode()
                        }
                    )
                  } else {
                    text("Weekly Spend Graph", style = TextStyle.HEADING)
                    val lineBmp = generateGlassesLineGraphBitmap(analysis.weeklyTrend, analysis.weeklySpent)
                    image(null, lineBmp)
                    flexBox(direction = Direction.COLUMN, gap = 2, background = FlexBoxBackground.CARD, padding = 6) {
                      text("Week Total: $${"%.2f".format(analysis.weeklySpent)}", style = TextStyle.BODY)
                      text("7-Day Trend • Mon to Sun", style = TextStyle.META, color = TextColor.SECONDARY)
                    }
                    button(
                        label = "Show Pie Chart",
                        style = ButtonStyle.SECONDARY,
                        iconName = IconName.TWO_ARROWS_CLOCKWISE,
                        onClick = {
                          Log.i(TAG, "Toggling to Pie Chart on glasses")
                          toggleGlassesAnalyticsMode()
                        }
                    )
                  }

                  button(
                      label = "Ask Voice Question",
                      style = ButtonStyle.PRIMARY,
                      iconName = IconName.META_AI,
                      onClick = {
                        Log.i(TAG, "Ask Voice Question clicked on glasses display")
                        viewModelScope.launch(Dispatchers.Main) { onAskVoiceQuestionClicked() }
                      }
                  )
                  button(
                      label = "Return to Home",
                      style = ButtonStyle.OUTLINE,
                      iconName = IconName.ARROW_LEFT,
                      onClick = {
                        Log.i(TAG, "Return to Home clicked on glasses display (ANALYTICS)")
                        viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                      }
                  )
                }
              }
              GlassesDisplayScreen.VOICE_QA_LISTENING -> {
                text("Meta AI • Voice QA", style = TextStyle.HEADING)
                flexBox(direction = Direction.COLUMN, gap = 4, background = FlexBoxBackground.CARD, padding = 8) {
                  text("Listening for your question...", style = TextStyle.META, color = TextColor.SECONDARY)
                  val liveTranscript = _uiState.value.liveSpeechTranscription
                  if (liveTranscript.isNotBlank()) {
                    text("\"$liveTranscript\"", style = TextStyle.BODY)
                  } else {
                    text("Say aloud:", style = TextStyle.META, color = TextColor.SECONDARY)
                    text("• \"Spend on food?\"", style = TextStyle.BODY)
                    text("• \"Show line graph\"", style = TextStyle.BODY)
                    text("• \"Highest expense?\"", style = TextStyle.BODY)
                  }
                }
                button(
                    label = "Back to Analytics",
                    style = ButtonStyle.OUTLINE,
                    iconName = IconName.ARROW_LEFT,
                    onClick = {
                      Log.i(TAG, "Back to Analytics clicked on glasses display")
                      viewModelScope.launch(Dispatchers.Main) { dismissVoiceQaListening() }
                    }
                )
              }
              GlassesDisplayScreen.CONTACTS -> {
                text("Send Money • Friends", style = TextStyle.HEADING)
                text("Select contact:", style = TextStyle.META, color = TextColor.SECONDARY)
                defaultFriends.forEach { friend ->
                  button(
                      label = "${friend.name}  •  ${friend.rail}",
                      style = ButtonStyle.SECONDARY,
                      iconName = IconName.PERSON,
                      onClick = {
                        Log.i(TAG, "Friend selected: ${friend.name}")
                        viewModelScope.launch(Dispatchers.Main) { onSendToContactSelected(friend) }
                      }
                  )
                }
                button(
                    label = "Return to Home",
                    style = ButtonStyle.OUTLINE,
                    iconName = IconName.ARROW_LEFT,
                    onClick = {
                      Log.i(TAG, "Return to Home clicked on glasses display (CONTACTS)")
                      viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                    }
                )
              }
              GlassesDisplayScreen.BILL_SPLIT -> {
                text("Air Wallet • Bill Split", style = TextStyle.HEADING)
                val split = _uiState.value.recentBillSplitResult
                if (split != null) {
                  flexBox(direction = Direction.COLUMN, gap = 3, background = FlexBoxBackground.CARD, padding = 8) {
                    text("Total Bill: $${"%.2f".format(split.billTotal)}", style = TextStyle.BODY)
                    text("Each Share: $${"%.2f".format(split.perPersonAmount)}", style = TextStyle.HEADING)
                    text("PayPal Invoices Sent (v2):", style = TextStyle.META, color = TextColor.SECONDARY)
                    split.invoices.forEach { inv ->
                      text("• ${inv.recipientName}: $${"%.2f".format(inv.totalAmount)}", style = TextStyle.META)
                    }
                  }
                  button(
                      label = "Split Another Bill",
                      style = ButtonStyle.PRIMARY,
                      iconName = IconName.SLIDERS_HORIZONTAL,
                      onClick = {
                        viewModelScope.launch(Dispatchers.Main) { openGlassesBillSplitSelection(split.billTotal) }
                      }
                  )
                } else {
                  val amt = _uiState.value.billSplitAmount
                  val selectedIds = _uiState.value.billSplitSelectedFriendIds
                  val count = selectedIds.size + 1
                  val perPerson = if (count > 0 && amt > 0.0) amt / count else 0.0

                  flexBox(direction = Direction.COLUMN, gap = 2, background = FlexBoxBackground.CARD, padding = 8) {
                    if (amt > 0.0) {
                      text("Bill: $${"%.2f".format(amt)} • $count people", style = TextStyle.BODY)
                      text("Each Pays: $${"%.2f".format(perPerson)}", style = TextStyle.HEADING)
                    } else {
                      text("Bill: Enter Amount • $count people", style = TextStyle.BODY)
                      text("Each Pays: --", style = TextStyle.HEADING)
                    }
                    text("Tap +$10/+$25 or say amount to adjust", style = TextStyle.META, color = TextColor.SECONDARY)
                  }

                  flexBox(direction = Direction.ROW, gap = 3, padding = 0) {
                    button(
                        label = "⌨ Keypad Total",
                        style = ButtonStyle.SECONDARY,
                        iconName = IconName.SLIDERS_HORIZONTAL,
                        onClick = {
                          viewModelScope.launch(Dispatchers.Main) { openBillSplitKeypad() }
                        }
                    )
                    button(label = "+$10", style = ButtonStyle.SECONDARY, onClick = {
                      viewModelScope.launch(Dispatchers.Main) { adjustBillSplitAmount(10.0) }
                    })
                    button(label = "+$25", style = ButtonStyle.SECONDARY, onClick = {
                      viewModelScope.launch(Dispatchers.Main) { adjustBillSplitAmount(25.0) }
                    })
                  }

                  defaultFriends.forEach { friend ->
                    val isSelected = selectedIds.contains(friend.id)
                    button(
                        label = "${if (isSelected) "✓ " else "+ "}${friend.name}",
                        style = if (isSelected) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY,
                        iconName = IconName.PERSON,
                        onClick = {
                          viewModelScope.launch(Dispatchers.Main) { toggleBillSplitFriend(friend.id) }
                        }
                    )
                  }

                  if (selectedIds.isNotEmpty() && amt > 0.0) {
                    button(
                        label = "Send Invoices ($${"%.2f".format(perPerson)} ea)",
                        style = ButtonStyle.PRIMARY,
                        iconName = IconName.PAPER_AIRPLANE,
                        onClick = {
                          viewModelScope.launch(Dispatchers.Main) { confirmBillSplit() }
                        }
                    )
                  }
                }
                button(
                    label = "Return to Home",
                    style = ButtonStyle.OUTLINE,
                    iconName = IconName.ARROW_LEFT,
                    onClick = {
                      viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                    }
                )
              }
              GlassesDisplayScreen.BILL_SPLIT_KEYPAD -> {
                text("Air Wallet • Bill Total", style = TextStyle.HEADING)
                val amtStr = keypadAmountString.ifEmpty { "0" }
                flexBox(direction = Direction.COLUMN, gap = 2, background = FlexBoxBackground.CARD, padding = 8) {
                  text("Total Bill: $$amtStr", style = TextStyle.HEADING)
                  text("Tap digits or say amount aloud", style = TextStyle.META, color = TextColor.SECONDARY)
                }

                // Row 1: 1 2 3 4
                flexBox(direction = Direction.ROW, gap = 3) {
                  listOf('1', '2', '3', '4').forEach { digit ->
                    button(label = "$digit", style = ButtonStyle.SECONDARY, onClick = {
                      viewModelScope.launch(Dispatchers.Main) { onKeypadDigit(digit) }
                    })
                  }
                }

                // Row 2: 5 6 7 8
                flexBox(direction = Direction.ROW, gap = 3) {
                  listOf('5', '6', '7', '8').forEach { digit ->
                    button(label = "$digit", style = ButtonStyle.SECONDARY, onClick = {
                      viewModelScope.launch(Dispatchers.Main) { onKeypadDigit(digit) }
                    })
                  }
                }

                // Row 3: 9 0 . ⌫
                flexBox(direction = Direction.ROW, gap = 3) {
                  button(label = "9", style = ButtonStyle.SECONDARY, onClick = { viewModelScope.launch(Dispatchers.Main) { onKeypadDigit('9') } })
                  button(label = "0", style = ButtonStyle.SECONDARY, onClick = { viewModelScope.launch(Dispatchers.Main) { onKeypadDigit('0') } })
                  button(label = ".", style = ButtonStyle.SECONDARY, onClick = { viewModelScope.launch(Dispatchers.Main) { onKeypadDigit('.') } })
                  button(label = "⌫", style = ButtonStyle.SECONDARY, onClick = { viewModelScope.launch(Dispatchers.Main) { onKeypadBackspace() } })
                }

                val parsedAmt = keypadAmountString.toDoubleOrNull() ?: 0.0
                flexBox(direction = Direction.ROW, gap = 4) {
                  button(
                      label = if (parsedAmt > 0) "Set Total ($$amtStr)" else "Set Total",
                      style = ButtonStyle.PRIMARY,
                      iconName = IconName.CHECKMARK,
                      onClick = {
                        viewModelScope.launch(Dispatchers.Main) { submitBillSplitKeypad() }
                      }
                  )
                  button(
                      label = "Cancel",
                      style = ButtonStyle.OUTLINE,
                      iconName = IconName.ARROW_LEFT,
                      onClick = {
                        viewModelScope.launch(Dispatchers.Main) { cancelBillSplitKeypad() }
                      }
                  )
                }
              }
              GlassesDisplayScreen.PAYMENT -> {
                val currentDetails = when (currentPaymentState) {
                  is PaymentState.EnterAmount -> currentPaymentState.details
                  is PaymentState.ConfirmPayment -> currentPaymentState.details
                  is PaymentState.Processing -> currentPaymentState.details
                  is PaymentState.Success -> currentPaymentState.details
                  else -> null
                }
                val isPayPal = currentDetails?.paymentMethod == PaymentMethod.PAYPAL_VAULT
                val headerTitle = if (isPayPal) "Air Wallet • PayPal Vault" else "Air Wallet • UPI Instant"
                text(headerTitle, style = TextStyle.HEADING)

                when (currentPaymentState) {
                  is PaymentState.EnterAmount -> {
                    val d = currentPaymentState.details
                    val curr = if (d.currency == "USD") "$" else "₹"
                    val currentAmountVal = keypadAmountString.toDoubleOrNull() ?: (d.amount ?: 0.0)
                    val displayAmt = if (keypadAmountString.isNotEmpty()) "$curr$keypadAmountString" else if (currentAmountVal > 0) "$curr${"%.2f".format(currentAmountVal)}" else "$curr 0"

                    flexBox(direction = Direction.COLUMN, gap = 2, background = FlexBoxBackground.CARD, padding = 8) {
                      text("To: ${d.payeeName}", style = TextStyle.BODY)
                      text("Amount: $displayAmt", style = TextStyle.HEADING)
                      text(if (isPayPal) "PayPal Vault • Tap digits or say amount" else "UPI Instant • Tap digits or say amount", style = TextStyle.META, color = TextColor.SECONDARY)
                    }

                    // Keypad Row 1: 1 2 3 4
                    flexBox(direction = Direction.ROW, gap = 3) {
                      listOf('1', '2', '3', '4').forEach { digit ->
                        button(label = "$digit", style = ButtonStyle.SECONDARY, onClick = {
                          viewModelScope.launch(Dispatchers.Main) { onKeypadDigit(digit) }
                        })
                      }
                    }

                    // Keypad Row 2: 5 6 7 8
                    flexBox(direction = Direction.ROW, gap = 3) {
                      listOf('5', '6', '7', '8').forEach { digit ->
                        button(label = "$digit", style = ButtonStyle.SECONDARY, onClick = {
                          viewModelScope.launch(Dispatchers.Main) { onKeypadDigit(digit) }
                        })
                      }
                    }

                    // Keypad Row 3: 9 0 . ⌫
                    flexBox(direction = Direction.ROW, gap = 3) {
                      button(label = "9", style = ButtonStyle.SECONDARY, onClick = { viewModelScope.launch(Dispatchers.Main) { onKeypadDigit('9') } })
                      button(label = "0", style = ButtonStyle.SECONDARY, onClick = { viewModelScope.launch(Dispatchers.Main) { onKeypadDigit('0') } })
                      button(label = ".", style = ButtonStyle.SECONDARY, onClick = { viewModelScope.launch(Dispatchers.Main) { onKeypadDigit('.') } })
                      button(label = "⌫", style = ButtonStyle.SECONDARY, onClick = { viewModelScope.launch(Dispatchers.Main) { onKeypadBackspace() } })
                    }

                    // Row 4: Confirm & Return
                    flexBox(direction = Direction.ROW, gap = 4) {
                      button(
                          label = if (currentAmountVal > 0) "Pay $displayAmt" else "Enter Amount",
                          style = ButtonStyle.PRIMARY,
                          iconName = IconName.CHECKMARK,
                          onClick = {
                            if (currentAmountVal > 0) {
                              viewModelScope.launch(Dispatchers.Main) {
                                setPaymentAmount(currentAmountVal)
                                confirmPayment()
                              }
                            }
                          }
                      )
                      button(label = "Cancel", style = ButtonStyle.OUTLINE, iconName = IconName.ARROW_LEFT, onClick = {
                        viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                      })
                    }
                  }
                  is PaymentState.ConfirmPayment -> {
                    val d = currentPaymentState.details
                    val curr = if (d.currency == "USD") "$" else "₹"
                    val amt = d.amount ?: (if (d.currency == "USD") 15.0 else 100.0)
                    val amtStr = "%.2f".format(amt)
                    val breakdown = d.itemizedBreakdown ?: payPalPaymentService.generateSmartItemizedBreakdown(d.payeeName, amt)

                    flexBox(direction = Direction.COLUMN, gap = 3, background = FlexBoxBackground.CARD, padding = 8) {
                      text("To: ${d.payeeName}", style = TextStyle.BODY)
                      text("Total: $curr $amtStr", style = TextStyle.HEADING)
                      if (d.paymentMethod == PaymentMethod.PAYPAL_VAULT) {
                        text("Source: PayPal Vault (1-Tap)", style = TextStyle.META, color = TextColor.SECONDARY)
                        val itemsSnippet = breakdown.items.take(2).joinToString(" • ") { "${it.quantity}x ${it.name}" }
                        if (itemsSnippet.isNotBlank()) {
                          text("Items: $itemsSnippet", style = TextStyle.META, color = TextColor.SECONDARY)
                        }
                      } else {
                        text("Source: UPI Instant Account", style = TextStyle.META, color = TextColor.SECONDARY)
                      }
                      text("Tap Pay or say 'Pay' or 'Yes'", style = TextStyle.META, color = TextColor.SECONDARY)
                    }

                    button(label = "Pay $curr $amtStr", style = ButtonStyle.PRIMARY, iconName = IconName.CHECKMARK, onClick = {
                      Log.i(TAG, "Pay button clicked on glasses display")
                      viewModelScope.launch(Dispatchers.Main) { confirmPayment() }
                    })
                    button(label = "Enter / Change Amount", style = ButtonStyle.SECONDARY, iconName = IconName.PENCIL, onClick = {
                      Log.i(TAG, "Enter/Change Amount clicked on glasses display")
                      viewModelScope.launch(Dispatchers.Main) { editPaymentAmount(d) }
                    })
                    button(label = "Return to Home", style = ButtonStyle.OUTLINE, iconName = IconName.ARROW_LEFT, onClick = {
                      Log.i(TAG, "Return to Home clicked on glasses display (ConfirmPayment)")
                      viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                    })
                  }
                  is PaymentState.Processing -> {
                    val d = currentPaymentState.details
                    flexBox(direction = Direction.COLUMN, gap = 4, background = FlexBoxBackground.CARD, padding = 10) {
                      text("Processing Payment...", style = TextStyle.HEADING)
                      text("To: ${d.payeeName}", style = TextStyle.BODY)
                      val methodDesc = if (d.paymentMethod == PaymentMethod.PAYPAL_VAULT) "Charging PayPal Vault..." else "Authorizing UPI..."
                      text(methodDesc, style = TextStyle.META, color = TextColor.SECONDARY)
                    }
                  }
                  is PaymentState.Success -> {
                    val d = currentPaymentState.details
                    val curr = if (d.currency == "USD") "$" else "₹"
                    val paidAmt = d.amount ?: (if (d.currency == "USD") 15.0 else 100.0)
                    val amtStr = "%.2f".format(paidAmt)
                    val railText = if (d.paymentMethod == PaymentMethod.PAYPAL_VAULT) "via PayPal Orders v2" else "via UPI"

                    flexBox(direction = Direction.COLUMN, gap = 3, background = FlexBoxBackground.CARD, padding = 8) {
                      text("Payment Successful!", style = TextStyle.HEADING)
                      text("Paid $curr $amtStr to ${d.payeeName}", style = TextStyle.BODY)
                      text(railText, style = TextStyle.META, color = TextColor.SECONDARY)
                      val receiptSnippet = currentPaymentState.itemizedBreakdown?.items?.take(2)?.joinToString(" • ") { "${it.quantity}x ${it.name}" }
                      if (!receiptSnippet.isNullOrBlank()) {
                        text("Receipt: $receiptSnippet", style = TextStyle.META, color = TextColor.SECONDARY)
                      }
                      text("Tap Split to divide this bill with friends", style = TextStyle.META, color = TextColor.SECONDARY)
                    }

                    button(label = "Split Bill with Friends", style = ButtonStyle.PRIMARY, iconName = IconName.SLIDERS_HORIZONTAL, onClick = {
                      Log.i(TAG, "Split Bill pressed on glasses display after payment success")
                      viewModelScope.launch(Dispatchers.Main) {
                        dismissPayment()
                        openGlassesBillSplitSelection(paidAmt)
                      }
                    })
                    button(label = "Rescan QR", style = ButtonStyle.SECONDARY, iconName = IconName.FOUR_CORNER_FRAME, onClick = {
                      Log.i(TAG, "Rescan QR button pressed on glasses display")
                      viewModelScope.launch(Dispatchers.Main) { onScanQrClicked() }
                    })
                    button(label = "Return to Home", style = ButtonStyle.OUTLINE, iconName = IconName.HOUSE, onClick = {
                      Log.i(TAG, "Return to Home pressed on glasses display (Success)")
                      viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                    })
                  }
                  is PaymentState.Error -> {
                    flexBox(direction = Direction.COLUMN, gap = 4, background = FlexBoxBackground.CARD, padding = 10) {
                      text("Payment Failed", style = TextStyle.HEADING)
                      text(currentPaymentState.message, style = TextStyle.BODY)
                    }
                    button(label = "Return to Home", style = ButtonStyle.PRIMARY, iconName = IconName.ARROW_LEFT, onClick = {
                      Log.i(TAG, "Return to Home clicked on glasses display (Error)")
                      viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                    })
                  }
                  else -> {
                    button(label = "Return to Home", style = ButtonStyle.PRIMARY, iconName = IconName.ARROW_LEFT, onClick = {
                      viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                    })
                  }
                }
              }
              GlassesDisplayScreen.SCAN_OBJECT -> {
                text("Muse Spark • Visual Shop", style = TextStyle.HEADING)
                val objResult = _uiState.value.museSparkObjectResult
                val isLoading = _uiState.value.isMuseSparkLoading

                if (isLoading) {
                  flexBox(direction = Direction.COLUMN, gap = 4, background = FlexBoxBackground.CARD, padding = 10) {
                    text("Scanning product...", style = TextStyle.BODY)
                    text("Muse Spark Multimodal Visual AI", style = TextStyle.META, color = TextColor.SECONDARY)
                  }
                  button(
                      label = "Cancel",
                      style = ButtonStyle.OUTLINE,
                      iconName = IconName.ARROW_LEFT,
                      onClick = {
                        viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                      }
                  )
                } else if (objResult != null) {
                  flexBox(direction = Direction.COLUMN, gap = 3, background = FlexBoxBackground.CARD, padding = 8) {
                    text(objResult.productName, style = TextStyle.BODY)
                    text("Best Price: $${"%.2f".format(objResult.estimatedPrice)}", style = TextStyle.HEADING)
                    text("Merchant: ${objResult.merchantName} • PayPal Active", style = TextStyle.META, color = TextColor.SECONDARY)
                  }
                  button(
                      label = "1-Tap Pay $${"%.2f".format(objResult.estimatedPrice)} (PayPal)",
                      style = ButtonStyle.PRIMARY,
                      iconName = IconName.CHECKMARK,
                      onClick = {
                        Log.i(TAG, "1-Tap Pay via PayPal clicked for Muse Spark object")
                        viewModelScope.launch(Dispatchers.Main) { executeMuseSparkObjectPurchase() }
                      }
                  )
                  button(
                      label = "Rescan Object",
                      style = ButtonStyle.SECONDARY,
                      iconName = IconName.META_AI,
                      onClick = {
                        viewModelScope.launch(Dispatchers.Main) { onScanObjectClicked() }
                      }
                  )
                  button(
                      label = "Return to Home",
                      style = ButtonStyle.OUTLINE,
                      iconName = IconName.ARROW_LEFT,
                      onClick = {
                        viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                      }
                  )
                } else {
                  button(
                      label = "Return to Home",
                      style = ButtonStyle.PRIMARY,
                      iconName = IconName.ARROW_LEFT,
                      onClick = {
                        viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                      }
                  )
                }
              }
              GlassesDisplayScreen.SCAN_BILL -> {
                text("Muse Spark • Bill Split", style = TextStyle.HEADING)
                val billResult = _uiState.value.museSparkBillResult
                val isLoading = _uiState.value.isMuseSparkLoading

                if (isLoading) {
                  flexBox(direction = Direction.COLUMN, gap = 4, background = FlexBoxBackground.CARD, padding = 10) {
                    text("Parsing receipt line items...", style = TextStyle.BODY)
                    text("Muse Spark Multimodal OCR Engine", style = TextStyle.META, color = TextColor.SECONDARY)
                  }
                  button(
                      label = "Cancel",
                      style = ButtonStyle.OUTLINE,
                      iconName = IconName.ARROW_LEFT,
                      onClick = {
                        viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                      }
                  )
                } else if (billResult != null) {
                  flexBox(direction = Direction.COLUMN, gap = 3, background = FlexBoxBackground.CARD, padding = 8) {
                    text(billResult.merchantName, style = TextStyle.BODY)
                    text("Total: $${"%.2f".format(billResult.grandTotal)}", style = TextStyle.HEADING)
                    val itemsSummary = "${billResult.items.size} items • Tax $${"%.2f".format(billResult.taxTotal)} • Tip $${"%.2f".format(billResult.tip)}"
                    text(itemsSummary, style = TextStyle.META, color = TextColor.SECONDARY)
                  }
                  button(
                      label = "Split with Friends (PayPal)",
                      style = ButtonStyle.PRIMARY,
                      iconName = IconName.SLIDERS_HORIZONTAL,
                      onClick = {
                        Log.i(TAG, "Split with Friends clicked for Muse Spark bill")
                        viewModelScope.launch(Dispatchers.Main) { executeMuseSparkBillSplit() }
                      }
                  )
                  button(
                      label = "Rescan Bill",
                      style = ButtonStyle.SECONDARY,
                      iconName = IconName.TWO_ARROWS_CLOCKWISE,
                      onClick = {
                        viewModelScope.launch(Dispatchers.Main) { onScanBillClicked() }
                      }
                  )
                  button(
                      label = "Return to Home",
                      style = ButtonStyle.OUTLINE,
                      iconName = IconName.ARROW_LEFT,
                      onClick = {
                        viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                      }
                  )
                } else {
                  button(
                      label = "Return to Home",
                      style = ButtonStyle.PRIMARY,
                      iconName = IconName.ARROW_LEFT,
                      onClick = {
                        viewModelScope.launch(Dispatchers.Main) { returnToGlassesHome() }
                      }
                  )
                }
              }
            }
          }
        }.fold(
            onSuccess = { Log.i(TAG, "Glasses content ($currentScreen) sent to display successfully") },
            onFailure = { error, _ -> Log.w(TAG, "Display sendContent failure: ${error.description}") }
        )
      }
    }
  }

  private fun renderPaymentContent() {
    renderGlassesScreen()
  }

  fun onKeypadDigit(digit: Char) {
    if (keypadAmountString.length >= 7) return // Prevent overflow (> 999,999)
    if (keypadAmountString.isEmpty() && digit == '0') return // Prevent leading zero
    if (digit == '.' && (keypadAmountString.contains('.') || keypadAmountString.isEmpty())) return // Prevent invalid decimals
    keypadAmountString += digit
    val parsed = keypadAmountString.toDoubleOrNull()
    _uiState.update { state ->
      val current = state.paymentState
      val updatedState = if (current is PaymentState.EnterAmount) {
        PaymentState.EnterAmount(current.details.copy(amount = parsed))
      } else {
        state.paymentState
      }
      state.copy(keypadAmount = keypadAmountString, paymentState = updatedState)
    }
    renderGlassesScreen()
  }

  fun onKeypadBackspace() {
    if (keypadAmountString.isNotEmpty()) {
      keypadAmountString = keypadAmountString.dropLast(1)
      val parsed = keypadAmountString.toDoubleOrNull()
      _uiState.update { state ->
        val current = state.paymentState
        val updatedState = if (current is PaymentState.EnterAmount) {
          PaymentState.EnterAmount(current.details.copy(amount = parsed))
        } else {
          state.paymentState
        }
        state.copy(keypadAmount = keypadAmountString, paymentState = updatedState)
      }
      renderGlassesScreen()
    }
  }

  fun onKeypadClear() {
    keypadAmountString = ""
    _uiState.update { state ->
      val current = state.paymentState
      val updatedState = if (current is PaymentState.EnterAmount) {
        PaymentState.EnterAmount(current.details.copy(amount = null))
      } else {
        state.paymentState
      }
      state.copy(keypadAmount = "", paymentState = updatedState)
    }
    renderGlassesScreen()
  }

  fun onKeypadSubmit() {
    val currentPayment = _uiState.value.paymentState
    val defaultAmt = if (currentPayment is PaymentState.EnterAmount && currentPayment.details.currency == "USD") 15.0 else 100.0
    val parsed = keypadAmountString.toDoubleOrNull() ?: defaultAmt
    setPaymentAmount(parsed)
    confirmPayment()
  }

  fun setPaymentAmount(amount: Double) {
    val currentState = _uiState.value.paymentState
    if (currentState is PaymentState.EnterAmount) {
      val updated = currentState.details.copy(amount = amount)
      _uiState.update { it.copy(paymentState = PaymentState.ConfirmPayment(updated)) }
      renderPaymentContent()
      promptForConfirmation(updated)
    } else if (currentState is PaymentState.ConfirmPayment) {
      val updated = currentState.details.copy(amount = amount)
      _uiState.update { it.copy(paymentState = PaymentState.ConfirmPayment(updated)) }
      renderPaymentContent()
      promptForConfirmation(updated)
    }
  }

  fun editPaymentAmount(details: PaymentDetails) {
    keypadAmountString = ""
    _uiState.update {
      it.copy(
          glassesDisplayScreen = GlassesDisplayScreen.PAYMENT,
          paymentState = PaymentState.EnterAmount(details.copy(amount = null)),
          keypadAmount = ""
      )
    }
    renderGlassesScreen()
    val currWord = if (details.currency == "USD") "dollars" else "rupees"
    if (isDisplayCapableDevice()) {
      voicePrompter.speak("Enter amount to pay ${details.payeeName}.")
    } else {
      voicePrompter.speak("Say the amount to pay ${details.payeeName}, for example 15 $currWord.")
    }
  }

  private fun promptForConfirmation(details: PaymentDetails) {
    startSpeechListening(forceRestart = false)
    val amountFormatted = details.amount?.let { "%.2f".format(it) } ?: ""
    val currencyWord = if (details.currency == "USD") "dollars" else "rupees"
    val viaText = if (details.paymentMethod == PaymentMethod.PAYPAL_VAULT) "via PayPal" else "via UPI"
    if (!isDisplayCapableDevice()) {
      voicePrompter.speak("Pay $amountFormatted $currencyWord to ${details.payeeName} $viaText. Say Yes to confirm, or say Cancel.")
    }
    initSpeechConfirmation()
  }

  private fun initSpeechConfirmation() {
    speechListener?.stopListening()
    speechListener = SpeechConfirmationListener(
        context = getApplication(),
        onConfirm = {
          viewModelScope.launch(Dispatchers.Main) {
            confirmPayment()
          }
        },
        onCancel = {
          viewModelScope.launch(Dispatchers.Main) {
            dismissPayment()
          }
        }
    )
    speechListener?.startListening()
  }

  fun confirmPayment() {
    speechListener?.stopListening()
    val currentState = _uiState.value.paymentState
    val details = when (currentState) {
      is PaymentState.ConfirmPayment -> currentState.details
      is PaymentState.EnterAmount -> {
        val fallbackAmt = if (currentState.details.currency == "USD") 15.0 else 100.0
        currentState.details.copy(amount = fallbackAmt)
      }
      else -> return
    }
    _uiState.update { it.copy(paymentState = PaymentState.Processing(details)) }
    renderPaymentContent()
    voicePrompter.speak("Processing payment...")

    viewModelScope.launch {
      val isPayPal = details.paymentMethod == PaymentMethod.PAYPAL_VAULT

      if (isPayPal) {
        val amount = details.amount ?: 15.0
        val result = payPalPaymentService.executePayment(
            payee = details.payeeVpa,
            amount = amount,
            currency = details.currency,
            note = details.note,
        )
        result.fold(
            onSuccess = { res ->
              val itemsSummary = res.itemizedBreakdown?.items?.joinToString(" • ") { "${it.quantity}x ${it.name} ($${"%.2f".format(it.unitAmount)})" } ?: ""
              _uiState.update {
                it.copy(
                    paymentState = PaymentState.Success(
                        details = details,
                        transactionId = res.transactionId,
                        timestamp = res.timestamp,
                        fundingSource = res.fundingSource,
                        itemizedBreakdown = res.itemizedBreakdown,
                    )
                )
              }
              transactionRepository.addTransaction(
                  WalletTransaction(
                      id = res.transactionId,
                      merchant = details.payeeName,
                      rail = "PayPal",
                      amount = amount,
                      timestamp = res.timestamp,
                      status = "SUCCESS",
                      fundingSource = res.fundingSource,
                      note = details.note,
                      itemizedSummary = itemsSummary,
                      referenceId = res.transactionId,
                  )
              )
              renderPaymentContent()
              val amountStr = "%.2f".format(amount)
              val currWord = if (details.currency == "USD") "dollars" else details.currency
              if (isDisplayCapableDevice()) {
                voicePrompter.speak("PayPal payment of $amountStr $currWord to ${details.payeeName} is successful. Tap Split to divide this with friends, or say Done.")
              } else {
                voicePrompter.speak("PayPal payment of $amountStr $currWord to ${details.payeeName} is successful. Say 'Split' to divide with friends, or say 'Done'.")
                startSpeechListening(forceRestart = true)
              }
            },
            onFailure = { err ->
              _uiState.update {
                it.copy(paymentState = PaymentState.Error(err.message ?: "PayPal transaction failed"))
              }
              renderPaymentContent()
              voicePrompter.speak("PayPal payment failed. ${err.message ?: ""}")
            }
        )
      } else {
        // UPI Rail processing
        delay(1200)
        val txnId = "UPI/" + System.currentTimeMillis() + "/" + (1000..9999).random()
        val now = System.currentTimeMillis()
        val upiAmount = details.amount ?: 100.0
        _uiState.update {
          it.copy(
              paymentState = PaymentState.Success(
                  details = details,
                  transactionId = txnId,
                  timestamp = now,
                  fundingSource = "Bank Account via UPI",
              )
          )
        }
        transactionRepository.addTransaction(
            WalletTransaction(
                id = txnId,
                merchant = details.payeeName,
                rail = "UPI",
                amount = upiAmount,
                timestamp = now,
                status = "SUCCESS",
                fundingSource = "Bank Account via UPI",
                note = details.note,
            )
        )
        renderPaymentContent()
        val amountStr = "%.2f".format(upiAmount)
        if (isDisplayCapableDevice()) {
          voicePrompter.speak("Payment of ${details.currency} $amountStr to ${details.payeeName} is successful. Tap Split to divide with friends, or say Done.")
        } else {
          voicePrompter.speak("Payment of ${details.currency} $amountStr to ${details.payeeName} is successful. Say 'Split' to divide with friends, or say 'Done'.")
          startSpeechListening(forceRestart = true)
        }
      }

      // Keep success screen visible for user to tap Rescan QR; fallback after 25s
      autoDismissJob?.cancel()
      autoDismissJob = viewModelScope.launch {
        delay(25000)
        if (_uiState.value.paymentState is PaymentState.Success) {
          Log.i(TAG, "Auto-dismissing success screen after 25s timeout")
          dismissPayment()
        }
      }
    }
  }

  private fun cleanupDisplay() {
    displayStateJob?.cancel()
    displayStateJob = null
    val currentSession = session
    val currentDisplay = display
    display = null

    if (currentDisplay != null) {
      try {
        currentDisplay.stop()
      } catch (e: Exception) {
        Log.w(TAG, "Error stopping display: ${e.message}")
      }
      try {
        currentDisplay.close()
      } catch (e: Exception) {
        Log.w(TAG, "Error closing display: ${e.message}")
      }
    }

    if (currentSession != null && currentDisplay != null && isDisplayCapableDevice(currentSession)) {
      try {
        currentSession.removeDisplay().onFailure { error, _ ->
          Log.w(TAG, "removeDisplay warning: ${error.description}")
        }
      } catch (e: Exception) {
        Log.w(TAG, "Error removing display from session: ${e.message}")
      }
    }

    _uiState.update { it.copy(displayState = DisplayState.STOPPED) }
  }

  fun dismissPayment() {
    returnToGlassesHome()
  }

  fun recordManualTransaction(merchant: String, rail: String, amount: Double, note: String = "") {
    val id = (if (rail == "PayPal") "PP-" else "UPI-") + System.currentTimeMillis()
    transactionRepository.addTransaction(
        WalletTransaction(
            id = id,
            merchant = merchant,
            rail = rail,
            amount = amount,
            timestamp = System.currentTimeMillis(),
            status = "SUCCESS",
            fundingSource = if (rail == "PayPal") "PayPal Vault" else "UPI Linked Account",
            note = note,
        )
    )
  }

  fun endAllSessions() {
    autoDismissJob?.cancel()
    autoDismissJob = null
    stopSpeechListening()
    speechListener?.stopListening()
    keypadAmountString = ""
    _uiState.update { it.copy(paymentState = PaymentState.Idle, keypadAmount = "") }

    viewModelScope.launch {
      Log.i(TAG, "Done pressed: ending ALL sessions (Display + Camera Stream + DeviceSession)...")
      // 1. Clean up display capability on the glasses
      cleanupDisplay()

      // 2. Stop QR scanning loop
      stopQrScanningLoop()

      // 3. Mark session stopped by user so auto-start in init doesn't restart it
      userStoppedSession = true
      userStoppedStream = true

      // 4. Release camera stream resources
      clearStreamResources()

      // 5. End the device session
      val activeSession = session
      if (activeSession != null) {
        try {
          _uiState.update { it.copy(sessionState = DeviceSessionState.STOPPING) }
          activeSession.stop()
        } catch (e: Exception) {
          Log.w(TAG, "Error stopping device session: ${e.message}")
        }
      }
      cleanupSession()
      Log.i(TAG, "All sessions ended successfully.")
    }
  }

  override fun onCleared() {
    super.onCleared()
    stopSpeechListening()
    
    stopQrScanningLoop()
    speechListener?.stopListening()
    voicePrompter.shutdown()
    clearStreamResources()
    cleanupDisplay()
    session?.stop()
    cleanupSession()
    financialAiEngine.close()
    audioInputHandler.cleanup()
    videoRecorder.close()
  }

  class Factory(
      private val application: Application,
      private val wearablesViewModel: WearablesViewModel,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
        @Suppress("UNCHECKED_CAST")
        return CameraViewModel(application, wearablesViewModel) as T
      }
      throw IllegalArgumentException("Unknown ViewModel class")
    }
  }
}
