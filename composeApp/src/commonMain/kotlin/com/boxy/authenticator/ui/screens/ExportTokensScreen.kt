package com.boxy.authenticator.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.accounts_exported
import boxy_authenticator.composeapp.generated.resources.boxy_file
import boxy_authenticator.composeapp.generated.resources.error_fetching_tokens
import boxy_authenticator.composeapp.generated.resources.export
import boxy_authenticator.composeapp.generated.resources.export_accounts
import boxy_authenticator.composeapp.generated.resources.export_failed
import boxy_authenticator.composeapp.generated.resources.export_to
import boxy_authenticator.composeapp.generated.resources.i_understand_the_risk
import boxy_authenticator.composeapp.generated.resources.plain_text_file
import boxy_authenticator.composeapp.generated.resources.recommended
import boxy_authenticator.composeapp.generated.resources.no_accounts_to_export
import boxy_authenticator.composeapp.generated.resources.retry
import boxy_authenticator.composeapp.generated.resources.warning
import boxy_authenticator.composeapp.generated.resources.warning_backup_encryption
import boxy_authenticator.composeapp.generated.resources.warning_no_backup_encryption
import com.boxy.authenticator.ui.components.Toolbar
import com.boxy.authenticator.ui.components.design.BoxyPreferenceScreen
import com.boxy.authenticator.ui.components.design.BoxyScaffold
import com.boxy.authenticator.ui.components.design.BoxyButton
import com.boxy.authenticator.ui.components.dialogs.BoxyDialog
import com.boxy.authenticator.ui.components.dialogs.SetPasswordDialog
import com.boxy.authenticator.ui.state.ExportUiState
import com.boxy.authenticator.ui.state.DataLoadState
import com.jw.preferences.Preference
import com.jw.preferences.PreferenceCategory
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportTokensScreen(
    uiState: ExportUiState,
    showPlainTextWarningDialog: (show: Boolean) -> Unit,
    showSetPasswordDialog: (show: Boolean) -> Unit,
    exportToPlainTextFile: (onDone: (Boolean) -> Unit) -> Unit,
    exportToBoxyFile: (password: String, onDone: (Boolean) -> Unit) -> Unit,
    retryLoad: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    BoxyScaffold(
        topBar = {
            Toolbar(
                title = stringResource(Res.string.export_accounts),
                showDefaultNavigationIcon = true,
                onNavigationIconClick = { onNavigateUp() }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->

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
            label = "export-data-state",
            modifier = Modifier
                .padding(contentPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
        ) { state ->
            when (state) {
            DataLoadState.Initial, DataLoadState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is DataLoadState.Error -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(stringResource(Res.string.error_fetching_tokens), color = MaterialTheme.colorScheme.error)
                BoxyButton(onClick = retryLoad, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(Res.string.retry))
                }
            }
            is DataLoadState.Data -> {
            if (state.value.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.no_accounts_to_export))
                }
            } else {
            val exportEnabled = state.value.isNotEmpty() && !uiState.isExporting
            BoxyPreferenceScreen {
                item {
                    PreferenceCategory(
                        title = { Text(stringResource(Res.string.export_to)) },
                    ) {
                        Preference(
                            title = {
                                Text(
                                    stringResource(Res.string.boxy_file) +
                                            " (${stringResource(Res.string.recommended)})"
                                )
                            },
                            enabled = exportEnabled,
                            onClick = {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                showSetPasswordDialog(true)
                            },
                        )
                        Preference(
                            title = { Text(stringResource(Res.string.plain_text_file)) },
                            enabled = exportEnabled,
                            onClick = {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                showPlainTextWarningDialog(true)
                            },
                            showDivider = false,
                        )
                    }
                }
            }
            }
            }
            }
        }

        if (uiState.showPlainTextWarningDialog) {
            var isUnencryptedAcknowledged by remember { mutableStateOf(false) }

            BoxyDialog(
                dialogTitle = stringResource(Res.string.warning),
                confirmText = stringResource(Res.string.export),
                confirmEnabled = isUnencryptedAcknowledged,
                onDismissRequest = {
                    showPlainTextWarningDialog(false)
                },
                onConfirmation = {
                    showPlainTextWarningDialog(false)
                    exportToPlainTextFile {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                if (it) getString(Res.string.accounts_exported)
                                else getString(Res.string.export_failed)
                            )
                        }
                    }
                },
            ) {
                Column {
                    Text(stringResource(Res.string.warning_no_backup_encryption))
                    Row(
                        modifier = Modifier
                            .padding(vertical = 5.dp)
                            .align(Alignment.End)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { isUnencryptedAcknowledged = !isUnencryptedAcknowledged }
                            .padding(end = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isUnencryptedAcknowledged,
                            onCheckedChange = {
                                isUnencryptedAcknowledged = !isUnencryptedAcknowledged
                            },
                            colors = CheckboxDefaults.colors().copy(
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                            )
                        )

                        Text(stringResource(Res.string.i_understand_the_risk))
                    }
                }
            }
        }

        if (uiState.showSetPasswordDialog) {
            SetPasswordDialog(
                dialogBody = stringResource(Res.string.warning_backup_encryption),
                confirmText = stringResource(Res.string.export),
                onDismissRequest = {
                    showSetPasswordDialog(false)
                },
                onConfirmation = { password ->
                    showSetPasswordDialog(false)
                    exportToBoxyFile(password) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                if (it) getString(Res.string.accounts_exported)
                                else getString(Res.string.export_failed)
                            )
                        }
                    }
                }
            )
        }

        if (uiState.isExporting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            }
        }
    }
}
