package com.boxy.authenticator.domain.usecases

import com.boxy.authenticator.core.runSuspendCatching
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.repository.TokenRepository

class FetchTokenByNameUseCase(
    private val tokenRepository: TokenRepository,
) {
    suspend operator fun invoke(
        issuer: String,
        label: String,
    ): Result<TokenEntry?> = runSuspendCatching {
        tokenRepository.findTokenWithName(issuer, label)
    }
}
