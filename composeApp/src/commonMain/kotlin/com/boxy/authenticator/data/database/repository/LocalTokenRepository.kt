package com.boxy.authenticator.data.database.repository

import com.boxy.authenticator.data.database.dao.TokenDao
import com.boxy.authenticator.domain.repository.TokenRepository
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.models.LabelSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class LocalTokenRepository(
    private val tokenDao: TokenDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TokenRepository {
    override suspend fun getAllTokens(): List<TokenEntry> = withContext(dispatcher) {
        tokenDao.getAllTokens()
    }

    override suspend fun getRecycledTokens(): List<TokenEntry> = withContext(dispatcher) {
        tokenDao.getRecycledTokens()
    }

    override suspend fun getTokensCount(): Long = withContext(dispatcher) {
        tokenDao.getTokensCount()
    }

    override suspend fun findTokenWithId(tokenId: String): TokenEntry = withContext(dispatcher) {
        tokenDao.findTokenWithId(tokenId)
    }

    override suspend fun findTokenWithName(issuer: String, label: String): TokenEntry? =
        withContext(dispatcher) { tokenDao.findTokenWithName(issuer, label) }

    override suspend fun insertToken(token: TokenEntry) = withContext(dispatcher) {
        tokenDao.insertToken(token)
    }

    override suspend fun insertTokens(tokens: List<TokenEntry>) = withContext(dispatcher) {
        tokenDao.insertTokens(tokens)
    }

    override suspend fun deleteToken(tokenId: String) = withContext(dispatcher) {
        tokenDao.moveTokensToRecycleBin(
            setOf(tokenId),
            Clock.System.now().toEpochMilliseconds(),
        )
    }

    override suspend fun deleteTokens(tokenIds: Set<String>) = withContext(dispatcher) {
        tokenDao.moveTokensToRecycleBin(
            tokenIds,
            Clock.System.now().toEpochMilliseconds(),
        )
    }

    override suspend fun restoreTokens(tokenIds: Set<String>) = withContext(dispatcher) {
        tokenDao.restoreTokens(tokenIds, Clock.System.now().toEpochMilliseconds())
    }

    override suspend fun permanentlyDeleteTokens(tokenIds: Set<String>) = withContext(dispatcher) {
        tokenDao.permanentlyDeleteTokens(tokenIds)
    }

    override suspend fun updateToken(token: TokenEntry) = withContext(dispatcher) {
        // The DAO compares the original revision before assigning the next timestamp.
        tokenDao.updateToken(token)
    }

    override suspend fun updateTokens(tokens: List<TokenEntry>) = withContext(dispatcher) {
        tokenDao.updateTokens(tokens)
    }

    override suspend fun replaceTokenWith(id: String, token: TokenEntry) = withContext(dispatcher) {
        tokenDao.replaceTokenWith(id, token)
    }

    override suspend fun updateHotpCounter(tokenId: String, counter: Long) = withContext(dispatcher) {
        tokenDao.updateHotpCounter(
            tokenId = tokenId,
            counter = counter,
            updatedOn = Clock.System.now().toEpochMilliseconds(),
        )
    }

    override suspend fun getLabels(): List<LabelSummary> = withContext(dispatcher) {
        tokenDao.getLabels()
    }

    override suspend fun renameLabel(oldName: String, newName: String) = withContext(dispatcher) {
        tokenDao.renameLabel(oldName, newName, Clock.System.now().toEpochMilliseconds())
    }

    override suspend fun deleteLabel(name: String) = withContext(dispatcher) {
        tokenDao.deleteLabel(name, Clock.System.now().toEpochMilliseconds())
    }
}
