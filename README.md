# AirWallet
### Ambient Agentic Commerce on Meta Ray-Ban Smart Glasses with PayPal & On-Device AI

[![PayPal Developer](https://img.shields.io/badge/PayPal-Orders_v2_|_Vault_|_Invoicing_|_Payouts-003087?style=for-the-badge&logo=paypal&logoColor=white)](https://developer.paypal.com)
[![Google Gemini 2.0 Flash](https://img.shields.io/badge/Google_Gemini_2.0_Flash-Multimodal_Visual_AI-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://ai.google.dev)
[![Google LiteRT](https://img.shields.io/badge/Google_LiteRT-On--Device_AI_(TFLite)-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://ai.google.dev/edge/litert)
[![Meta Wearables](https://img.shields.io/badge/Meta_Wearables_DAT-Display_HUD_|_Camera_|_Speech-0668E1?style=for-the-badge&logo=meta&logoColor=white)](https://wearables.developer.meta.com)
[![Android](https://img.shields.io/badge/Android-Jetpack_Compose-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Download APK](https://img.shields.io/badge/Download_APK-v1.0.0-blue?style=for-the-badge&logo=android&logoColor=white)](https://github.com/vyu-xr/AirWallet/releases/tag/v1.0.0)

---

## Overview

**AirWallet** transforms Meta Ray-Ban Display smart glasses into an **ambient, agentic commerce terminal**. By combining **PayPal's developer suite** (Orders v2 with Vaulting, Invoicing v2, and Payouts v1) with **Google Gemini 2.0 Flash multimodal visual AI**, **on-device Google LiteRT financial intelligence**, and the **Meta Wearables DAT (Device Access Toolkit)**, AirWallet enables hands-free, glance-and-tap transactions in physical environments.

Wearers can look at merchant QR codes or physical products, inspect details directly on the glasses' waveguide HUD, complete 1-tap vaulted payments via PayPal credentials, split dining bills with live PayPal invoices, query on-device financial intelligence via voice, and disburse peer-to-peer payouts.

---

## System Architecture

![AirWallet Architecture](docs/architecture.jpg)

---

## Active Capabilities & Architecture Breakdown

### 1. Glance-to-Pay with PayPal Vault (Orders v2)
- **High-Speed QR Decoding**: Decodes merchant QR codes (`paypal.me`, PayPal Managed Merchant QRs, or point-of-sale barcodes) via low-latency camera capture and Google ML Kit.
- **1-Tap Vaulted Capture**: Leverages PayPal Vault to capture payments instantly (`POST /v2/checkout/orders` with vaulted credentials) without requiring phone unlock, app switching, or web redirects.
- **Waveguide Optical HUD**: Renders real-time transaction confirmation, merchant details, amount, and payment status onto the smart glasses display using declarative FlexBox DSL.

### 2. Multimodal Visual AI (Google Gemini 2.0 Flash)
- **Visual Product Search & 1-Tap Checkout**: Point the glasses camera at any physical product. Google Gemini 2.0 Flash multimodal vision identifies the product name, estimated price, and merchant, instantly populating a 1-tap PayPal purchase option on both the glasses HUD and the companion app.
- **Optical Receipt Itemization**: Point the glasses at any paper dining receipt or retail invoice. Gemini Flash parses merchant name, line items, tax, and grand total, preparing an automated multi-party split dispatched through PayPal Invoicing v2.
- **Build-Time Key Injection & Anycast DNS Resilience**: Supports build-time `GEMINI_API_KEY` injection via `local.properties` (and runtime configuration in Settings), paired with OkHttp Google Anycast DNS fallback (`172.217.115.4`, `142.250.193.138`, `142.250.193.10`) so network requests succeed uninterrupted alongside active smart glasses Bluetooth streaming.

### 3. On-Device Financial Intelligence (Google LiteRT)
- **Private Edge Intelligence**: Employs an on-device TensorFlow Lite model (`financial_analytics.tflite`) with XNNPACK acceleration to categorize real-time spending, calculate budget health scores, and forecast expenditure trends.
- **Hands-Free Financial Voice Q&A**: Wearers can ask analytical financial questions while wearing the glasses (e.g., *"What was my highest expense this week?"*, *"How much did I spend on food today?"*, *"What is my burn rate?"*). The engine processes the local transaction ledger on-device and delivers immediate voice responses (TTS) and HUD visual cards.

### 4. Group Bill Splitting & Invoicing (PayPal Invoicing v2)
- **Automated Invoice Synthesis**: Generates itemized line items and dispatches live PayPal Invoices (`POST /v2/invoicing/invoices` and `POST /v2/invoicing/invoices/{id}/send`) to dining companions via email or handle.
- **Dynamic Group Allocation**: Select dining companions from the glasses HUD or mobile UI, divide costs equally or by custom amounts, and dispatch payment request links.

### 5. Peer Payouts (PayPal Payouts v1)
- **Hands-Free Disbursements**: Disburse peer-to-peer payments via PayPal Payouts v1 (`POST /v1/payments/payouts`) directly from the contact list or via voice command.

### 6. Meta Ray-Ban Display Integration (DAT SDK)
- **Waveguide Display**: Dynamic screens (Home, QR Scanner, Visual Product Search, Receipt Split, Contacts, Keypad, Analytics) rendered via `Display.sendContent` with declarative FlexBox DSL.
- **Camera Stream & Photo Capture**: Low-latency video stream with on-demand high-resolution photo capture for visual analysis.
- **Speech Recognition & Prompts**: Dual-layer voice feedback via on-device ASR and text-to-speech voice guidance.

---

## Connecting to Meta Smart Glasses & Developer Mode Setup

Follow these steps to configure your Meta Ray-Ban Display smart glasses, enable Developer Mode, and connect them to AirWallet:

### Step 1: Pair Glasses with the Meta Companion App
1. Ensure your glasses software and companion app are updated to the latest supported versions.
2. Put the glasses into pairing mode by holding the button on the back of the charging case with the glasses seated inside until the case LED pulses blue.
3. Open the **Meta View** (or **Meta AI**) app on your mobile device and complete initial Bluetooth pairing.

### Step 2: Enable Developer Mode in the Meta Companion App
Developer Mode allows third-party applications integrated with the Meta Wearables Device Access Toolkit (DAT) SDK to register, stream camera data, and render onto the waveguide display without requiring submission for Meta App Review.

1. In the **Meta View** (or **Meta AI**) app, tap the **Settings** gear icon.
2. Scroll to **App Info** (or **About**).
3. Locate the **App Version** number and **tap it 5 times consecutively**.
4. A hidden **Developer Mode** toggle will appear on the screen.
5. Switch the **Developer Mode** toggle to **ON** and tap **Enable** to confirm.
6. The app is now ready to authorize DAT client applications under **Settings** > **App connections** > **Developer mode apps**.

### Step 3: Connect to Glasses from the AirWallet App
1. Launch **AirWallet** on your Android phone.
2. Grant the requested Android permissions when prompted (Bluetooth, Bluetooth Connect, Camera, Microphone).
3. If this is your first time launching, tap the connection chip or **Start Session**:
   - AirWallet automatically calls `Wearables.startRegistration(activity)`.
   - A system authorization screen from Meta View will appear asking to approve the connection.
   - Tap **Approve / Allow**.
4. Once registered, AirWallet automatically uses `Wearables.createSession(AutoDeviceSelector())` to find your paired smart glasses:
   - The device status indicator changes to **Connected** (`Meta RB Display 005R`).
   - AirWallet starts the session (`session.start()`).
   - Upon reaching `DeviceSessionState.STARTED`, AirWallet attaches the Display capability (`session.addDisplay(DisplayConfiguration())`).
5. Put on your glasses: the green waveguide HUD will illuminate displaying the **AirWallet Home Screen**:
   - **Scan QR Code** (starts 15 FPS camera stream with instant QR payment capture)
   - **Send Money to Friend** (peer payouts via PayPal Payouts v1)
   - **Spending Analytics** (on-device LiteRT financial intelligence)
   - **Scan Product** (multimodal visual product search and 1-tap checkout)
   - **Scan Paper Bill** (receipt OCR and group bill split)
6. Speech recognition attaches sequentially, enabling hands-free voice Q&A and audio prompts through the glasses speakers.

---

## Tech Stack & SDKs Utilized

| Layer | Technology | Implementation Details |
| :--- | :--- | :--- |
| **Payments** | **PayPal Developer Platform** | Orders v2, Vault API, Invoicing v2, Payouts v1, Sandbox REST endpoints |
| **Visual AI** | **Google Gemini 2.0 Flash** | Multimodal visual product search, optical receipt itemization, OkHttp 4.12 client |
| **Edge Intelligence** | **Google LiteRT (TFLite)** | On-device model (`financial_analytics.tflite`), XNNPACK delegate, local NLP |
| **Barcode Vision** | **Google ML Kit** | Barcode scanning engine with cold-start pre-warming |
| **Wearable Toolkit** | **Meta Wearables DAT SDK** | Display (Waveguide FlexBox DSL), Camera (Stream & PhotoData), Speech (ASR) |
| **Networking** | **OkHttp 4.12** | Anycast DNS resolver, cooperative coroutine cancellation |
| **Mobile Client** | **Android / Kotlin** | Jetpack Compose, Material 3, StateFlow, Coroutines, Foreground Service |

---

## PayPal API Endpoints Utilized

| Service | API Endpoint | Implementation File |
| :--- | :--- | :--- |
| **PayPal Orders v2** | `POST /v2/checkout/orders` | `PayPalPaymentService.kt` |
| **PayPal Invoicing v2** | `POST /v2/invoicing/invoices` <br> `POST /v2/invoicing/invoices/{id}/send` | `PayPalInvoiceService.kt` |
| **PayPal Payouts v1** | `POST /v1/payments/payouts` | `PayPalPayoutService.kt` |
| **PayPal Vault** | Vaulted credential tokens | `PayPalVaultRepository.kt` |

---

## Test Scannable QR Codes

Point your Meta Ray-Ban Display smart glasses or mobile camera directly at these QR codes to test 1-tap checkout, flexible splits, and invoices:

| Merchant / Purpose | Amount | Scannable QR Code |
| :--- | :---: | :---: |
| **Coffee Shop** | `$15.00` | <img src="docs/qr/paypal_coffee_15.png" width="140" alt="Coffee Shop $15.00 QR"> |
| **Retail Merchant** | `$25.00` | <img src="docs/qr/paypal_merchant_25.png" width="140" alt="Retail Merchant $25.00 QR"> |
| **Flexible Split** | Custom | <img src="docs/qr/paypal_lunch_noamt.png" width="140" alt="Flexible Split QR"> |
| **Dinner Group Tab** | `$45.00` | <img src="docs/qr/paypal_split_bill_45.png" width="140" alt="Dinner Group Tab $45.00 QR"> |
| **Peer Payout** | Custom | <img src="docs/qr/venmo_qr.png" width="140" alt="Peer Payout QR"> |

---

## Project Structure

```
AirWallet/
├── app/src/main/java/com/meta/wearable/dat/externalsampleapps/cameraaccess/
│   ├── MainActivity.kt                      # Activity lifecycle & voice invocation stream
│   ├── camera/
│   │   ├── CameraViewModel.kt               # Central coordinator: DAT lifecycle, HUD rendering, AI dispatch
│   │   ├── CameraUiState.kt                 # Reactive state machine for glasses HUD & mobile screens
│   │   └── FastQrScanner.kt                 # ML Kit barcode scanning pipeline
│   ├── payment/
│   │   ├── paypal/
│   │   │   ├── PayPalPaymentService.kt      # PayPal Orders v2 with Vaulting
│   │   │   ├── PayPalInvoiceService.kt      # PayPal Invoicing v2 for bill splitting
│   │   │   ├── PayPalPayoutService.kt       # PayPal Payouts v1 for peer disbursements
│   │   │   ├── PayPalVaultRepository.kt     # Vault credential repository adhering to PayPal best practices
│   │   │   └── PayPalModels.kt              # Data models for Orders, Vault, Invoices, and Payouts
│   │   ├── ai/
│   │   │   ├── FinancialAiEngine.kt         # Google LiteRT (TFLite) edge inference engine & voice Q&A
│   │   │   └── GeminiFlashService.kt        # Google Gemini 2.0 Flash multimodal visual AI service
│   │   ├── QrCodeAnalyzer.kt                # Multi-format QR decoder (PayPal.me, Managed QRs, UPI)
│   │   └── TransactionRepository.kt         # Persistent local transaction ledger
│   ├── ui/
│   │   ├── WalletMainScreen.kt              # Jetpack Compose companion application
│   │   ├── CameraScreen.kt                  # Camera scanner view & viewfinder
│   │   └── CameraAccessScaffold.kt          # Root navigation & error handling
│   └── stream/
│       ├── StreamingService.kt              # Foreground service for stream stability
│       └── HevcDecoder.kt                   # Low-latency hardware HEVC video decoder
├── app/src/main/assets/
│   └── financial_analytics.tflite           # On-device LiteRT financial intelligence model
└── README.md
```

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
