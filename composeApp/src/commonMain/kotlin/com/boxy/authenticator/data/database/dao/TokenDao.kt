package com.boxy.authenticator.data.database.dao

import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.models.LabelSummary

interface TokenDao {
    fun getAllTokens(): List<TokenEntry>
    fun getTokensCount(): Long
    fun insertToken(token: TokenEntry)
    fun deleteToken(tokenId: String)
    fun deleteTokens(tokenIds: Set<String>)
    fun findTokenWithId(tokenId: String): TokenEntry
    fun findTokenWithName(issuer: String, label: String): TokenEntry?
    fun insertTokens(tokens: List<TokenEntry>)
    fun updateToken(token: TokenEntry)
    fun updateTokens(tokens: List<TokenEntry>)
    fun replaceTokenWith(id: String, token: TokenEntry)
    fun updateHotpCounter(tokenId: String, counter: Long, updatedOn: Long)
    fun getLabels(): List<LabelSummary>
    fun renameLabel(oldName: String, newName: String, updatedOn: Long)
    fun deleteLabel(name: String, updatedOn: Long)
}
