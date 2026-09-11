package com.boxy.authenticator.ui.viewmodels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.empty_content
import boxy_authenticator.composeapp.generated.resources.failed_to_check_duplicates
import boxy_authenticator.composeapp.generated.resources.failed_to_decrypt
import boxy_authenticator.composeapp.generated.resources.failed_to_parse_file
import boxy_authenticator.composeapp.generated.resources.failed_to_read_file
import boxy_authenticator.composeapp.generated.resources.file_too_large
import boxy_authenticator.composeapp.generated.resources.import_failed
import boxy_authenticator.composeapp.generated.resources.invalid_aegis_backup
import boxy_authenticator.composeapp.generated.resources.no_tokens_to_import
import boxy_authenticator.composeapp.generated.resources.unsupported_aegis_encryption
import boxy_authenticator.composeapp.generated.resources.unsupported_aegis_token_type
import boxy_authenticator.composeapp.generated.resources.unsupported_aegis_version
import com.boxy.authenticator.core.ImportedTokenValidator
import com.boxy.authenticator.core.Logger
import com.boxy.authenticator.core.TokenEntryParser
import com.boxy.authenticator.core.accountNameKey
import com.boxy.authenticator.core.crypto.Crypto
import com.boxy.authenticator.core.importing.AegisImporter
import com.boxy.authenticator.core.importing.InvalidAegisPasswordException
import com.boxy.authenticator.core.importing.UnsupportedAegisEncryptionException
import com.boxy.authenticator.core.importing.UnsupportedAegisTokenTypeException
import com.boxy.authenticator.core.importing.UnsupportedAegisVersionException
import com.boxy.authenticator.core.serialization.BoxyJson
import com.boxy.authenticator.domain.models.ExportableTokenEntry
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.usecases.FetchTokenByNameUseCase
import com.boxy.authenticator.domain.usecases.FetchTokensUseCase
import com.boxy.authenticator.domain.usecases.InsertTokensUseCase
import com.boxy.authenticator.utils.Constants
import com.boxy.authenticator.utils.name
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import io.github.vinceglb.filekit.core.pickFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

class ImportTokensViewModel(
    private val fetchTokensUseCase: FetchTokensUseCase,
    private val insertTokensUseCase: InsertTokensUseCase,
    private val fetchTokenByNameUseCase: FetchTokenByNameUseCase,
) : ViewModel() {
    private val logger = Logger("ImportTokensViewModel")

    enum class ImportFormat {
        ENCRYPTED_BACKUP,
        PLAIN_TEXT,
        AEGIS,
    }

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

        data class RequestPassword(val file: PlatformFile, val format: ImportFormat) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val showRenameTokenDialogWithId = mutableStateOf<String?>(null)
    val showDuplicateWarningDialog = mutableStateOf(false)

    fun setInitialState() {
        _uiState.update { UiState.Initial }
    }

    fun pickFile(format: ImportFormat) = viewModelScope.launch {
        val extensions = when (format) {
            ImportFormat.ENCRYPTED_BACKUP -> listOf(Constants.EXPORT_ENCRYPTED_FILE_EXTENSION)
            ImportFormat.PLAIN_TEXT -> listOf(Constants.EXPORT_PLAIN_FILE_EXTENSION)
            ImportFormat.AEGIS -> listOf("json")
        }
        val file = runCatching { FileKit.pickFile(type = PickerType.File(extensions)) }
            .onFailure { logger.e("File selection failed", it) }
            .getOrElse {
                _uiState.value = UiState.Error(getString(Res.string.failed_to_read_file))
                return@launch
            } ?: run {
            return@launch
        }

        if (format == ImportFormat.ENCRYPTED_BACKUP) {
            _uiState.update { UiState.RequestPassword(file, format) }
        } else {
            _uiState.update { UiState.Loading }
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val fileContent = readImportFile(file)
                    if (fileContent.isEmpty()) throw EmptyFileException()
                    val text = fileContent.decodeToString(throwOnInvalidSequence = true)
                    if (format == ImportFormat.AEGIS) {
                        when (val inspection = AegisImporter.inspect(text)) {
                            is AegisImporter.Inspection.Plain -> {
                                val tokens = AegisImporter.tokens(inspection)
                                if (tokens.isEmpty()) throw NoTokensException()
                                UiState.FileLoaded(buildImportListFromTokens(tokens).getOrThrow())
                            }

                            is AegisImporter.Inspection.Encrypted -> UiState.RequestPassword(file, format)
                        }
                    } else {
                        val tokens = decodePlainContent(text)
                        if (tokens.isEmpty()) throw NoTokensException()
                        UiState.FileLoaded(buildImportListFromTokens(tokens).getOrThrow())
                    }
                }
            }
            _uiState.value = result.fold(
                onSuccess = { it },
                onFailure = {
                    UiState.Error(if (format == ImportFormat.AEGIS) messageForAegisError(it) else messageForFileError(it))
                },
            )
        }
    }

    private fun decodePlainContent(fileContent: String): List<TokenEntry> {
        val lines = fileContent.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        require(lines.size <= Constants.MAX_IMPORT_TOKENS) { "Too many accounts in backup." }
        return lines.map { line ->
            require(line.length <= MAX_PLAIN_TOKEN_LENGTH) { "Token URL is too long." }
            ImportedTokenValidator.validate(TokenEntryParser.buildFromUrl(line))
        }
    }

    fun decodeEncryptedContent(
        file: PlatformFile,
        password: String,
        format: ImportFormat,
    ) = viewModelScope.launch {
        _uiState.value = UiState.Loading
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val fileContent = readImportFile(file)
                val list = if (format == ImportFormat.AEGIS) {
                    val inspection = AegisImporter.inspect(
                        fileContent.decodeToString(throwOnInvalidSequence = true)
                    ) as? AegisImporter.Inspection.Encrypted ?: throw IllegalArgumentException()
                    AegisImporter.decrypt(inspection, password)
                } else {
                    val decryptedData = Crypto.decrypt(password, fileContent)
                    val exportedTokens = BoxyJson.decodeFromString<List<ExportableTokenEntry>>(decryptedData)
                    require(exportedTokens.size <= Constants.MAX_IMPORT_TOKENS) {
                        "Too many accounts in backup."
                    }
                    exportedTokens.map { ImportedTokenValidator.validate(it.toTokenEntry()) }
                }
                if (list.isEmpty()) throw NoTokensException()
                buildImportListFromTokens(list).getOrThrow()
            }
        }
        _uiState.value = result.fold(
            onSuccess = { UiState.FileLoaded(it) },
            onFailure = {
                if (it is DuplicateCheckException) {
                    logger.e("Failed to compare encrypted backup accounts with the vault")
                } else {
                    logger.e("Encrypted backup validation failed")
                }
                UiState.Error(
                    if (format == ImportFormat.AEGIS) messageForAegisError(it) else {
                        if (it is DuplicateCheckException) getString(Res.string.failed_to_check_duplicates)
                        else if (it is NoTokensException) getString(Res.string.no_tokens_to_import)
                        else getString(Res.string.failed_to_decrypt)
                    }
                )
            },
        )
    }

    fun importAccounts(onComplete: (Boolean) -> Unit) = viewModelScope.launch {
        val current = _uiState.value as? UiState.FileLoaded ?: return@launch
        if (current.isImporting) return@launch
        val tokensToInsert = current.list
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
                    currentState.copy(list = updatedList)
                }

                else -> currentState
            }
        }
    }

    private suspend fun buildImportListFromTokens(tokens: List<TokenEntry>): Result<List<ImportItem>> {
        return fetchTokensUseCase()
            .map { data ->
                val existingAccountNames = data.map { it.accountKey() }.toSet()
                val importedAccountNames = mutableSetOf<Pair<String, String>>()
                tokens.map { token ->
                    val accountKey = token.accountKey()
                    val isDuplicate = accountKey in existingAccountNames ||
                            !importedAccountNames.add(accountKey)
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
            _uiState.value = (uiState.value as? UiState.FileLoaded)?.copy(list = updatedList)
                ?: return@launch
        }
    }

    private suspend fun checkIfDuplicate(token: TokenEntry): Boolean {
        val duplicateInSelectedFile = (_uiState.value as? UiState.FileLoaded)
            ?.list
            ?.any {
                it.token.id != token.id &&
                        it.token.accountKey() == token.accountKey()
            } == true
        if (duplicateInSelectedFile) return true

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
        if (error is DuplicateCheckException) {
            logger.e("Failed to compare backup accounts with the vault")
        } else {
            logger.e("Backup file validation failed")
        }
        return when (error) {
            is EmptyFileException -> getString(Res.string.empty_content)
            is NoTokensException -> getString(Res.string.no_tokens_to_import)
            is DuplicateCheckException -> getString(Res.string.failed_to_check_duplicates)
            is FileTooLargeException -> getString(Res.string.file_too_large)
            else -> getString(Res.string.failed_to_parse_file)
        }
    }

    private suspend fun messageForAegisError(error: Throwable): String = when (error) {
        is UnsupportedAegisVersionException -> getString(Res.string.unsupported_aegis_version)
        is UnsupportedAegisTokenTypeException -> getString(Res.string.unsupported_aegis_token_type)
        is UnsupportedAegisEncryptionException -> getString(Res.string.unsupported_aegis_encryption)
        is InvalidAegisPasswordException -> getString(Res.string.failed_to_decrypt)
        is NoTokensException -> getString(Res.string.no_tokens_to_import)
        is DuplicateCheckException -> getString(Res.string.failed_to_check_duplicates)
        is FileTooLargeException -> getString(Res.string.file_too_large)
        else -> getString(Res.string.invalid_aegis_backup)
    }

    private class EmptyFileException : Exception()
    private class NoTokensException : Exception()
    private class DuplicateCheckException(cause: Throwable) : Exception(cause)

    private suspend fun readImportFile(file: PlatformFile): ByteArray {
        val reportedSize = file.getSize()
        if (reportedSize != null && reportedSize > Constants.MAX_IMPORT_FILE_BYTES) {
            throw FileTooLargeException()
        }
        if (!file.supportsStreams()) {
            val bytes = file.readBytes()
            if (bytes.size.toLong() > Constants.MAX_IMPORT_FILE_BYTES) throw FileTooLargeException()
            return bytes
        }

        val limit = Constants.MAX_IMPORT_FILE_BYTES.toInt()
        val output = ByteArray(limit + 1)
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        var totalBytes = 0
        val stream = file.getStream()
        try {
            while (totalBytes <= limit) {
                val bytesRead = stream.readInto(buffer, buffer.size)
                if (bytesRead <= 0) break
                val bytesToCopy = minOf(bytesRead, output.size - totalBytes)
                buffer.copyInto(output, totalBytes, 0, bytesToCopy)
                totalBytes += bytesToCopy
                if (totalBytes > limit) throw FileTooLargeException()
            }
        } finally {
            stream.close()
        }
        return output.copyOf(totalBytes)
    }

    private fun TokenEntry.accountKey(): Pair<String, String> =
        accountNameKey(issuer, label)

    private class FileTooLargeException : Exception()

    private companion object {
        const val MAX_PLAIN_TOKEN_LENGTH = 16_384
        const val STREAM_BUFFER_SIZE = 8_192
    }
}
