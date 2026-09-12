package com.boxy.authenticator

import com.boxy.authenticator.domain.models.Thumbnail
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.models.otp.TotpInfo
import com.boxy.authenticator.domain.usecases.FetchTokensUseCase
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

    fun dispose() {
        scope.cancel()
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
