package com.boxy.authenticator.domain.usecases

import com.boxy.authenticator.core.runSuspendCatching
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.repository.TokenRepository

class FetchRecycledTokensUseCase(private val repository: TokenRepository) {
    suspend operator fun invoke(): Result<List<TokenEntry>> = runSuspendCatching {
        repository.getRecycledTokens()
    }
}

class RestoreTokensUseCase(private val repository: TokenRepository) {
    suspend operator fun invoke(tokenIds: Set<String>): Result<Unit> = runSuspendCatching {
        repository.restoreTokens(tokenIds)
    }
}

class PermanentlyDeleteTokensUseCase(private val repository: TokenRepository) {
    suspend operator fun invoke(tokenIds: Set<String>): Result<Unit> = runSuspendCatching {
        repository.permanentlyDeleteTokens(tokenIds)
    }
}
