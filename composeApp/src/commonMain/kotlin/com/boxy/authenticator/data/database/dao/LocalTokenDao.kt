package com.boxy.authenticator.data.database.dao

import com.boxy.authenticator.core.ImportedTokenValidator
import com.boxy.authenticator.core.TokenLabels
import com.boxy.authenticator.core.accountNameKey
import com.boxy.authenticator.db.GetRecycledTokens
import com.boxy.authenticator.db.TokenDatabase
import com.boxy.authenticator.db.TokenEntityQueries
import com.boxy.authenticator.db.Token_entry
import com.boxy.authenticator.domain.models.LabelSummary
import com.boxy.authenticator.domain.models.Thumbnail
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.models.enums.AccountEntryMethod
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.models.otp.OtpInfo
import com.boxy.authenticator.utils.StaleTokenException
import com.boxy.authenticator.utils.TokenNameExistsException
import kotlin.time.Clock

class LocalTokenDao(database: TokenDatabase) : TokenDao {
    private val queries: TokenEntityQueries = database.tokenEntityQueries

    override fun getAllTokens(): List<TokenEntry> {
        return queries.transactionWithResult {
            val labelsByToken = queries.getAllTokenLabels().executeAsList()
                .groupBy({ it.tokenId }, { it.name })
            queries.getAllTokens().executeAsList().map {
                it.toTokenEntry(labelsByToken[it.id].orEmpty().toSet())
            }
        }
    }

    override fun getRecycledTokens(): List<TokenEntry> {
        return queries.transactionWithResult {
            val labelsByToken = queries.getRecycledTokenLabels().executeAsList()
                .groupBy({ it.tokenId }, { it.name })
            queries.getRecycledTokens().executeAsList().map { token ->
                token.toTokenEntry(labelsByToken[token.id].orEmpty().toSet())
            }
        }
    }

    override fun getTokensCount(): Long {
        return queries.getTokensCount().executeAsOne()
    }

    override fun insertToken(token: TokenEntry) {
        queries.transaction {
            validateInsert(token)
            queries.insertTokenEntry(ImportedTokenValidator.validate(token))
            queries.replaceLabels(token)
        }
    }

    override fun moveTokensToRecycleBin(tokenIds: Set<String>, deletedOn: Long) {
        queries.transaction {
            tokenIds.forEach { queries.moveTokenToRecycleBin(deletedOn, it) }
        }
    }

    override fun restoreTokens(tokenIds: Set<String>, updatedOn: Long) {
        queries.transaction {
            tokenIds.forEach { tokenId ->
                queries.restoreToken(updatedOn, tokenId)
                if (queries.findTokenWithId(tokenId).executeAsOneOrNull() == null) {
                    throw IllegalStateException("Account could not be restored.")
                }
            }
        }
    }

    override fun permanentlyDeleteTokens(tokenIds: Set<String>) {
        queries.transaction {
            tokenIds.forEach { tokenId ->
                queries.deleteRecycledTokenLabels(tokenId)
                queries.permanentlyDeleteToken(tokenId)
            }
            queries.deleteUnusedLabels()
        }
    }

    override fun findTokenWithId(tokenId: String): TokenEntry {
        return queries.transactionWithResult {
            queries.findTokenWithId(tokenId).executeAsOne().toTokenEntry(
                queries.getLabelsForToken(tokenId).executeAsList().toSet()
            )
        }
    }

    override fun findTokenWithName(issuer: String, label: String): TokenEntry? {
        return queries.transactionWithResult {
            queries.findTokenWithName(issuer, label).executeAsOneOrNull()?.let {
                it.toTokenEntry(queries.getLabelsForToken(it.id).executeAsList().toSet())
            }
        }
    }

    override fun insertTokens(tokens: List<TokenEntry>) {
        queries.transaction {
            tokens.forEach { token ->
                validateInsert(token)
                queries.insertTokenEntry(ImportedTokenValidator.validate(token))
                queries.replaceLabels(token)
            }
        }
    }

    override fun updateToken(token: TokenEntry) = updateTokens(listOf(token))

    override fun updateTokens(tokens: List<TokenEntry>) {
        queries.transaction {
            tokens.forEach { token ->
                val current = queries.findTokenWithId(token.id).executeAsOneOrNull()
                    ?: throw StaleTokenException()
                if (current.updatedOn != token.updatedOn) throw StaleTokenException()
                val validated = ImportedTokenValidator.validate(token)
                if (accountNameKey(current.issuer, current.label) != accountNameKey(token.issuer, token.label)) {
                    checkNameAvailable(token)
                }
                val updated = validated.copy(updatedOn = nextUpdatedOn(current.updatedOn))
                queries.updateTokenEntry(updated)
                queries.replaceLabels(updated)
            }
            queries.deleteUnusedLabels()
        }
    }

    private fun nextUpdatedOn(previous: Long): Long {
        check(previous < Long.MAX_VALUE) { "Account revision is exhausted." }
        return maxOf(Clock.System.now().toEpochMilliseconds(), previous + 1)
    }

    private fun checkNameAvailable(token: TokenEntry) {
        val existing = queries.findTokenWithName(token.issuer, token.label).executeAsOneOrNull()
        if (existing != null && existing.id != token.id) {
            throw TokenNameExistsException(
                existing.toTokenEntry(emptySet()),
                "An account with this name already exists."
            )
        }
    }

    private fun validateInsert(token: TokenEntry) {
        require(token.deletedOn == null) { "New accounts must be active." }
        checkNameAvailable(token)
    }

    override fun replaceTokenWith(id: String, token: TokenEntry) {
        queries.transaction {
            queries.findTokenWithId(id).executeAsOneOrNull() ?: throw StaleTokenException()
            ImportedTokenValidator.validate(token)
            queries.deleteTokenLabels(id)
            queries.deleteTokenForReplacement(id)
            validateInsert(token)
            queries.insertTokenEntry(ImportedTokenValidator.validate(token))
            queries.replaceLabels(token)
            queries.deleteUnusedLabels()
        }
    }

    override fun updateHotpCounter(tokenId: String, counter: Long, updatedOn: Long) {
        queries.transaction {
            val current = queries.findTokenWithId(tokenId).executeAsOneOrNull()
                ?: throw StaleTokenException()
            val currentOtpInfoJson = current.otpInfo

            val otpInfoMap = OtpInfo.deserialize(currentOtpInfoJson)
            if (otpInfoMap !is HotpInfo) throw IllegalStateException("Not HOTP")
            if (otpInfoMap.counter == Long.MAX_VALUE || counter < 0 || counter != otpInfoMap.counter + 1) {
                throw StaleTokenException()
            }

            val updatedOtpInfoJson = HotpInfo(
                secretKey = otpInfoMap.secretKey,
                algorithm = otpInfoMap.algorithm,
                digits = otpInfoMap.digits,
                counter = counter,
            ).serialize()

            queries.updateHotpInfo(updatedOtpInfoJson, maxOf(updatedOn, nextUpdatedOn(current.updatedOn)), tokenId)
        }
    }

    override fun getLabels(): List<LabelSummary> = queries.getLabelsWithCounts { name, tokenCount ->
        LabelSummary(name, tokenCount)
    }.executeAsList()

    override fun renameLabel(oldName: String, newName: String, updatedOn: Long) {
        val normalizedName = TokenLabels.normalize(listOf(newName)).single()
        queries.transaction {
            val sourceId = queries.findLabelId(oldName).executeAsOneOrNull()
                ?: throw NoSuchElementException("Label not found.")
            queries.touchTokensForLabel(updatedOn = updatedOn, labelId = sourceId)
            val targetId = queries.findLabelId(normalizedName).executeAsOneOrNull()
            when {
                targetId == null || targetId == sourceId -> {
                    queries.updateLabelName(normalizedName, sourceId)
                }

                else -> {
                    queries.mergeLabelRelations(targetId, sourceId)
                    queries.deleteTokenLabelsForLabel(sourceId)
                    queries.deleteLabel(sourceId)
                }
            }
        }
    }

    override fun deleteLabel(name: String, updatedOn: Long) {
        queries.transaction {
            val labelId = queries.findLabelId(name).executeAsOneOrNull() ?: return@transaction
            queries.touchTokensForLabel(updatedOn = updatedOn, labelId = labelId)
            queries.deleteTokenLabelsForLabel(labelId)
            queries.deleteLabel(labelId)
        }
    }
}

private fun Token_entry.toTokenEntry(labels: Set<String>) = TokenEntry(
    id = id,
    issuer = issuer,
    label = label,
    thumbnail = Thumbnail.deserialize(thumbnail),
    otpInfo = OtpInfo.deserialize(otpInfo),
    createdOn = createdOn,
    updatedOn = updatedOn,
    addedFrom = AccountEntryMethod.valueOf(addedFrom),
    labels = labels,
    isArchived = isArchived != 0L,
    deletedOn = deletedOn,
)

private fun GetRecycledTokens.toTokenEntry(labels: Set<String>) = TokenEntry(
    id = id,
    issuer = issuer,
    label = label,
    thumbnail = Thumbnail.deserialize(thumbnail),
    otpInfo = OtpInfo.deserialize(otpInfo),
    createdOn = createdOn,
    updatedOn = updatedOn,
    addedFrom = AccountEntryMethod.valueOf(addedFrom),
    labels = labels,
    isArchived = isArchived != 0L,
    deletedOn = deletedOn,
)

private fun TokenEntityQueries.insertTokenEntry(token: TokenEntry) {
    insertToken(
        id = token.id,
        issuer = token.issuer,
        label = token.label,
        thumbnail = token.thumbnail.serialize(),
        otpInfo = token.otpInfo.serialize(),
        createdOn = token.createdOn,
        updatedOn = token.updatedOn,
        addedFrom = token.addedFrom.name,
        isArchived = if (token.isArchived) 1L else 0L,
        deletedOn = token.deletedOn,
    )
}

private fun TokenEntityQueries.updateTokenEntry(token: TokenEntry) {
    updateToken(
        issuer = token.issuer,
        label = token.label,
        thumbnail = token.thumbnail.serialize(),
        otpInfo = token.otpInfo.serialize(),
        updatedOn = token.updatedOn,
        isArchived = if (token.isArchived) 1L else 0L,
        id = token.id,
    )
}

private fun TokenEntityQueries.replaceLabels(token: TokenEntry) {
    deleteTokenLabels(token.id)
    TokenLabels.normalize(token.labels).forEach { label ->
        insertLabel(label, token.updatedOn)
        insertTokenLabel(token.id, findLabelId(label).executeAsOne())
    }
}
