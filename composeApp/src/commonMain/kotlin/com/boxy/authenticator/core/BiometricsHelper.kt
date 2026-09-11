package com.boxy.authenticator.core

import dev.icerock.moko.biometry.BiometryAuthenticator
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.CancellationException

class BiometricsHelper {
    private val logger = Logger("BiometricsHelper")

    private var biometryAuthenticator: BiometryAuthenticator? = null

    fun init(biometryAuthenticator: BiometryAuthenticator) {
        this.biometryAuthenticator = biometryAuthenticator
    }

    suspend fun promptForBiometrics(
        title: String,
        reason: String,
        failureButtonText: String,
    ): Boolean {
        try {
            val isSuccess = biometryAuthenticator?.checkBiometryAuthentication(
                requestTitle = title.desc(),
                requestReason = reason.desc(),
                failureButtonText = failureButtonText.desc(),
                allowDeviceCredentials = false,
            )
            return isSuccess == true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Exception) {
            logger.e(throwable.message, throwable)
            return false
        }
    }

    fun isBiometricAvailable(): Boolean {
        return try {
            biometryAuthenticator?.isBiometricAvailable() == true
        } catch (error: Exception) {
            logger.e(error.message, error)
            false
        }
    }
}
