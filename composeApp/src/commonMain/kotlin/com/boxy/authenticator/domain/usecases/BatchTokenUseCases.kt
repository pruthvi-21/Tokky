package com.boxy.authenticator.domain.usecases

import com.boxy.authenticator.core.runSuspendCatching
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.repository.TokenRepository

class UpdateTokensUseCase(private val repository: TokenRepository) {
    suspend operator fun invoke(tokens: List<TokenEntry>): Result<Unit> = runSuspendCatching {
        repository.updateTokens(tokens)
    }
}

class DeleteTokensUseCase(private val repository: TokenRepository) {
    suspend operator fun invoke(tokenIds: Set<String>): Result<Unit> = runSuspendCatching {
        repository.deleteTokens(tokenIds)
    }
}
