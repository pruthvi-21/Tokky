package com.boxy.authenticator.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.title_settings
import com.boxy.authenticator.domain.models.form.SettingChangeEvent
import com.boxy.authenticator.ui.components.Toolbar
import com.boxy.authenticator.ui.components.design.BoxyPreferenceScreen
import com.boxy.authenticator.ui.components.design.BoxyScaffold
import com.boxy.authenticator.ui.screens.settings.AppearanceSettings
import com.boxy.authenticator.ui.screens.settings.GeneralSettings
import com.boxy.authenticator.ui.screens.settings.SecuritySettings
import com.boxy.authenticator.ui.screens.settings.TransferAccounts
import com.boxy.authenticator.ui.state.SettingsUiState
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onEvent: (SettingChangeEvent) -> Unit,
    showEnableAppLockDialog: (Boolean) -> Unit,
    showDisableAppLockDialog: (Boolean) -> Unit,
    clearSecurityError: () -> Unit,
    navigateToExportScreen: () -> Unit,
    navigateToImportScreen: () -> Unit,
    navigateUp: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.securityError) {
        uiState.securityError?.let {
            snackbarHostState.showSnackbar(it)
            clearSecurityError()
        }
    }

    BoxyScaffold(
        topBar = {
            Toolbar(
                title = stringResource(Res.string.title_settings),
                showDefaultNavigationIcon = true,
                onNavigationIconClick = { navigateUp() }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        BoxyPreferenceScreen(
            modifier = Modifier
                .padding(contentPadding)
                .padding(horizontal = 10.dp),
        ) {
            item {
                AppearanceSettings(
                    uiState = uiState,
                    onEvent = onEvent,
                )
            }
            item {
                GeneralSettings(
                    uiState = uiState,
                    onEvent = onEvent,
                )
            }
            item {
                SecuritySettings(
                    uiState = uiState,
                    snackbarHostState = snackbarHostState,
                    onEvent = onEvent,
                    showEnableAppLockDialog = showEnableAppLockDialog,
                    showDisableAppLockDialog = showDisableAppLockDialog,
                )
            }
            item {
                TransferAccounts(
                    uiState = uiState,
                    navigateToExport = navigateToExportScreen,
                    navigateToImport = navigateToImportScreen
                )
            }
        }
    }
}
