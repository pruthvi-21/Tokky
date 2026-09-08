package com.boxy.authenticator.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.boxy_file
import boxy_authenticator.composeapp.generated.resources.cancel
import boxy_authenticator.composeapp.generated.resources.duplicate_warning_message
import boxy_authenticator.composeapp.generated.resources.enter_password_to_decrypt
import boxy_authenticator.composeapp.generated.resources.enter_your_password
import boxy_authenticator.composeapp.generated.resources.hint_issuer
import boxy_authenticator.composeapp.generated.resources.hint_label
import boxy_authenticator.composeapp.generated.resources.import_accounts
import boxy_authenticator.composeapp.generated.resources.import_from
import boxy_authenticator.composeapp.generated.resources.import_label
import boxy_authenticator.composeapp.generated.resources.loading_file
import boxy_authenticator.composeapp.generated.resources.plain_text_file
import boxy_authenticator.composeapp.generated.resources.proceed
import boxy_authenticator.composeapp.generated.resources.retry
import boxy_authenticator.composeapp.generated.resources.rename
import boxy_authenticator.composeapp.generated.resources.warning
import com.boxy.authenticator.core.TokenFormValidator
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.ui.components.Toolbar
import com.boxy.authenticator.ui.components.design.BoxyButton
import com.boxy.authenticator.ui.components.design.BoxyPreferenceScreen
import com.boxy.authenticator.ui.components.design.BoxyScaffold
import com.boxy.authenticator.ui.components.design.BoxyTextField
import com.boxy.authenticator.ui.components.dialogs.BoxyDialog
import com.boxy.authenticator.ui.components.dialogs.RequestPasswordDialog
import com.boxy.authenticator.ui.viewmodels.ImportTokensViewModel
import com.boxy.authenticator.utils.name
import com.jw.preferences.Preference
import com.jw.preferences.PreferenceCategory
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportTokensScreen(navController: NavController) {

    val importTokensViewModel: ImportTokensViewModel = koinViewModel()

    val snackbarHostState = remember { SnackbarHostState() }
    val importState by importTokensViewModel.uiState.collectAsStateWithLifecycle()

    BoxyScaffold(
        topBar = {
            Toolbar(
                title = stringResource(Res.string.import_accounts),
                showDefaultNavigationIcon = true,
                onNavigationIconClick = { navController.navigateUp() }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp)
        ) {

            AnimatedContent(
                targetState = importState,
                contentKey = { state ->
                    when (state) {
                        ImportTokensViewModel.UiState.Initial -> "initial"
                        ImportTokensViewModel.UiState.Loading -> "loading"
                        is ImportTokensViewModel.UiState.Error -> "error"
                        is ImportTokensViewModel.UiState.FileLoaded -> "loaded"
                        is ImportTokensViewModel.UiState.RequestPassword -> "password"
                    }
                },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "import-data-state",
                modifier = Modifier.fillMaxSize(),
            ) { uiState ->
            when (uiState) {
                ImportTokensViewModel.UiState.Initial -> {

                    BoxyPreferenceScreen {
                        item {
                            PreferenceCategory(
                                title = { Text(stringResource(Res.string.import_from)) }
                            ) {
                                Preference(
                                    title = { Text(stringResource(Res.string.boxy_file) + " (.boxy)") },
                                    onClick = {
                                        importTokensViewModel.pickFile(isEncrypted = true)
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                    },
                                )
                                Preference(
                                    title = { Text(stringResource(Res.string.plain_text_file) + " (.txt)") },
                                    onClick = {
                                        importTokensViewModel.pickFile(isEncrypted = false)
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                    },
                                    showDivider = false,
                                )
                            }
                        }
                    }
                }

                ImportTokensViewModel.UiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(Res.string.loading_file),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }

                is ImportTokensViewModel.UiState.Error -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error)
                    BoxyButton(
                        onClick = importTokensViewModel::setInitialState,
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text(stringResource(Res.string.retry)) }
                }

                is ImportTokensViewModel.UiState.FileLoaded -> {
                    LaunchedEffect(uiState.errorMessage) {
                        val message = uiState.errorMessage ?: return@LaunchedEffect
                        snackbarHostState.showSnackbar(message)
                        importTokensViewModel.clearErrorMessage()
                    }
                    val tokensToImport = uiState.list

                    DuplicateTokensWarningDialog(
                        showDialog = importTokensViewModel.showDuplicateWarningDialog.value,
                        tokensToImport = tokensToImport,
                        onDismissRequest = {
                            importTokensViewModel.showDuplicateWarningDialog.value = false
                        },
                        onConfirmRequest = {
                            importTokensViewModel.importAccounts(tokensToImport) { success ->
                                importTokensViewModel.showDuplicateWarningDialog.value = false
                                if (success) navController.navigateUp()
                            }
                        }
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(tokensToImport, key = { it.token.id }) {
                            ImportListItem(
                                item = it,
                                importTokensViewModel = importTokensViewModel
                            )
                        }
                    }
                    BoxyButton(
                        onClick = {
                            if (tokensToImport.any { it.isDuplicate }) {
                                importTokensViewModel.showDuplicateWarningDialog.value = true
                            } else {
                                importTokensViewModel.importAccounts(tokensToImport) { success ->
                                    importTokensViewModel.showDuplicateWarningDialog.value = false
                                    if (success) navController.navigateUp()
                                }
                            }
                        },
                        enabled = tokensToImport.any { it.isChecked } && !uiState.isImporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .heightIn(min = 46.dp)
                    ) {
                        if (uiState.isImporting) {
                            CircularProgressIndicator(modifier = Modifier.height(24.dp))
                        } else {
                            Text(text = stringResource(Res.string.import_label))
                        }
                    }
                }

                is ImportTokensViewModel.UiState.RequestPassword -> {
                    RequestPasswordDialog(
                        title = stringResource(Res.string.enter_your_password),
                        placeholder = stringResource(Res.string.enter_password_to_decrypt),
                        onDismissRequest = {
                            importTokensViewModel.setInitialState()
                        },
                        onConfirmation = {
                            importTokensViewModel.decodeEncryptedContent(uiState.file, it)
                        }
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun DuplicateTokensWarningDialog(
    showDialog: Boolean,
    tokensToImport: List<ImportTokensViewModel.ImportItem>,
    onDismissRequest: () -> Unit,
    onConfirmRequest: () -> Unit,
) {
    if (showDialog) {
        val duplicateCount = tokensToImport.count { it.isDuplicate }
        val nonDuplicateCount = tokensToImport.size - duplicateCount

        BoxyDialog(
            dialogTitle = stringResource(Res.string.warning),
            dialogBody = stringResource(
                Res.string.duplicate_warning_message,
                nonDuplicateCount,
                duplicateCount
            ),
            confirmText = stringResource(Res.string.proceed),
            dismissText = stringResource(Res.string.cancel),
            onDismissRequest = onDismissRequest,
            onConfirmation = onConfirmRequest,
        )
    }
}

@Composable
private fun RenameTokenDialog(
    showDialog: Boolean,
    token: TokenEntry,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    if (showDialog) {
        val issuer = remember { mutableStateOf(token.issuer) }
        val label = remember { mutableStateOf(token.label) }

        val issuerError = remember { mutableStateOf<String?>(null) }

        BoxyDialog(
            dialogTitle = stringResource(Res.string.rename),
            onDismissRequest = onDismissRequest,
            onConfirmation = {
                val validator = TokenFormValidator()
                when (val issuerResult = validator.validateIssuer(issuer.value)) {
                    is TokenFormValidator.Result.Failure -> {
                        scope.launch {
                            issuerError.value = getString(issuerResult.errorMessage)
                        }
                    }

                    is TokenFormValidator.Result.Success -> {
                        onConfirmRequest(issuer.value, label.value)
                    }
                }
            }
        ) {
            Column {
                BoxyTextField(
                    value = issuer.value,
                    onValueChange = {
                        issuer.value = it
                        issuerError.value = null
                    },
                    placeholder = stringResource(Res.string.hint_issuer),
                    errorMessage = issuerError.value
                )
                Spacer(Modifier.height(10.dp))

                BoxyTextField(
                    value = label.value,
                    onValueChange = { label.value = it },
                    placeholder = stringResource(Res.string.hint_label)
                )
            }
        }
    }
}

@Composable
private fun ImportListItem(
    item: ImportTokensViewModel.ImportItem,
    importTokensViewModel: ImportTokensViewModel,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(
                    if (!item.isDuplicate) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.errorContainer
                )
                .clickable { importTokensViewModel.toggleToken(item) }
                .padding(horizontal = 16.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = { importTokensViewModel.toggleToken(item) }
            )
            Text(
                item.token.name,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
            )
            IconButton(onClick = {
                importTokensViewModel.showRenameTokenDialogWithId.value = item.token.id
            }) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null
                )
            }
        }
        HorizontalDivider()
    }

    RenameTokenDialog(
        showDialog = item.token.id == importTokensViewModel.showRenameTokenDialogWithId.value,
        token = item.token,
        onDismissRequest = {
            importTokensViewModel.showRenameTokenDialogWithId.value = null
        },
        onConfirmRequest = { issuer, label ->
            importTokensViewModel.updateToken(item.token, issuer, label)
        }
    )
}
