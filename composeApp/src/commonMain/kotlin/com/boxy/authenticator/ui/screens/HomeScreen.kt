package com.boxy.authenticator.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.app_name
import boxy_authenticator.composeapp.generated.resources.dismiss
import boxy_authenticator.composeapp.generated.resources.empty_layout_text
import boxy_authenticator.composeapp.generated.resources.expandable_fab_manual_title
import boxy_authenticator.composeapp.generated.resources.expandable_fab_qr_title
import boxy_authenticator.composeapp.generated.resources.no_backup_taken_msg
import boxy_authenticator.composeapp.generated.resources.outdated_backup_msg
import boxy_authenticator.composeapp.generated.resources.retry
import boxy_authenticator.composeapp.generated.resources.title_settings
import boxy_authenticator.composeapp.generated.resources.unable_to_load_accounts
import com.boxy.authenticator.core.Platform
import com.boxy.authenticator.ui.components.ExpandableFab
import com.boxy.authenticator.ui.components.ExpandableFabItem
import com.boxy.authenticator.ui.components.Toolbar
import com.boxy.authenticator.ui.components.design.BoxyScaffold
import com.boxy.authenticator.ui.components.design.BoxyButton
import com.boxy.authenticator.ui.screens.home.TokensList
import com.boxy.authenticator.ui.state.HomeUiState
import com.boxy.authenticator.ui.state.DataLoadState
import com.boxy.authenticator.ui.util.SystemBackHandler
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    loadTokens: () -> Unit,
    onFabExpanded: (Boolean) -> Unit,
    onDismissSnackbar: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToQrScan: () -> Unit,
    onNavigateToNewTokenSetup: () -> Unit,
    onNavigateToEditToken: (String) -> Unit,
) {

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        loadTokens()
    }

    LaunchedEffect(
        uiState.tokensState,
        uiState.isLastBackupOutdated,
        uiState.hasTakenAtleastOneBackup,
        uiState.isSnackBarVisible,
    ) {
        if (uiState.tokensState is DataLoadState.Data &&
            (uiState.isLastBackupOutdated || !uiState.hasTakenAtleastOneBackup) &&
            uiState.isSnackBarVisible
        ) {
            val message = when {
                !uiState.hasTakenAtleastOneBackup -> getString(Res.string.no_backup_taken_msg)
                uiState.isLastBackupOutdated -> getString(Res.string.outdated_backup_msg)
                else -> null
            }
            if (message != null) {
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = getString(Res.string.dismiss),
                )
                onDismissSnackbar()
            }
        }
    }

    SystemBackHandler(enabled = uiState.isFabExpanded) {
        onFabExpanded(false)
    }

    BoxyScaffold(
        topBar = {
            Toolbar(
                title = stringResource(Res.string.app_name),
                showDefaultNavigationIcon = false,
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.TwoTone.Settings,
                            contentDescription = stringResource(Res.string.title_settings),
                        )
                    }
                },
            )
        }
    ) { safePadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(safePadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                AnimatedContent(
                    targetState = uiState.tokensState,
                    contentKey = { state ->
                        when (state) {
                            DataLoadState.Initial -> "initial"
                            DataLoadState.Loading -> "loading"
                            is DataLoadState.Error -> "error"
                            is DataLoadState.Data -> if (state.value.isEmpty()) "empty" else "content"
                        }
                    },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "home-data-state",
                ) { state ->
                    when (state) {
                    DataLoadState.Initial, DataLoadState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    is DataLoadState.Data -> if (state.value.isNotEmpty()) {
                        TokensList(
                            tokensList = state.value,
                            onEdit = { onNavigateToEditToken(it.id) }
                        )
                    } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(Res.string.empty_layout_text),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .padding(horizontal = 36.dp),
                                    textAlign = TextAlign.Center,
                                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5f
                                )
                            }
                    }
                    is DataLoadState.Error ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(Res.string.unable_to_load_accounts),
                            color = MaterialTheme.colorScheme.error,
                        )
                        BoxyButton(onClick = loadTokens, modifier = Modifier.padding(top = 12.dp)) {
                            Text(stringResource(Res.string.retry))
                        }
                    }
                    }
                }
            }

            if (uiState.isRefreshing && uiState.tokensState is DataLoadState.Data) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.TopCenter)
                )
            }
        }
    }

    if (Platform.isAndroid) {
        // Not needed in IOS as action sheet is used
        AnimatedVisibility(
            visible = uiState.isFabExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .then(
                        if (!uiState.isFabExpanded) Modifier
                        else Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = uiState.isFabExpanded
                        ) { onFabExpanded(false) }
                    )
            )
        }
    }

    val items = listOf(
        ExpandableFabItem(
            stringResource(Res.string.expandable_fab_qr_title),
            Icons.Outlined.QrCodeScanner
        ),
        ExpandableFabItem(
            stringResource(Res.string.expandable_fab_manual_title),
            Icons.Outlined.Edit
        ),
    )

    ExpandableFab(
        isFabExpanded = uiState.isFabExpanded,
        items = items,
        onItemClick = { index ->
            onFabExpanded(false)
            when (index) {
                0 -> onNavigateToQrScan()
                1 -> onNavigateToNewTokenSetup()
            }
        },
        onFabExpandChange = {
            onFabExpanded(it)
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(16.dp),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .align(Alignment.BottomCenter),
        ) { snackbarData ->
            Snackbar(
                snackbarData = snackbarData,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                actionColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
