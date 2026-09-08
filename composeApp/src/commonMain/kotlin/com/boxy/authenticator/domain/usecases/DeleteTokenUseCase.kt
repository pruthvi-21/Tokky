package com.boxy.authenticator.domain.usecases

import com.boxy.authenticator.core.runSuspendCatching
import com.boxy.authenticator.domain.repository.TokenRepository

class DeleteTokenUseCase(
    private val tokenRepository: TokenRepository,
) {
    suspend operator fun invoke(tokenId: String) = runSuspendCatching {
        tokenRepository.deleteToken(tokenId)
    }
}
