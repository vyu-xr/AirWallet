/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.ui.graphics.Color

/** Design tokens from dark_mode_wallet_smart_glasses_ui_system.tsx */
object AppColor {
  // Core surfaces (from TOKENS)
  val BgCanvas         = Color(0xFF1E1E1E)
  val BgSurface        = Color(0xFF272727)
  val PillActive       = Color(0xFFD4D4D4)
  val BorderSubtle     = Color(0xFF383838)
  val TextPrimary      = Color(0xFFEAEAEA)
  val TextSecondary    = Color(0xFF8E8E93)
  val TextMuted        = Color(0xFF525255)
  val TextInverted     = Color(0xFF1E1E1E)

  // Accent palette (from TOKENS.accents)
  val Lime             = Color(0xFFC5F467)   // primary confirm / active
  val Coral            = Color(0xFFF2746B)   // expenses / error
  val Indigo           = Color(0xFF6875F5)   // camera scan accent
  val Lavender         = Color(0xFFB37CF5)   // contacts / send
  val Orange           = Color(0xFFFC7A58)   // history / savings
  val Sky              = Color(0xFF85C8F2)   // stats chart
  val Mint             = Color(0xFF63B97A)   // UPI / success
  val Slate            = Color(0xFF525252)   // muted chart segment

  // Legacy aliases kept for backward-compat
  val Green            = Mint
  val Red              = Coral
  val Yellow           = Color(0xFFFFCC00)
  val DeepBlue         = Indigo
  val DestructiveBackground  = Color(0xFF3B1F1F)
  val DestructiveForeground  = Coral
  val RecordAccent           = Coral
  val UpdateRequiredBackground = Color(0xFF2E2218)
  val UpdateRequiredForeground = Orange
}
