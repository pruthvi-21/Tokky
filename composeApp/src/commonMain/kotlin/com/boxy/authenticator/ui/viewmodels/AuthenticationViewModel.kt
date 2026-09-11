package com.boxy.authenticator.ui.viewmodels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.incorrect_password
import com.boxy.authenticator.core.Logger
import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.core.crypto.PasscodeManager
import com.boxy.authenticator.ui.state.AuthenticationUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class AuthenticationViewModel(
    private val settings: SettingsDataStore,
    private val passcodeManager: PasscodeManager,
) : ViewModel() {
    private val logger = Logger("AuthenticationViewModel")
    private val _uiState = MutableStateFlow(AuthenticationUiState())
    val uiState = _uiState.asStateFlow()

    val isPinPadVisible = mutableStateOf(settings.isLockscreenPinPadEnabled())
    val isBiometricUnlockEnabled: Boolean
        get() = settings.isBiometricUnlockEnabled()

    fun updatePinPadVisibility() {
        isPinPadVisible.value = settings.isLockscreenPinPadEnabled()
    }

    fun verifyPassword(onComplete: (Boolean) -> Unit) {
        if (_uiState.value.isVerifyingPassword) return
        val password = _uiState.value.password
        _uiState.value = _uiState.value.copy(isVerifyingPassword = true)
        viewModelScope.launch {
            try {
                val verification = passcodeManager.verify(password, settings.getPasscodeHash())
                val isValid = verification != PasscodeManager.Verification.INVALID
                if (isValid && verification == PasscodeManager.Verification.LEGACY) {
                    try {
                        settings.updatePasscodeHash(passcodeManager.create(password))
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        // Migration is opportunistic; a transient write failure must not reject a
                        // password that was already verified successfully.
                        logger.e("Unable to upgrade the app-lock credential", error)
                    }
                }
                _uiState.value = _uiState.value.copy(
                    password = "",
                    passwordError = if (isValid) null else incorrectPasswordMessage(),
                )
                onComplete(isValid)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    password = "",
                    passwordError = incorrectPasswordMessage(),
                )
                onComplete(false)
            } finally {
                _uiState.value = _uiState.value.copy(isVerifyingPassword = false)
            }
        }
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            passwordError = null,
        )
    }

    private suspend fun incorrectPasswordMessage(): String = try {
        getString(Res.string.incorrect_password)
    } catch (_: Exception) {
        "Incorrect password"
    }
}
