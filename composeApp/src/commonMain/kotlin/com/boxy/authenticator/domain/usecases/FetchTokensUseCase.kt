package com.boxy.authenticator.domain.usecases

import com.boxy.authenticator.core.runSuspendCatching
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.repository.TokenRepository

class FetchTokensUseCase(
    private val tokenRepository: TokenRepository
) {
    suspend operator fun invoke(): Result<List<TokenEntry>> = runSuspendCatching {
        tokenRepository.getAllTokens()
    }
}
