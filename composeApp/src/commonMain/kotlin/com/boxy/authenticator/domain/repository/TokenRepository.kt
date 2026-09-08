package com.boxy.authenticator.domain.repository

import com.boxy.authenticator.domain.models.TokenEntry

interface TokenRepository {
    suspend fun getAllTokens(): List<TokenEntry>
    suspend fun getTokensCount(): Long
    suspend fun findTokenWithId(tokenId: String): TokenEntry
    suspend fun findTokenWithName(issuer: String, label: String): TokenEntry?
    suspend fun insertTokens(tokens: List<TokenEntry>)
    suspend fun insertToken(token: TokenEntry)
    suspend fun deleteToken(tokenId: String)
    suspend fun updateToken(token: TokenEntry)
    suspend fun replaceTokenWith(id: String, token: TokenEntry)
    suspend fun updateHotpCounter(tokenId: String, counter: Long)
}
