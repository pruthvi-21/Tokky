package com.boxy.authenticator.ui.viewmodels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.empty_content
import boxy_authenticator.composeapp.generated.resources.failed_to_decrypt
import boxy_authenticator.composeapp.generated.resources.failed_to_read_file
import boxy_authenticator.composeapp.generated.resources.failed_to_check_duplicates
import boxy_authenticator.composeapp.generated.resources.import_failed
import boxy_authenticator.composeapp.generated.resources.no_tokens_to_import
import com.boxy.authenticator.core.Logger
import com.boxy.authenticator.core.TokenEntryParser
import com.boxy.authenticator.core.crypto.Crypto
import com.boxy.authenticator.core.serialization.BoxyJson
import com.boxy.authenticator.domain.models.ExportableTokenEntry
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.usecases.FetchTokenByNameUseCase
import com.boxy.authenticator.domain.usecases.FetchTokensUseCase
import com.boxy.authenticator.domain.usecases.InsertTokensUseCase
import com.boxy.authenticator.utils.name
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PlatformFile
import io.github.vinceglb.filekit.core.pickFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

class ImportTokensViewModel(
    private val fetchTokensUseCase: FetchTokensUseCase,
    private val insertTokensUseCase: InsertTokensUseCase,
    private val fetchTokenByNameUseCase: FetchTokenByNameUseCase,
) : ViewModel() {
    private val logger = Logger("ImportTokensViewModel")

    data class ImportItem(
        val token: TokenEntry,
        val isChecked: Boolean,
        val isDuplicate: Boolean,
    )

    sealed class UiState {
        data object Initial : UiState()
        data object Loading : UiState()
        data class Error(val message: String) : UiState()
        data class FileLoaded(
            val list: List<ImportItem>,
            val isImporting: Boolean = false,
            val errorMessage: String? = null,
        ) : UiState()
        data class RequestPassword(val file: PlatformFile) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val showRenameTokenDialogWithId = mutableStateOf<String?>(null)
    val showDuplicateWarningDialog = mutableStateOf(false)

    fun setInitialState() {
        _uiState.update { UiState.Initial }
    }

    fun pickFile(isEncrypted: Boolean) = viewModelScope.launch {
        val file = runCatching { FileKit.pickFile() }
            .onFailure { logger.e("File selection failed", it) }
            .getOrElse {
                _uiState.value = UiState.Error(getString(Res.string.failed_to_read_file))
                return@launch
            } ?: run {
            return@launch
        }

        if (isEncrypted) {
            _uiState.update { UiState.RequestPassword(file) }
        } else {
            _uiState.update { UiState.Loading }
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val fileContent = file.readBytes()
                    if (fileContent.isEmpty()) throw EmptyFileException()
                    val tokens = decodePlainContent(fileContent.decodeToString())
                    if (tokens.isEmpty()) throw NoTokensException()
                    buildImportListFromTokens(tokens).getOrThrow()
                }
            }
            _uiState.value = result.fold(
                onSuccess = { UiState.FileLoaded(it) },
                onFailure = { UiState.Error(messageForFileError(it)) },
            )
        }
    }

    private fun decodePlainContent(fileContent: String): List<TokenEntry> {
        return runCatching {
            val list = fileContent.split("\n")
            val tokensList = arrayListOf<TokenEntry>()

            list.forEachIndexed { index, line ->
                try {
                    val token = TokenEntryParser.buildFromUrl(line)
                    tokensList.add(token)
                } catch (e: Exception) {
                    logger.e("Error parsing line $index: ${e.message}", e)
                }
            }

            tokensList
        }.onFailure { logger.e(it.message, it) }.getOrThrow()
    }

    fun decodeEncryptedContent(
        file: PlatformFile,
        password: String,
    ) = viewModelScope.launch {
        _uiState.value = UiState.Loading
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val fileContent = file.readBytes()
                val decryptedData = Crypto.decrypt(password, fileContent)
                val list = BoxyJson.decodeFromString<List<ExportableTokenEntry>>(decryptedData)
                    .map { it.toTokenEntry() }
                if (list.isEmpty()) throw NoTokensException()
                buildImportListFromTokens(list).getOrThrow()
            }
        }
        _uiState.value = result.fold(
            onSuccess = { UiState.FileLoaded(it) },
            onFailure = {
                logger.e(it)
                UiState.Error(
                    if (it is DuplicateCheckException) getString(Res.string.failed_to_check_duplicates)
                    else if (it is NoTokensException) getString(Res.string.no_tokens_to_import)
                    else getString(Res.string.failed_to_decrypt)
                )
            },
        )
    }

    fun importAccounts(tokens: List<ImportItem>, onComplete: (Boolean) -> Unit) = viewModelScope.launch {
        val current = _uiState.value as? UiState.FileLoaded ?: return@launch
        if (current.isImporting) return@launch
        val tokensToInsert = tokens
            .filter { !it.isDuplicate }
            .filter { it.isChecked }
            .map { it.token }

        _uiState.value = current.copy(isImporting = true, errorMessage = null)
        val result = insertTokensUseCase(tokensToInsert)
        result.onFailure { logger.e(it.message, it) }
        _uiState.value = current.copy(
            isImporting = false,
            errorMessage = if (result.isFailure) getString(Res.string.import_failed) else null,
        )
        onComplete(result.isSuccess)
    }

    fun toggleToken(token: ImportItem) {
        _uiState.update { currentState ->
            when (currentState) {
                is UiState.FileLoaded -> {
                    val updatedList = currentState.list.map { item ->
                        if (item == token && !item.isDuplicate)
                            item.copy(isChecked = !item.isChecked) else item
                    }
                    UiState.FileLoaded(updatedList)
                }

                else -> currentState
            }
        }
    }

    private suspend fun buildImportListFromTokens(tokens: List<TokenEntry>): Result<List<ImportItem>> {
        return fetchTokensUseCase()
            .map { data ->
                    val existingAccountNames = data.map { it.name }.toSet()
                    tokens.map { token ->
                        val isDuplicate = existingAccountNames.contains(token.name)
                        ImportItem(
                            token = token,
                            isChecked = !isDuplicate,
                            isDuplicate = isDuplicate,
                        )
                    }.sortedBy { it.token.name }
                }
            .recoverCatching { throw DuplicateCheckException(it) }
    }

    fun updateToken(token: TokenEntry, issuer: String, label: String) = viewModelScope.launch {
        if (uiState.value is UiState.FileLoaded) {
            val list = (uiState.value as UiState.FileLoaded).list
            val updatedList = list.map { item ->
                if (item.token.id == token.id) {
                    val updatedToken = item.token.copy(issuer = issuer, label = label)
                    val isStillaDuplicate = checkIfDuplicate(updatedToken)
                    item.copy(
                        token = updatedToken,
                        isDuplicate = isStillaDuplicate,
                        isChecked = !isStillaDuplicate,
                    )
                } else item
            }
            showRenameTokenDialogWithId.value = null
            _uiState.update { UiState.FileLoaded(updatedList) }
        }
    }

    private suspend fun checkIfDuplicate(token: TokenEntry): Boolean {
        return fetchTokenByNameUseCase(token.issuer, token.label)
            .fold(
                onSuccess = { it != null },
                onFailure = {
                    val current = _uiState.value as? UiState.FileLoaded
                    if (current != null) {
                        _uiState.value = current.copy(errorMessage = getString(Res.string.failed_to_check_duplicates))
                    }
                    true
                }
            )
    }

    fun clearErrorMessage() {
        val current = _uiState.value as? UiState.FileLoaded ?: return
        _uiState.value = current.copy(errorMessage = null)
    }

    private suspend fun messageForFileError(error: Throwable): String {
        logger.e(error.message, error)
        return when (error) {
            is EmptyFileException -> getString(Res.string.empty_content)
            is NoTokensException -> getString(Res.string.no_tokens_to_import)
            is DuplicateCheckException -> getString(Res.string.failed_to_check_duplicates)
            else -> getString(Res.string.failed_to_read_file)
        }
    }

    private class EmptyFileException : Exception()
    private class NoTokensException : Exception()
    private class DuplicateCheckException(cause: Throwable) : Exception(cause)
}
