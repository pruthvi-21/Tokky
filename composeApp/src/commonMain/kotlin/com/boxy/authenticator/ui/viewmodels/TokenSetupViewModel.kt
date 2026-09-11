package com.boxy.authenticator.ui.viewmodels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.account_delete_failed
import boxy_authenticator.composeapp.generated.resources.account_save_failed
import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.core.Logger
import com.boxy.authenticator.core.TokenEntryParser
import com.boxy.authenticator.core.TokenFormValidator
import com.boxy.authenticator.core.TokenLabels
import com.boxy.authenticator.core.encoding.Base32
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.models.enums.AccountEntryMethod
import com.boxy.authenticator.domain.models.enums.OTPType
import com.boxy.authenticator.domain.models.enums.TokenSetupMode
import com.boxy.authenticator.domain.models.form.TokenFormEvent
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.models.otp.OtpInfo
import com.boxy.authenticator.domain.models.otp.SteamInfo
import com.boxy.authenticator.domain.models.otp.TotpInfo
import com.boxy.authenticator.domain.usecases.DeleteTokenUseCase
import com.boxy.authenticator.domain.usecases.FetchTokenByIdUseCase
import com.boxy.authenticator.domain.usecases.FetchLabelsUseCase
import com.boxy.authenticator.domain.usecases.InsertTokenUseCase
import com.boxy.authenticator.domain.usecases.ReplaceExistingTokenUseCase
import com.boxy.authenticator.domain.usecases.UpdateTokenUseCase
import com.boxy.authenticator.ui.state.TokenSetupUiState
import com.boxy.authenticator.ui.state.DataLoadState
import com.boxy.authenticator.utils.TokenNameExistsException
import com.boxy.authenticator.utils.StaleTokenException
import com.boxy.authenticator.utils.cleanSecretKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class TokenSetupViewModel(
    private val settings: SettingsDataStore,
    private val fetchTokenByIdUseCase: FetchTokenByIdUseCase,
    private val fetchLabelsUseCase: FetchLabelsUseCase,
    private val insertTokenUseCase: InsertTokenUseCase,
    private val updateTokenUseCase: UpdateTokenUseCase,
    private val deleteTokenUseCase: DeleteTokenUseCase,
    private val replaceExistingTokenUseCase: ReplaceExistingTokenUseCase,
    private val formValidator: TokenFormValidator,
) : ViewModel() {
    private val logger = Logger("TokenSetupViewModel")

    private var currentToken: TokenEntry? = null

    private var initialUiState = TokenSetupUiState() // only required to check if form is updated
    private val _uiState = MutableStateFlow(initialUiState)
    val uiState = _uiState.asStateFlow()

    val lockSensitiveFields: Boolean
        get() = mutableStateOf(settings.isLockSensitiveFieldsEnabled()).value
                && uiState.value.isInEditMode

    data class DuplicateTokenDialogArgs(
        val show: Boolean,
        val token: TokenEntry? = null,
        val existingToken: TokenEntry? = null,
    )

    fun setStateFromToken(token: TokenEntry?, setupMode: TokenSetupMode) {
        if (token == null) {
            _uiState.value = _uiState.value.copy(editLoadState = DataLoadState.Error("Account not found"))
            return
        }

        currentToken = token
        val type = when (token.otpInfo) {
            is HotpInfo -> OTPType.HOTP
            is SteamInfo -> OTPType.STEAM
            is TotpInfo -> OTPType.TOTP
        }
        val loadedState = _uiState.value.copy(
            issuer = token.issuer,
            label = token.label,
            labels = token.labels,
            availableLabels = TokenLabels.normalize(_uiState.value.availableLabels + token.labels),
            isArchived = token.isArchived,
            thumbnail = token.thumbnail,
            secretKey = Base32.encode(token.otpInfo.secretKey),
            algorithm = token.otpInfo.algorithm,
            digits = token.otpInfo.digits.toString(),
            type = type,
            period = (token.otpInfo as? TotpInfo)?.period?.toString() ?: _uiState.value.period,
            counter = (token.otpInfo as? HotpInfo)?.counter?.toString() ?: _uiState.value.counter,
            isInEditMode = true,
            tokenSetupMode = setupMode,
            editLoadState = DataLoadState.Data(Unit),
            isAlgorithmFieldVisible = type != OTPType.STEAM,
            isDigitsFieldVisible = type != OTPType.STEAM,
            isPeriodFieldVisible = type == OTPType.TOTP,
            isCounterFieldVisible = type == OTPType.HOTP,
        )
        _uiState.value = loadedState
        initialUiState = loadedState
    }

    fun setStateFromAuthUrl(authUrl: String) {
        val token = try {
            TokenEntryParser.buildFromUrl(authUrl)
        } catch (e: Exception) {
            null
        }

        setStateFromToken(token, TokenSetupMode.URL)
    }

    fun loadToken(tokenId: String) {
        if (_uiState.value.editLoadState == DataLoadState.Loading) return
        _uiState.value = _uiState.value.copy(editLoadState = DataLoadState.Loading)
        viewModelScope.launch {
            val result = fetchTokenByIdUseCase(tokenId)
            result.fold(
                onSuccess = { setStateFromToken(it, TokenSetupMode.UPDATE) },
                onFailure = {
                    logger.e("Failed to load token", it)
                    _uiState.value = _uiState.value.copy(
                        editLoadState = DataLoadState.Error(it.message ?: "Unknown error"),
                    )
                },
            )
        }
    }

    fun loadAvailableLabels() {
        viewModelScope.launch {
            fetchLabelsUseCase()
                .onSuccess { summaries ->
                    updateState {
                        copy(
                            availableLabels = TokenLabels.normalize(
                                availableLabels + labels + summaries.map { it.name }
                            )
                        )
                    }
                }
                .onFailure { logger.e("Failed to load available labels", it) }
        }
    }

    fun onEvent(event: TokenFormEvent) {
        if (event !is TokenFormEvent.Submit && _uiState.value.operationError != null) {
            _uiState.value = _uiState.value.copy(operationError = null)
        }
        when (event) {
            is TokenFormEvent.IssuerChanged -> {
                updateState {
                    copy(
                        issuer = event.issuer,
                        validationErrors = validationErrors - "issuer"
                    )
                }
            }

            is TokenFormEvent.LabelChanged -> {
                updateState { copy(label = event.label) }
            }

            is TokenFormEvent.NewLabelChanged -> {
                updateState {
                    copy(
                        newLabel = event.label,
                        validationErrors = validationErrors - "labels",
                    )
                }
            }

            TokenFormEvent.AddLabel -> addPendingLabel()

            is TokenFormEvent.LabelToggled -> {
                updateState {
                    val selected = labels.any { it.equals(event.label, ignoreCase = true) }
                    copy(
                        labels = if (selected) {
                            labels.filterNot { it.equals(event.label, ignoreCase = true) }.toSet()
                        } else {
                            TokenLabels.normalize(labels + event.label)
                        }
                    )
                }
            }

            is TokenFormEvent.AddLabelDialogVisibilityChanged -> {
                updateState {
                    copy(
                        showAddLabelDialog = event.visible,
                        newLabel = if (event.visible) newLabel else "",
                        validationErrors = if (event.visible) validationErrors
                        else validationErrors - "labels",
                    )
                }
            }

            is TokenFormEvent.SecretKeyChanged -> {
                updateState {
                    copy(
                        secretKey = event.secretKey.cleanSecretKey(),
                        validationErrors = validationErrors - "secretKey"
                    )
                }
            }

            is TokenFormEvent.TypeChanged -> {
                updateState { copy(type = event.type) }
                updateFieldVisibilityState()
            }

            is TokenFormEvent.ThumbnailChanged -> {
                updateState { copy(thumbnail = event.thumbnail) }
            }

            is TokenFormEvent.AlgorithmChanged -> {
                updateState { copy(algorithm = event.algorithm) }
            }

            is TokenFormEvent.PeriodChanged -> {
                updateState {
                    copy(
                        period = event.period,
                        validationErrors = validationErrors - "period"
                    )
                }
            }

            is TokenFormEvent.DigitsChanged -> {
                updateState {
                    copy(
                        digits = event.digits,
                        validationErrors = validationErrors - "digits"
                    )
                }
            }

            is TokenFormEvent.CounterChanged -> {
                updateState {
                    copy(
                        counter = event.counter,
                        validationErrors = validationErrors - "counter"
                    )
                }
            }

            is TokenFormEvent.EnableAdvancedOptionsChanged -> {
                updateState { copy(enableAdvancedOptions = event.enableAdvancedOptions) }
            }

            is TokenFormEvent.Submit -> {
                validateInputs(event)
            }
        }
    }

    private fun updateState(newState: TokenSetupUiState.() -> TokenSetupUiState) {
        _uiState.value = _uiState.value.newState()
    }

    private fun addPendingLabel() {
        val rawPending = _uiState.value.newLabel
        if (rawPending.isBlank()) return
        val pending = _uiState.value.availableLabels.firstOrNull {
            it.equals(rawPending.trim(), ignoreCase = true)
        } ?: rawPending
        runCatching { TokenLabels.normalize(_uiState.value.labels + pending) }
            .onSuccess { labels ->
                _uiState.value = _uiState.value.copy(
                    labels = labels,
                    availableLabels = TokenLabels.normalize(
                        _uiState.value.availableLabels + labels
                    ),
                    newLabel = "",
                    showAddLabelDialog = false,
                    validationErrors = _uiState.value.validationErrors - "labels",
                )
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(
                    validationErrors = _uiState.value.validationErrors +
                            ("labels" to LABEL_ERROR),
                )
            }
    }

    private suspend fun handleValidationResult(result: TokenFormValidator.Result): String? {
        return when (result) {
            is TokenFormValidator.Result.Success -> null
            is TokenFormValidator.Result.Failure -> getString(result.errorMessage)
        }
    }

    private fun validateInputs(event: TokenFormEvent.Submit) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            val issuerResult = formValidator.validateIssuer(_uiState.value.issuer)
            val secretKeyResult = formValidator.validateSecretKey(_uiState.value.secretKey)
            val periodResult = formValidator.validatePeriod(_uiState.value.period)
            val counterResult = formValidator.validateCounter(_uiState.value.counter)

            _uiState.value = _uiState.value.copy(
                validationErrors = mapOf(
                    "issuer" to handleValidationResult(issuerResult),
                    "secretKey" to handleValidationResult(secretKeyResult),
                    "period" to handleValidationResult(periodResult),
                    "counter" to handleValidationResult(counterResult)
                )
            )

            val state = _uiState.value
            val tokenLabels = runCatching {
                TokenLabels.normalize(
                    state.labels + listOfNotNull(state.newLabel.takeIf(String::isNotBlank))
                )
            }.getOrElse {
                _uiState.value = _uiState.value.copy(
                    validationErrors = _uiState.value.validationErrors + ("labels" to LABEL_ERROR),
                )
                return@launch
            }

            fun buildOtpInfo(): OtpInfo {
                return when (state.type) {
                    OTPType.TOTP -> {
                        val totpResults =
                            listOf(issuerResult, secretKeyResult, periodResult)
                        val hasError = totpResults.any { it is TokenFormValidator.Result.Failure }
                        if (hasError) throw Exception()

                        TotpInfo(
                            Base32.decode(uiState.value.secretKey),
                            state.algorithm,
                            state.digits.toInt(),
                            state.period.toLong(),
                        )
                    }

                    OTPType.HOTP -> {
                        val hotpResults =
                            listOf(issuerResult, secretKeyResult, counterResult)
                        val hasError = hotpResults.any { it is TokenFormValidator.Result.Failure }
                        if (hasError) throw Exception()

                        HotpInfo(
                            Base32.decode(uiState.value.secretKey),
                            state.algorithm,
                            state.digits.toInt(),
                            state.counter.toLong(),
                        )
                    }

                    OTPType.STEAM -> {
                        val steamResults = listOf(issuerResult, secretKeyResult)
                        val hasError = steamResults.any { it is TokenFormValidator.Result.Failure }
                        if (hasError) throw Exception()

                        SteamInfo(Base32.decode(uiState.value.secretKey))
                    }
                }
            }

            try {
                val otpInfo = buildOtpInfo()

                when (_uiState.value.tokenSetupMode) {
                    TokenSetupMode.NEW,
                    TokenSetupMode.URL,
                        -> {
                        var newToken = TokenEntry.create(
                            issuer = state.issuer,
                            label = state.label,
                            thumbnail = state.thumbnail,
                            otpInfo = otpInfo,
                            addedFrom = AccountEntryMethod.FORM,
                            labels = tokenLabels,
                        )

                        if (_uiState.value.tokenSetupMode == TokenSetupMode.URL) {
                            newToken = newToken.copy(addedFrom = AccountEntryMethod.QR_CODE)
                        }

                        insertToken(newToken, event)
                    }

                    TokenSetupMode.UPDATE -> {
                        val token = currentToken?.copy(
                            issuer = state.issuer,
                            label = state.label,
                            thumbnail = state.thumbnail,
                            otpInfo = otpInfo,
                            labels = tokenLabels,
                        )
                            ?: throw IllegalStateException("No token ID available for update")

                        updateToken(token, event)
                    }
                }
            } catch (e: Exception) {
                logger.e("validateInputs: Exception while validating", e)
                _uiState.value = _uiState.value.copy(operationError = getString(Res.string.account_save_failed))
            }
        }
    }

    private suspend fun insertToken(
        token: TokenEntry,
        event: TokenFormEvent.Submit,
    ) {
        _uiState.value = _uiState.value.copy(isSaving = true, operationError = null)
        insertTokenUseCase(token)
            .onSuccess { event.onComplete() }
            .onFailure { exception ->
                logger.e("insertToken: Failed to insert token", exception)

                if (exception is TokenNameExistsException) {
                    exception.token?.let { event.onDuplicate(token, it) }
                } else {
                    _uiState.value = _uiState.value.copy(
                        operationError = getString(Res.string.account_save_failed),
                    )
                }
            }
        _uiState.value = _uiState.value.copy(isSaving = false)
    }

    private suspend fun updateToken(
        token: TokenEntry,
        event: TokenFormEvent.Submit,
    ) {
        _uiState.value = _uiState.value.copy(isSaving = true, operationError = null)
        updateTokenUseCase(token)
            .onSuccess { event.onComplete() }
            .onFailure {
                logger.e("updateToken: Failed to update token", it)
                _uiState.value = _uiState.value.copy(
                    operationError = when (it) {
                        is TokenNameExistsException, is StaleTokenException -> it.message
                        else -> getString(Res.string.account_save_failed)
                    },
                )
            }
        _uiState.value = _uiState.value.copy(isSaving = false)
    }

    private fun updateFieldVisibilityState() {
        _uiState.value = when (_uiState.value.type) {
            OTPType.TOTP -> _uiState.value.copy(
                isAlgorithmFieldVisible = true,
                isDigitsFieldVisible = true,
                isPeriodFieldVisible = true,
                isCounterFieldVisible = false,
            )

            OTPType.HOTP -> _uiState.value.copy(
                isAlgorithmFieldVisible = true,
                isDigitsFieldVisible = true,
                isPeriodFieldVisible = false,
                isCounterFieldVisible = true,
            )

            OTPType.STEAM -> _uiState.value.copy(
                isAlgorithmFieldVisible = false,
                isDigitsFieldVisible = false,
                isPeriodFieldVisible = false,
                isCounterFieldVisible = false,
            )
        }
    }

    fun isFormUpdated(): Boolean {
        return initialUiState != _uiState.value.copy(
            // ignore these fields
            enableAdvancedOptions = initialUiState.enableAdvancedOptions,
            tokenSetupMode = initialUiState.tokenSetupMode,
            showBackPressDialog = initialUiState.showBackPressDialog,
            showDeleteTokenDialog = initialUiState.showDeleteTokenDialog,
            showArchiveTokenDialog = initialUiState.showArchiveTokenDialog,
            showAddLabelDialog = initialUiState.showAddLabelDialog,
            showDuplicateTokenDialog = initialUiState.showDuplicateTokenDialog,
            availableLabels = initialUiState.availableLabels,
            editLoadState = initialUiState.editLoadState,
            isSaving = initialUiState.isSaving,
            operationError = initialUiState.operationError,
        )
    }

    suspend fun deleteToken(): Boolean {
        val token = currentToken ?: return false
        _uiState.value = _uiState.value.copy(isSaving = true, operationError = null)
        val result = deleteTokenUseCase(token.id)
        result.onFailure { logger.e("Failed to delete token", it) }
        _uiState.value = _uiState.value.copy(
            isSaving = false,
            operationError = if (result.isFailure) {
                getString(Res.string.account_delete_failed)
            } else null,
        )
        return result.isSuccess
    }

    suspend fun toggleArchiveToken(): Boolean {
        val token = currentToken ?: return false
        _uiState.value = _uiState.value.copy(isSaving = true, operationError = null)
        val result = updateTokenUseCase(token.copy(isArchived = !token.isArchived))
        result.onFailure { logger.e("Failed to change token archive state", it) }
        _uiState.value = _uiState.value.copy(
            isSaving = false,
            operationError = if (result.isFailure) {
                getString(Res.string.account_save_failed)
            } else null,
        )
        return result.isSuccess
    }

    suspend fun replaceExistingToken(existingToken: TokenEntry, token: TokenEntry): Boolean {
        _uiState.value = _uiState.value.copy(isSaving = true, operationError = null)
        val result = replaceExistingTokenUseCase(existingToken, token)
        result.onFailure { logger.e("Failed to replace token", it) }
        _uiState.value = _uiState.value.copy(
            isSaving = false,
            operationError = if (result.isFailure) {
                getString(Res.string.account_save_failed)
            } else null,
        )
        return result.isSuccess
    }

    fun showBackPressDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showBackPressDialog = show)
    }

    fun showDeleteTokenDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDeleteTokenDialog = show)
    }

    fun showArchiveTokenDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showArchiveTokenDialog = show)
    }

    fun showDuplicateTokenDialog(args: DuplicateTokenDialogArgs) {
        _uiState.value = _uiState.value.copy(showDuplicateTokenDialog = args)
    }

    private companion object {
        const val LABEL_ERROR = "Labels must be 1–${TokenLabels.MAX_LENGTH} characters."
    }
}
