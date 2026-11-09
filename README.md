# AirWallet
### Ambient Agentic Commerce on Meta Ray-Ban Smart Glasses with PayPal & On-Device AI

[![PayPal Developer](https://img.shields.io/badge/PayPal-Orders_v2_|_Vault_|_Invoicing_|_Payouts-003087?style=for-the-badge&logo=paypal&logoColor=white)](https://developer.paypal.com)
[![Google Gemini 2.0 Flash](https://img.shields.io/badge/Google_Gemini_2.0_Flash-Multimodal_Visual_AI-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://ai.google.dev)
[![Google LiteRT](https://img.shields.io/badge/Google_LiteRT-On--Device_AI_(TFLite)-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://ai.google.dev/edge/litert)
[![Meta Wearables](https://img.shields.io/badge/Meta_Wearables_DAT-Display_HUD_|_Camera_|_Speech-0668E1?style=for-the-badge&logo=meta&logoColor=white)](https://wearables.developer.meta.com)
[![Android](https://img.shields.io/badge/Android-Jetpack_Compose-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)

---

## Overview

**AirWallet** transforms Meta Ray-Ban Display smart glasses into an **ambient, agentic commerce terminal**. By combining **PayPal's developer suite** (Orders v2 with Vaulting, Invoicing v2, and Payouts v1) with **Google Gemini 2.0 Flash multimodal visual AI**, **on-device Google LiteRT financial intelligence**, and the **Meta Wearables DAT (Device Access Toolkit)**, AirWallet enables hands-free, glance-and-tap transactions in physical environments.

Wearers can look at merchant QR codes or physical products, inspect details directly on the glasses' waveguide HUD, complete 1-tap vaulted payments via PayPal credentials, split dining bills with live PayPal invoices, query on-device financial intelligence via voice, and disburse peer-to-peer payouts.

---

## Active Capabilities & Architecture

### 1. Glance-to-Pay with PayPal Vault (Orders v2)
- **High-Speed QR Decoding**: Decodes merchant QR codes (`paypal.me`, PayPal Managed Merchant QRs, or point-of-sale barcodes) via low-latency camera capture and Google ML Kit.
- **1-Tap Vaulted Capture**: Leverages PayPal Vault to capture payments instantly (`POST /v2/checkout/orders` with vaulted credentials) without requiring phone unlock, app switching, or web redirects.
- **Waveguide Optical HUD**: Renders real-time transaction confirmation, merchant details, amount, and payment status onto the smart glasses display using declarative FlexBox DSL.

### 2. Multimodal Visual AI (Google Gemini 2.0 Flash)
- **Visual Product Search & 1-Tap Checkout**: Point the glasses camera at any physical product. Google Gemini 2.0 Flash multimodal vision identifies the product name, estimated price, and merchant, instantly populating a 1-tap PayPal purchase option on both the glasses HUD and the companion app.
- **Optical Receipt Itemization**: Point the glasses at any paper dining receipt or retail invoice. Gemini Flash parses merchant name, line items, tax, and grand total, preparing an automated multi-party split dispatched through PayPal Invoicing v2.
- **Embedded Credentials & Anycast DNS Resilience**: Pre-configured app-side credentials with OkHttp Google Anycast DNS fallback (`172.217.115.4`, `142.250.193.138`, `142.250.193.10`) ensure network requests succeed uninterrupted alongside active smart glasses Bluetooth streaming.

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

## Architecture Diagram

![AirWallet Architecture](docs/architecture.jpg)

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

## Testing Guide

AirWallet can be evaluated **with physical Meta Ray-Ban Display smart glasses** or **on an Android device** using the built-in MockDeviceKit emulator and mobile camera scanner.

### Option A: Testing on Android Device (No Glasses Required)
1. **Clone and build**:
   ```bash
   git clone https://github.com/your-username/AirWallet.git
   cd AirWallet
   ./gradlew assembleDebug
   ```
2. **Install APK**:
   ```bash
   adb install -r app/build.nosync/outputs/apk/debug/app-debug.apk
   ```
3. **Run AirWallet**:
   - Tap **"Scan QR (Mobile)"** or switch to the **Scan** tab.
   - Point the camera at any test QR code below.
   - The app parses the merchant payload and executes the 1-Tap PayPal payment.
   - Use the **Scan Object** and **Scan Bill** triggers to test multimodal visual AI.
   - Visit the **Spending Analytics** tab to test on-device Google LiteRT financial intelligence.

### Option B: Testing with Meta Ray-Ban Display Smart Glasses
1. Enable **Developer Mode** in the Meta View companion app.
2. Pair the glasses via Bluetooth and open AirWallet on the phone.
3. Tap **"Connect"** to initialize the DAT device session.
4. Put on the glasses: the **AirWallet HUD** illuminates with Home options (Scan QR Code, Send Money, Spending Analytics, Scan Product, Scan Paper Bill).
5. Glance at a merchant QR code or physical product and tap the temple touchpad to buy with vaulted PayPal credentials.

---

## Test QR Payloads

| Merchant / Purpose | Amount | Test QR Payload URL |
| :--- | :---: | :--- |
| **Coffee Shop** | `$15.00` | `https://paypal.me/CoffeeShop/15` |
| **Retail Merchant** | `$25.00` | `https://www.paypal.com/qrcodes/managed/demo?amount=25.00&currencyCode=USD` |
| **Flexible Split** | Custom | `https://paypal.me/LunchBistro` |
| **Dinner Group Tab** | `$45.00` | `https://paypal.me/ItalianKitchen/45` |

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
│   │   ├── CameraAccessScaffold.kt          # Root navigation & error handling
│   │   └── MockDeviceKitScreen.kt           # MockDeviceKit emulator for no-hardware testing
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
