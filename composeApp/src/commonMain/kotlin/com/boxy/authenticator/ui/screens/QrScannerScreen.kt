package com.boxy.authenticator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.close
import boxy_authenticator.composeapp.generated.resources.invalid_qr_code
import boxy_authenticator.composeapp.generated.resources.retry
import com.boxy.authenticator.core.Logger
import com.boxy.authenticator.core.TokenEntryParser
import com.boxy.authenticator.navigation.Screen
import com.boxy.authenticator.ui.components.Toolbar
import com.boxy.authenticator.ui.components.design.BoxyScaffold
import com.boxy.authenticator.ui.components.dialogs.BoxyDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import qrscanner.CameraLens
import qrscanner.OverlayShape
import qrscanner.QrScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(navController: NavController) {
    val logger = Logger("QrScannerScreen")

    val scope = rememberCoroutineScope()

    var isFlashOn by remember { mutableStateOf(false) }
    var showPlatformAlertDialog by remember { mutableStateOf(false) }
    var isScanComplete by remember { mutableStateOf(false) }

    BoxyScaffold { contentPadding ->
        Box {
            if (!showPlatformAlertDialog || !isScanComplete) {
                QrScanner(
                    modifier = Modifier,
                    flashlightOn = isFlashOn,
                    cameraLens = CameraLens.Back,
                    openImagePicker = false,
                    onCompletion = { result ->
                        scope.launch {
                            try {
                                TokenEntryParser.buildFromUrl(result)
                                if (!isScanComplete) {
                                    isScanComplete = true
                                    delay(300)
                                    navController.navigate(Screen.TokenSetup(authUrl = result))
                                }
                            } catch (e: Exception) {
                                logger.e(e.message, e)
                                showPlatformAlertDialog = true
                            }
                        }
                    },
                    imagePickerHandler = { },
                    onFailure = { },
                    overlayShape = OverlayShape.Square,
                    overlayColor = Color(0x88000000),
                    overlayBorderColor = MaterialTheme.colorScheme.outline,
                )
            }
            Toolbar(
                title = "",
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, stringResource(Res.string.close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                )
            )

            if (showPlatformAlertDialog) {
                BoxyDialog(
                    dialogTitle = stringResource(Res.string.invalid_qr_code),
                    confirmText = stringResource(Res.string.retry),
                    dismissText = stringResource(Res.string.close),
                    onDismissRequest = {
                        showPlatformAlertDialog = false
                        navController.navigateUp()
                    },
                    onConfirmation = {
                        showPlatformAlertDialog = false
                    }
                )
            }

            CameraControls(
                isFlashOn = isFlashOn,
                onFlashChange = { isFlashOn = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = contentPadding.calculateBottomPadding()),
            )
        }
    }
}

@Composable
private fun CameraControls(
    modifier: Modifier = Modifier,
    isFlashOn: Boolean = false,
    onFlashChange: (Boolean) -> Unit = {},
) {
    Row(
        modifier = modifier
            .padding(20.dp)
    ) {
        IconToggleButton(
            isFlashOn,
            onCheckedChange = {
                onFlashChange(it)
            },
            modifier = Modifier.size(66.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = .05f))
        ) {
            Icon(
                if (isFlashOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}