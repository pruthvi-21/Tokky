package com.boxy.authenticator.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
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
import com.boxy.authenticator.ui.screens.TokenSetupScreen
import com.boxy.authenticator.ui.viewmodels.AuthenticationViewModel
import com.boxy.authenticator.ui.viewmodels.ExportTokensViewModel
import com.boxy.authenticator.ui.viewmodels.HomeViewModel
import com.boxy.authenticator.ui.viewmodels.SettingsViewModel
import com.boxy.authenticator.ui.viewmodels.TokenSetupViewModel
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

    val startDestination = if (settings.isAppLockEnabled()) Screen.Auth
    else Screen.Home

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
                onPasswordChange = { authViewModel.updatePassword(it) },
                onSubmit = {
                    authViewModel.verifyPassword {
                        if (it) navController.navigate(Screen.Home) {
                            popUpTo(navController.graph.startDestinationId) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                },
                updatePinPadVisibility = { authViewModel.updatePinPadVisibility() },
                onAuthSuccess = {
                    navController.navigate(Screen.Home) {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
                navigateToSettings = { navController.navigate(Screen.Settings) }
            )
        }

        composable<Screen.Home> {
            val homeViewModel: HomeViewModel = koinViewModel()

            val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

            HomeScreen(
                uiState = uiState,
                loadTokens = { homeViewModel.loadTokens() },
                onFabExpanded = { homeViewModel.setIsFabExpanded(it) },
                onTokenViewed = homeViewModel::markTokenViewed,
                onUpdateHotpCounter = homeViewModel::updateHotpCounter,
                onDismissSnackbar = { homeViewModel.dismissSnackbar() },
                onNavigateToSettings = { navController.navigate(Screen.Settings) },
                onNavigateToQrScan = { navController.navigate(Screen.QrScanner) },
                onNavigateToNewTokenSetup = { navController.navigate(Screen.TokenSetup()) },
                onNavigateToEditToken = { navController.navigate(Screen.TokenSetup(tokenId = it)) }
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
    }
}
