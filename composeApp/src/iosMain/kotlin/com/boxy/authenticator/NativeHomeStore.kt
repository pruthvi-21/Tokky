package com.boxy.authenticator

import com.boxy.authenticator.domain.models.Thumbnail
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.models.otp.TotpInfo
import com.boxy.authenticator.domain.usecases.FetchTokensUseCase
import com.boxy.authenticator.domain.usecases.FetchRecycledTokensUseCase
import com.boxy.authenticator.domain.usecases.DeleteTokensUseCase
import com.boxy.authenticator.domain.usecases.RestoreTokensUseCase
import com.boxy.authenticator.domain.usecases.PermanentlyDeleteTokensUseCase
import com.boxy.authenticator.domain.usecases.UpdateTokenUseCase
import com.boxy.authenticator.domain.usecases.UpdateHotpCounterUseCase
import com.boxy.authenticator.utils.getInitials
import com.boxy.authenticator.utils.name
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NativeHomeStore : KoinComponent {
    private val fetchTokensUseCase: FetchTokensUseCase by inject()
    private val updateHotpCounterUseCase: UpdateHotpCounterUseCase by inject()
    private val fetchRecycledTokens: FetchRecycledTokensUseCase by inject()
    private val deleteTokens: DeleteTokensUseCase by inject()
    private val restoreTokens: RestoreTokensUseCase by inject()
    private val permanentlyDeleteTokens: PermanentlyDeleteTokensUseCase by inject()
    private val updateToken: UpdateTokenUseCase by inject()
    private val scope = MainScope()
    private var cachedTokens: List<TokenEntry> = emptyList()

    init {
        NativeAppBootstrap.start()
    }

    fun load(onSuccess: (NativeHomeSnapshot) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            fetchTokensUseCase().fold(
                onSuccess = { tokens ->
                    cachedTokens = tokens
                    onSuccess(tokens.toNativeSnapshot())
                },
                onFailure = { exception ->
                    onError(exception.message ?: "Unable to load accounts.")
                },
            )
        }
    }

    fun refreshCodes(onSuccess: (NativeHomeSnapshot) -> Unit, onError: (String) -> Unit) {
        if (cachedTokens.isEmpty()) {
            load(onSuccess, onError)
            return
        }

        onSuccess(cachedTokens.toNativeSnapshot())
    }

    fun incrementHotp(tokenId: String, counter: Long, onComplete: (Boolean) -> Unit) {
        if (counter == Long.MAX_VALUE) {
            onComplete(false)
            return
        }
        scope.launch {
            updateHotpCounterUseCase(tokenId, counter + 1).fold(
                onSuccess = {
                    load(
                        onSuccess = { onComplete(true) },
                        onError = { onComplete(false) },
                    )
                },
                onFailure = { onComplete(false) },
            )
        }
    }

    fun loadRecycleBin(onSuccess: (List<NativeTokenSummary>) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            fetchRecycledTokens().fold(
                onSuccess = { onSuccess(it.map { token -> token.toNativeSummary() }) },
                onFailure = { onError(it.message ?: "Unable to load recycle bin.") },
            )
        }
    }

    fun performAction(tokenId: String, action: String, onComplete: (String?) -> Unit) {
        scope.launch {
            val result = when (action) {
                "recycle" -> deleteTokens(setOf(tokenId))
                "restore" -> restoreTokens(setOf(tokenId))
                "delete" -> permanentlyDeleteTokens(setOf(tokenId))
                "archive", "unarchive" -> {
                    val token = cachedTokens.find { it.id == tokenId }
                    if (token == null) {
                        onComplete("Account is no longer available. Refresh and try again.")
                        return@launch
                    }
                    updateToken(token.copy(isArchived = action == "archive"))
                }
                else -> Result.failure(IllegalArgumentException("Unknown account action."))
            }
            onComplete(result.exceptionOrNull()?.message)
        }
    }

    fun dispose() {
        scope.cancel()
    }

    fun performRecycledAction(tokenIds: List<String>, restore: Boolean, onComplete: (String?) -> Unit) {
        scope.launch {
            val ids = tokenIds.toSet()
            if (ids.isEmpty()) {
                onComplete("Select at least one account.")
                return@launch
            }
            val result = if (restore) restoreTokens(ids) else permanentlyDeleteTokens(ids)
            result.fold(
                onSuccess = { onComplete(null) },
                onFailure = { onComplete(it.message ?: "Unable to update selected accounts.") },
            )
        }
    }

    private fun List<TokenEntry>.toNativeSnapshot(): NativeHomeSnapshot {
        val activeTokens = filterNot(TokenEntry::isArchived)
            .sortedBy { it.name.lowercase() }
            .map { it.toNativeSummary() }
        val archivedCount = count(TokenEntry::isArchived)
        val labels = filterNot(TokenEntry::isArchived)
            .flatMap { it.labels }
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        return NativeHomeSnapshot(
            activeTokens = activeTokens,
            archivedTokens = filter(TokenEntry::isArchived).sortedBy { it.name.lowercase() }
                .map { it.toNativeSummary() },
            archivedCount = archivedCount,
            labels = labels,
        )
    }

    private fun TokenEntry.toNativeSummary(): NativeTokenSummary {
        val timeInfo = otpInfo as? TotpInfo
        val hotpInfo = otpInfo as? HotpInfo
        val remainingSeconds = timeInfo?.getMillisTillNextRotation()
            ?.let { ((it + 999) / 1000).toInt() }
        val periodSeconds = timeInfo?.period?.toInt()
        val progress = if (remainingSeconds != null && periodSeconds != null && periodSeconds > 0) {
            remainingSeconds.toDouble() / periodSeconds.toDouble()
        } else {
            1.0
        }

        return NativeTokenSummary(
            id = id,
            issuer = issuer,
            label = label,
            displayName = name,
            initials = issuer.getInitials(),
            otp = runCatching { otpInfo.getOtp() }.getOrDefault(""),
            timeBased = timeInfo != null,
            remainingSeconds = remainingSeconds ?: 0,
            periodSeconds = periodSeconds ?: 0,
            progress = progress.coerceIn(0.0, 1.0),
            hotpCounter = hotpInfo?.counter ?: 0L,
            tintColor = (thumbnail as? Thumbnail.Color)?.color ?: "#3478F6",
            labels = labels.sortedWith(String.CASE_INSENSITIVE_ORDER),
        )
    }
}

data class NativeHomeSnapshot(
    val activeTokens: List<NativeTokenSummary>,
    val archivedTokens: List<NativeTokenSummary>,
    val archivedCount: Int,
    val labels: List<String>,
)

data class NativeTokenSummary(
    val id: String,
    val issuer: String,
    val label: String,
    val displayName: String,
    val initials: String,
    val otp: String,
    val timeBased: Boolean,
    val remainingSeconds: Int,
    val periodSeconds: Int,
    val progress: Double,
    val hotpCounter: Long,
    val tintColor: String,
    val labels: List<String>,
)
