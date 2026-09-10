package com.boxy.authenticator.test

import com.boxy.authenticator.data.preferences.PreferenceStore
import com.boxy.authenticator.domain.models.Thumbnail
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.models.LabelSummary
import com.boxy.authenticator.domain.models.enums.AccountEntryMethod
import com.boxy.authenticator.domain.models.otp.OtpInfo
import com.boxy.authenticator.domain.models.otp.TotpInfo
import com.boxy.authenticator.domain.repository.TokenRepository

internal class InMemoryPreferenceStore : PreferenceStore {
    val values = mutableMapOf<String, Any>()
    var readError: Throwable? = null

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        readError?.let { throw it }
        return values[key] as? Boolean ?: defaultValue
    }

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun getString(key: String, defaultValue: String?): String? {
        readError?.let { throw it }
        return values[key] as? String ?: defaultValue
    }

    override fun putInt(key: String, value: Int) {
        values[key] = value
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        readError?.let { throw it }
        return values[key] as? Int ?: defaultValue
    }

    override fun putLong(key: String, value: Long) {
        values[key] = value
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        readError?.let { throw it }
        return values[key] as? Long ?: defaultValue
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun clear() {
        values.clear()
    }
}

internal class RecordingTokenRepository(
    initialTokens: List<TokenEntry> = emptyList(),
) : TokenRepository {
    val tokens = initialTokens.toMutableList()
    var error: Throwable? = null
    var lastDeletedId: String? = null
    var lastReplacement: Pair<String, TokenEntry>? = null
    var lastHotpUpdate: Pair<String, Long>? = null

    private fun failIfConfigured() {
        error?.let { throw it }
    }

    override suspend fun getAllTokens(): List<TokenEntry> {
        failIfConfigured()
        return tokens.toList()
    }

    override suspend fun getTokensCount(): Long {
        failIfConfigured()
        return tokens.size.toLong()
    }

    override suspend fun findTokenWithId(tokenId: String): TokenEntry {
        failIfConfigured()
        return tokens.first { it.id == tokenId }
    }

    override suspend fun findTokenWithName(issuer: String, label: String): TokenEntry? {
        failIfConfigured()
        return tokens.firstOrNull {
            it.issuer.equals(issuer, ignoreCase = true) &&
                    it.label.equals(label, ignoreCase = true)
        }
    }

    override suspend fun insertTokens(tokens: List<TokenEntry>) {
        failIfConfigured()
        this.tokens += tokens
    }

    override suspend fun insertToken(token: TokenEntry) {
        failIfConfigured()
        tokens += token
    }

    override suspend fun deleteToken(tokenId: String) {
        failIfConfigured()
        lastDeletedId = tokenId
        tokens.removeAll { it.id == tokenId }
    }

    override suspend fun deleteTokens(tokenIds: Set<String>) {
        failIfConfigured()
        tokens.removeAll { it.id in tokenIds }
    }

    override suspend fun updateToken(token: TokenEntry) {
        failIfConfigured()
        val index = tokens.indexOfFirst { it.id == token.id }
        if (index >= 0) tokens[index] = token
    }

    override suspend fun updateTokens(tokens: List<TokenEntry>) {
        failIfConfigured()
        val updatesById = tokens.associateBy { it.id }
        this.tokens.indices.forEach { index ->
            updatesById[this.tokens[index].id]?.let { this.tokens[index] = it }
        }
    }

    override suspend fun replaceTokenWith(id: String, token: TokenEntry) {
        failIfConfigured()
        lastReplacement = id to token
        tokens.removeAll { it.id == id }
        tokens += token
    }

    override suspend fun updateHotpCounter(tokenId: String, counter: Long) {
        failIfConfigured()
        lastHotpUpdate = tokenId to counter
    }

    override suspend fun getLabels(): List<LabelSummary> {
        failIfConfigured()
        return tokens.flatMap { it.labels }
            .distinctBy { it.lowercase() }
            .map { name ->
                LabelSummary(name, tokens.count { token ->
                    token.labels.any { it.equals(name, ignoreCase = true) }
                }.toLong())
            }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun renameLabel(oldName: String, newName: String) {
        failIfConfigured()
        tokens.indices.forEach { index ->
            val token = tokens[index]
            if (token.labels.any { it.equals(oldName, ignoreCase = true) }) {
                tokens[index] = token.copy(
                    labels = (token.labels.filterNot { it.equals(oldName, ignoreCase = true) } + newName)
                        .distinctBy { it.lowercase() }
                        .toSet(),
                )
            }
        }
    }

    override suspend fun deleteLabel(name: String) {
        failIfConfigured()
        tokens.indices.forEach { index ->
            val token = tokens[index]
            tokens[index] = token.copy(
                labels = token.labels.filterNot { it.equals(name, ignoreCase = true) }.toSet(),
            )
        }
    }
}

internal fun testToken(
    id: String = "token-id",
    issuer: String = "Example",
    label: String = "person@example.com",
    otpInfo: OtpInfo = TotpInfo("hello world".encodeToByteArray()),
    labels: Set<String> = emptySet(),
) = TokenEntry(
    id = id,
    issuer = issuer,
    label = label,
    thumbnail = Thumbnail.Color("#336699"),
    otpInfo = otpInfo,
    createdOn = 1_000L,
    updatedOn = 2_000L,
    addedFrom = AccountEntryMethod.FORM,
    labels = labels,
)
