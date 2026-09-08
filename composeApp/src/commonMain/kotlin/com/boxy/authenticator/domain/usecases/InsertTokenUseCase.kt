package com.boxy.authenticator.domain.usecases

import com.boxy.authenticator.core.runSuspendCatching
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.repository.TokenRepository
import com.boxy.authenticator.utils.TokenNameExistsException

class InsertTokenUseCase(
    private val tokenRepository: TokenRepository,
) {
    suspend operator fun invoke(token: TokenEntry): Result<Unit> = runSuspendCatching {
        val existingToken = tokenRepository.findTokenWithName(token.issuer, token.label)

        if (existingToken != null && existingToken.id != token.id) {
            throw TokenNameExistsException(existingToken, "Token already exists.")
        }

        tokenRepository.insertToken(token)
    }
}
