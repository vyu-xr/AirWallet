/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// CameraAccessScaffold - DAT Application Navigation Orchestrator
//
// This scaffold demonstrates a typical DAT application navigation pattern based on device
// registration state from the DAT API.
//
// DAT State-Based Navigation:
// - HomeScreen: When NOT registered (uiState.isRegistered = false) Shows initial registration UI
//   calling Wearables.startRegistration()
// - CameraScreen: When registered (uiState.isRegistered = true) Shows the full-bleed camera screen
//   that walks the SDK lifecycle: start session, start preview, capture/record, stop, end session.

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.externalsampleapps.cameraaccess.BuildConfig
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.error.AppErrorHandler
import com.meta.wearable.dat.externalsampleapps.cameraaccess.error.ErrorSeverity
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraAccessScaffold(
    viewModel: WearablesViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    onRequestRecordAudioPermission: suspend () -> Boolean,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val activeError by AppErrorHandler.activeError.collectAsStateWithLifecycle()

  Surface(modifier = modifier.fillMaxSize(), color = AppColor.BgCanvas) {
    Box(modifier = Modifier.fillMaxSize()) {
      WalletMainScreen(
          wearablesViewModel = viewModel,
          onRequestWearablesPermission = onRequestWearablesPermission,
          onRequestRecordAudioPermission = onRequestRecordAudioPermission,
      )

      // Dedicated, rate-limited, auto-dismissing error banner (prevents repetitive popup loops)
      AnimatedVisibility(
          visible = activeError != null,
          enter = fadeIn() + slideInVertically { it / 2 },
          exit = fadeOut() + slideOutVertically { it / 2 },
          modifier = Modifier
              .align(Alignment.BottomCenter)
              .navigationBarsPadding()
              .padding(horizontal = 20.dp, vertical = 28.dp),
      ) {
        activeError?.let { err ->
          val isFatal = err.severity == ErrorSeverity.ERROR
          Surface(
              shape = RoundedCornerShape(22.dp),
              color = AppColor.BgSurface,
              border = BorderStroke(
                  1.dp,
                  if (isFatal) AppColor.Coral.copy(alpha = 0.6f) else AppColor.Orange.copy(alpha = 0.5f)
              ),
              shadowElevation = 10.dp,
          ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                  imageVector = if (isFatal) Icons.Default.Error else Icons.Default.Warning,
                  contentDescription = null,
                  tint = if (isFatal) AppColor.Coral else AppColor.Orange,
                  modifier = Modifier.size(20.dp),
              )
              Text(
                  text = err.userMessage,
                  color = AppColor.TextPrimary,
                  fontSize = 12.sp,
                  lineHeight = 16.sp,
                  modifier = Modifier.weight(1f, fill = false),
              )
              IconButton(
                  onClick = { AppErrorHandler.dismissCurrent() },
                  modifier = Modifier.size(22.dp),
              ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = AppColor.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}
