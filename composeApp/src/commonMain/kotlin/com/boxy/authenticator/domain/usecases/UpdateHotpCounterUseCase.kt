package com.boxy.authenticator.domain.usecases

import com.boxy.authenticator.core.runSuspendCatching
import com.boxy.authenticator.domain.repository.TokenRepository

class UpdateHotpCounterUseCase(
    private val tokenRepository: TokenRepository,
) {
    suspend operator fun invoke(tokenId: String, counter: Long) = runSuspendCatching {
        tokenRepository.updateHotpCounter(tokenId, counter)
    }
}
