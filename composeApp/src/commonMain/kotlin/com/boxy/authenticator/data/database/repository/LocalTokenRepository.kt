package com.boxy.authenticator.data.database.repository

import com.boxy.authenticator.domain.database.dao.TokenDao
import com.boxy.authenticator.domain.database.repository.TokenRepository
import com.boxy.authenticator.domain.models.TokenEntry
import kotlin.time.Clock

class LocalTokenRepository(private val tokenDao: TokenDao) : TokenRepository {
    override fun getAllTokens(): List<TokenEntry> {
        return tokenDao.getAllTokens()
    }

    override fun getTokensCount(): Long {
        return tokenDao.getTokensCount()
    }

    override fun findTokenWithId(tokenId: String): TokenEntry {
        return tokenDao.findTokenWithId(tokenId)
    }

    override fun findTokenWithName(issuer: String, label: String): TokenEntry? {
        return tokenDao.findTokenWithName(issuer, label)
    }

    override fun insertToken(token: TokenEntry) {
        tokenDao.insertToken(token)
    }

    override fun insertTokens(tokens: List<TokenEntry>) {
        tokenDao.insertTokens(tokens)
    }

    override fun deleteToken(tokenId: String) {
        tokenDao.deleteToken(tokenId)
    }

    override fun updateToken(token: TokenEntry) {
        tokenDao.updateToken(token.copy(updatedOn = Clock.System.now().toEpochMilliseconds()))
    }

    override fun replaceTokenWith(id: String, token: TokenEntry) {
        tokenDao.replaceTokenWith(id, token)
    }

    override fun updateHotpCounter(tokenId: String, counter: Long) {
        tokenDao.updateHotpCounter(tokenId, counter)
    }
}
