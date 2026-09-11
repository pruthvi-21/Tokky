package com.boxy.authenticator.domain.repository

import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.models.LabelSummary

interface TokenRepository {
    suspend fun getAllTokens(): List<TokenEntry>
    suspend fun getRecycledTokens(): List<TokenEntry>
    suspend fun getTokensCount(): Long
    suspend fun findTokenWithId(tokenId: String): TokenEntry
    suspend fun findTokenWithName(issuer: String, label: String): TokenEntry?
    suspend fun insertTokens(tokens: List<TokenEntry>)
    suspend fun insertToken(token: TokenEntry)
    suspend fun deleteToken(tokenId: String)
    suspend fun deleteTokens(tokenIds: Set<String>)
    suspend fun restoreTokens(tokenIds: Set<String>)
    suspend fun permanentlyDeleteTokens(tokenIds: Set<String>)
    /** updatedOn is the revision read by the caller; stale or recycled entries must fail atomically. */
    suspend fun updateToken(token: TokenEntry)
    /** All entries must match their persisted updatedOn revisions, or the entire batch fails. */
    suspend fun updateTokens(tokens: List<TokenEntry>)
    suspend fun replaceTokenWith(id: String, token: TokenEntry)
    suspend fun updateHotpCounter(tokenId: String, counter: Long)
    suspend fun getLabels(): List<LabelSummary>
    suspend fun renameLabel(oldName: String, newName: String)
    suspend fun deleteLabel(name: String)
}
