package com.boxy.authenticator.domain.usecases

import com.boxy.authenticator.core.runSuspendCatching
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.repository.TokenRepository

class ReplaceExistingTokenUseCase(
    private val tokenRepository: TokenRepository,
) {
    suspend operator fun invoke(existingToken: TokenEntry, token: TokenEntry) = runSuspendCatching {
        tokenRepository.replaceTokenWith(existingToken.id, token)
    }
}
