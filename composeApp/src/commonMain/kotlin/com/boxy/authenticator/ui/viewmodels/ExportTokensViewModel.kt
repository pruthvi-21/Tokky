package com.boxy.authenticator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.core.Logger
import com.boxy.authenticator.core.crypto.Crypto
import com.boxy.authenticator.core.serialization.BoxyJson
import com.boxy.authenticator.domain.models.ExportableTokenEntry
import com.boxy.authenticator.domain.models.generateOtpAuthUrl
import com.boxy.authenticator.domain.usecases.FetchTokensUseCase
import com.boxy.authenticator.ui.state.ExportUiState
import com.boxy.authenticator.ui.state.DataLoadState
import com.boxy.authenticator.utils.Constants
import com.boxy.authenticator.utils.Constants.EXPORT_ENCRYPTED_FILE_EXTENSION
import com.boxy.authenticator.utils.Constants.EXPORT_PLAIN_FILE_EXTENSION
import io.github.vinceglb.filekit.core.FileKit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Clock

class ExportTokensViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val fetchTokensUseCase: FetchTokensUseCase,
) : ViewModel() {
    private val logger = Logger("ExportTokensViewModel")

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState = _uiState.asStateFlow()

    fun loadAllTokens() {
        if (_uiState.value.tokensState == DataLoadState.Loading) return
        _uiState.value = _uiState.value.copy(
            tokensState = DataLoadState.Loading,
        )

        viewModelScope.launch {
            fetchTokensUseCase().fold(
                onSuccess = {
                _uiState.value = _uiState.value.copy(
                    tokensState = DataLoadState.Data(it),
                )
            },
            onFailure = {
                logger.e(it.message, it)
                _uiState.value = _uiState.value.copy(
                    tokensState = DataLoadState.Error(it.message ?: "Unknown error"),
                )
            }
            )
        }
    }

    fun showPlainTextWarningDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showPlainTextWarningDialog = show)
    }

    fun showSetPasswordDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSetPasswordDialog = show)
    }

    fun exportToPlainTextFile(onDone: (Boolean) -> Unit) = viewModelScope.launch {
        if (_uiState.value.isExporting) return@launch
        val tokens = (_uiState.value.tokensState as? DataLoadState.Data)?.value ?: return@launch
        _uiState.value = _uiState.value.copy(isExporting = true)
        val status = runCatching {
            val data = withContext(Dispatchers.Default) {
                tokens.joinToString("\n") { it.generateOtpAuthUrl() }.encodeToByteArray()
            }
            saveToFile(data, EXPORT_PLAIN_FILE_EXTENSION)
        }.onFailure { logger.e(it.message, it) }.getOrDefault(false)
        _uiState.value = _uiState.value.copy(isExporting = false)
        onDone(status)
    }

    fun exportToEncryptedFile(password: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        if (_uiState.value.isExporting) return@launch
        val tokens = (_uiState.value.tokensState as? DataLoadState.Data)?.value ?: return@launch
        _uiState.value = _uiState.value.copy(isExporting = true)
        val status = runCatching {
            val encryptedExportData = withContext(Dispatchers.Default) {
                val tokensJsonArray = JsonArray(tokens.map { token ->
                    BoxyJson.encodeToJsonElement(ExportableTokenEntry.fromTokenEntry(token))
                })
                val exportData = BoxyJson.encodeToString(tokensJsonArray)
                Crypto.encrypt(password, exportData)
            }
            saveToFile(encryptedExportData, EXPORT_ENCRYPTED_FILE_EXTENSION)
        }.onFailure { logger.e(it.message, it) }.getOrDefault(false)
        _uiState.value = _uiState.value.copy(isExporting = false)
        onDone(status)
    }

    private suspend fun saveToFile(data: ByteArray, extension: String): Boolean {
        val file = FileKit.saveFile(
            baseName = buildFileName(),
            extension = extension,
            bytes = data,
        )

        if (file != null) {
            val currentTimeMillis = Clock.System.now().toEpochMilliseconds()
            runCatching {
                settingsDataStore.setLastBackupTimestamp(currentTimeMillis)
            }.onFailure {
                logger.e("Backup was saved, but its timestamp could not be recorded", it)
            }
        }

        return file != null
    }

    private fun buildFileName(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return Constants.EXPORT_FILE_NAME_PREFIX +
                now.year +
                (now.month.ordinal + 1).toString().padStart(2, '0') +
                now.day.toString().padStart(2, '0') +
                "_" +
                now.hour.toString().padStart(2, '0') +
                now.minute.toString().padStart(2, '0') +
                now.second.toString().padStart(2, '0')
    }
}
