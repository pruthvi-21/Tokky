package com.boxy.authenticator.domain.usecases

import com.boxy.authenticator.core.runSuspendCatching
import com.boxy.authenticator.domain.repository.TokenRepository
import com.boxy.authenticator.domain.models.TokenEntry

class UpdateTokenUseCase(
    private val tokenRepository: TokenRepository,
) {
    suspend operator fun invoke(token: TokenEntry) = runSuspendCatching {
        tokenRepository.updateToken(token)
    }
}
