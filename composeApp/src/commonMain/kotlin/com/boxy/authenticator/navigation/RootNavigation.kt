package com.boxy.authenticator.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.domain.models.enums.TokenSetupMode
import com.boxy.authenticator.ui.screens.AuthenticationScreen
import com.boxy.authenticator.ui.screens.ExportTokensScreen
import com.boxy.authenticator.ui.screens.HomeScreen
import com.boxy.authenticator.ui.screens.ImportTokensScreen
import com.boxy.authenticator.ui.screens.QrScannerScreen
import com.boxy.authenticator.ui.screens.SettingsScreen
import com.boxy.authenticator.ui.screens.ManageLabelsScreen
import com.boxy.authenticator.ui.screens.RecycleBinScreen
import com.boxy.authenticator.ui.screens.TokenSetupScreen
import com.boxy.authenticator.ui.viewmodels.AuthenticationViewModel
import com.boxy.authenticator.ui.viewmodels.ExportTokensViewModel
import com.boxy.authenticator.ui.viewmodels.HomeViewModel
import com.boxy.authenticator.ui.viewmodels.SettingsViewModel
import com.boxy.authenticator.ui.viewmodels.ManageLabelsViewModel
import com.boxy.authenticator.ui.viewmodels.RecycleBinViewModel
import com.boxy.authenticator.ui.viewmodels.TokenSetupViewModel
import com.boxy.authenticator.ui.util.BindDeviceLockEffect
import io.ktor.http.decodeURLQueryComponent
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RootNavigation(
    settingsViewModel: SettingsViewModel
) {
    val density = LocalDensity.current
    val transitions = TransitionHelper(density)

    val settings: SettingsDataStore = koinInject()
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current
    val appLockLifecycleGuard = remember(settings) {
        AppLockLifecycleGuard(settings::isAppLockEnabled)
    }

    val startDestination = if (settings.isAppLockEnabled()) Screen.Auth
    else Screen.Home

    fun navigateToAuthentication() {
        navController.navigate(Screen.Auth) {
            popUpTo(navController.graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }

    BindDeviceLockEffect {
        if (appLockLifecycleGuard.onDeviceLocked()) navigateToAuthentication()
    }

    DisposableEffect(lifecycleOwner, navController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    appLockLifecycleGuard.onStopped()
                }
                Lifecycle.Event.ON_START -> {
                    if (appLockLifecycleGuard.onStarted()) {
                        navigateToAuthentication()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { transitions.screenEnterAnim },
        exitTransition = { transitions.screenExitAnim },
        popEnterTransition = { transitions.screenPopEnterAnim },
        popExitTransition = { transitions.screenPopExitAnim },
    ) {
        composable<Screen.Auth> {
            val authViewModel: AuthenticationViewModel = koinViewModel()

            val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

            AuthenticationScreen(
                uiState = uiState,
                isPinPadVisible = authViewModel.isPinPadVisible.value,
                isBiometricUnlockEnabled = authViewModel.isBiometricUnlockEnabled,
                onPasswordChange = { authViewModel.updatePassword(it) },
                onSubmit = {
                    authViewModel.verifyPassword {
                        if (it) navController.navigate(Screen.Home) {
                            popUpTo<Screen.Auth> { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                updatePinPadVisibility = { authViewModel.updatePinPadVisibility() },
                onAuthSuccess = {
                    navController.navigate(Screen.Home) {
                        popUpTo<Screen.Auth> { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<Screen.Home> {
            val homeViewModel: HomeViewModel = koinViewModel()

            val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
            val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            HomeScreen(
                uiState = uiState,
                loadTokens = { homeViewModel.loadTokens() },
                onFabExpanded = { homeViewModel.setIsFabExpanded(it) },
                onTokenViewed = homeViewModel::markTokenViewed,
                onUpdateHotpCounter = homeViewModel::updateHotpCounter,
                onDismissSnackbar = { homeViewModel.dismissSnackbar() },
                onTokenLongPressed = homeViewModel::selectToken,
                onTokenSelectionToggle = homeViewModel::toggleTokenSelection,
                onClearSelection = homeViewModel::clearSelection,
                onShowApplyLabelsDialog = homeViewModel::showApplyLabelsDialog,
                onToggleLabelToApply = homeViewModel::toggleLabelToApply,
                onApplyLabelsToSelection = homeViewModel::applyLabelsToSelection,
                onShowArchiveSelectionDialog = homeViewModel::showArchiveSelectionDialog,
                onArchiveOrRestoreSelection = homeViewModel::archiveOrRestoreSelection,
                onShowDeleteSelectionDialog = homeViewModel::showDeleteSelectionDialog,
                onDeleteSelection = homeViewModel::deleteSelection,
                onClearSelectionError = homeViewModel::clearSelectionError,
                onLabelFilterToggle = homeViewModel::toggleLabelFilter,
                onDefaultLabelRequested = homeViewModel::requestDefaultLabel,
                onDefaultLabelDismissed = homeViewModel::dismissDefaultLabelDialog,
                onDefaultLabelConfirmed = homeViewModel::confirmDefaultLabel,
                onArchivedFilterToggle = homeViewModel::toggleArchivedFilter,
                onDefaultArchivedRequested = homeViewModel::requestDefaultArchived,
                labelVisibility = settingsUiState.settings.labelVisibility,
                showLabelCounts = settingsUiState.settings.isShowLabelCountsEnabled,
                onNavigateToSettings = { navController.navigate(Screen.Settings) },
                onNavigateToManageLabels = { navController.navigate(Screen.ManageLabels) },
                onNavigateToRecycleBin = { navController.navigate(Screen.RecycleBin) },
                onNavigateToQrScan = { navController.navigate(Screen.QrScanner) },
                onNavigateToNewTokenSetup = { navController.navigate(Screen.TokenSetup()) },
                onNavigateToEditToken = {
                    homeViewModel.clearSelection()
                    navController.navigate(Screen.TokenSetup(tokenId = it))
                }
            )
        }

        composable<Screen.QrScanner> {
            QrScannerScreen(navController)
        }

        composable<Screen.TokenSetup> { navBackStackEntry ->
            val params = navBackStackEntry.toRoute<Screen.TokenSetup>()

            val viewModel: TokenSetupViewModel = koinViewModel()

            when {
                params.authUrl != null -> {
                    TokenSetupScreen(
                        viewModel = viewModel,
                        tokenId = null,
                        authUrl = params.authUrl.decodeURLQueryComponent(),
                        setupMode = TokenSetupMode.URL,
                        navController = navController
                    )
                }

                params.tokenId != null -> {
                    TokenSetupScreen(
                        viewModel = viewModel,
                        tokenId = params.tokenId,
                        setupMode = TokenSetupMode.UPDATE,
                        navController = navController
                    )
                }

                else -> {
                    TokenSetupScreen(
                        viewModel = viewModel,
                        tokenId = null,
                        setupMode = TokenSetupMode.NEW,
                        navController = navController
                    )
                }
            }
        }

        composable<Screen.Settings> {
            val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            SettingsScreen(
                uiState = uiState,
                onEvent = { settingsViewModel.onEvent(it) },
                showEnableAppLockDialog = { settingsViewModel.showEnableAppLockDialog(it) },
                showDisableAppLockDialog = { settingsViewModel.showDisableAppLockDialog(it) },
                clearSecurityError = settingsViewModel::clearSecurityError,
                navigateToExportScreen = { navController.navigate(Screen.ExportTokens) },
                navigateToImportScreen = { navController.navigate(Screen.ImportTokens) },
                navigateUp = { navController.navigateUp() },
            )
        }

        composable<Screen.ExportTokens> {
            val exportViewModel: ExportTokensViewModel = koinViewModel()

            val uiState by exportViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                exportViewModel.loadAllTokens()
            }

            ExportTokensScreen(
                uiState = uiState,
                showPlainTextWarningDialog = { exportViewModel.showPlainTextWarningDialog(it) },
                showSetPasswordDialog = { exportViewModel.showSetPasswordDialog(it) },
                exportToPlainTextFile = { exportViewModel.exportToPlainTextFile(it) },
                exportToEncryptedFile = { password, onDone ->
                    exportViewModel.exportToEncryptedFile(password, onDone)
                },
                retryLoad = exportViewModel::loadAllTokens,
                onNavigateUp = { navController.navigateUp() }
            )
        }

        composable<Screen.ImportTokens> {
            ImportTokensScreen(navController)
        }

        composable<Screen.ManageLabels> {
            val viewModel: ManageLabelsViewModel = koinViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            ManageLabelsScreen(
                uiState = uiState,
                loadLabels = viewModel::loadLabels,
                showRenameDialog = viewModel::showRenameDialog,
                updateEditedName = viewModel::updateEditedName,
                renameLabel = viewModel::renameLabel,
                showDeleteDialog = viewModel::showDeleteDialog,
                deleteLabel = viewModel::deleteLabel,
                clearOperationError = viewModel::clearOperationError,
                navigateUp = navController::navigateUp,
            )
        }

        composable<Screen.RecycleBin> {
            val viewModel: RecycleBinViewModel = koinViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            RecycleBinScreen(
                uiState = uiState,
                loadTokens = viewModel::loadTokens,
                restoreToken = viewModel::restoreToken,
                showPermanentDeleteDialog = viewModel::showPermanentDeleteDialog,
                permanentlyDeleteToken = viewModel::permanentlyDeleteToken,
                clearOperationError = viewModel::clearOperationError,
                navigateUp = navController::navigateUp,
            )
        }
    }
}
