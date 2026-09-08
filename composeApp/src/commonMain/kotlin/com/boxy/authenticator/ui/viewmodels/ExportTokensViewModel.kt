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
import com.boxy.authenticator.utils.Constants
import com.boxy.authenticator.utils.Constants.EXPORT_ENCRYPTED_FILE_EXTENSION
import com.boxy.authenticator.utils.Constants.EXPORT_FILE_EXTENSION
import io.github.vinceglb.filekit.core.FileKit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        _uiState.value = _uiState.value.copy(
            tokensFetchError = false,
            tokens = emptyList(),
        )

        fetchTokensUseCase().fold(
            onSuccess = {
                _uiState.value = _uiState.value.copy(
                    tokensFetchError = false,
                    tokens = it,
                )
            },
            onFailure = {
                logger.e(it.message, it)
                _uiState.value = _uiState.value.copy(
                    tokensFetchError = true,
                    tokens = emptyList(),
                )
            }
        )
    }

    fun showPlainTextWarningDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showPlainTextWarningDialog = show)
    }

    fun showSetPasswordDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSetPasswordDialog = show)
    }

    fun exportToPlainTextFile(onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val exportData = _uiState.value.tokens.joinToString("\n") { it.generateOtpAuthUrl() }
        val status = saveToFile(exportData.encodeToByteArray(), EXPORT_FILE_EXTENSION)
        onDone(status)
    }

    fun exportToBoxyFile(password: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val tokensJsonArray = JsonArray(_uiState.value.tokens.map { token ->
            BoxyJson.encodeToJsonElement(ExportableTokenEntry.fromTokenEntry(token))
        })
        val exportData = BoxyJson.encodeToString(tokensJsonArray)
        val encryptedExportData = Crypto.encrypt(password, exportData)
        val status = saveToFile(encryptedExportData, EXPORT_ENCRYPTED_FILE_EXTENSION)
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
            settingsDataStore.setLastBackupTimestamp(currentTimeMillis)
        }

        return file != null
    }

    private fun buildFileName(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return Constants.EXPORT_FILE_NAME_PREFIX +
                now.year +
                now.monthNumber.toString().padStart(2, '0') +
                now.dayOfMonth.toString().padStart(2, '0') +
                "_" +
                now.hour.toString().padStart(2, '0') +
                now.minute.toString().padStart(2, '0') +
                now.second.toString().padStart(2, '0')
    }
}
