package com.boxy.authenticator.test.ui

import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.core.TokenFormValidator
import com.boxy.authenticator.data.preferences.PreferenceStore
import com.boxy.authenticator.domain.database.repository.TokenRepository
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.usecases.FetchTokensUseCase
import com.boxy.authenticator.domain.usecases.DeleteTokenUseCase
import com.boxy.authenticator.domain.usecases.FetchTokenByIdUseCase
import com.boxy.authenticator.domain.usecases.InsertTokenUseCase
import com.boxy.authenticator.domain.usecases.ReplaceExistingTokenUseCase
import com.boxy.authenticator.domain.usecases.UpdateTokenUseCase
import com.boxy.authenticator.ui.state.DataLoadState
import com.boxy.authenticator.ui.viewmodels.HomeViewModel
import com.boxy.authenticator.ui.viewmodels.TokenSetupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun `empty vault finishes in data state without backup warning`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val viewModel = homeViewModel(repository = FakeTokenRepository())

            viewModel.loadTokens()
            val state = viewModel.uiState.first { !it.isRefreshing }

            assertIs<DataLoadState.Data<List<TokenEntry>>>(state.tokensState)
            assertTrue(state.tokensState.value.isEmpty())
            assertTrue(state.hasTakenAtleastOneBackup)
            assertFalse(state.isLastBackupOutdated)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `database failure finishes in retryable error state`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val viewModel = homeViewModel(
                repository = FakeTokenRepository(loadError = IllegalStateException("database unavailable")),
            )

            viewModel.loadTokens()
            val state = viewModel.uiState.first { !it.isRefreshing }

            val error = assertIs<DataLoadState.Error>(state.tokensState)
            assertEquals("database unavailable", error.message)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `edit lookup failure does not fall back to a new-account form`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakeTokenRepository(findError = NoSuchElementException("missing"))
            val settings = SettingsDataStore(FakePreferenceStore())
            val viewModel = TokenSetupViewModel(
                settings = settings,
                fetchTokenByIdUseCase = FetchTokenByIdUseCase(repository),
                insertTokenUseCase = InsertTokenUseCase(repository),
                updateTokenUseCase = UpdateTokenUseCase(repository),
                deleteTokenUseCase = DeleteTokenUseCase(repository),
                replaceExistingTokenUseCase = ReplaceExistingTokenUseCase(repository),
                formValidator = TokenFormValidator(),
            )

            viewModel.loadToken("missing-id")
            val state = viewModel.uiState.first { it.editLoadState != DataLoadState.Loading }

            assertIs<DataLoadState.Error>(state.editLoadState)
            assertFalse(state.isInEditMode)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun homeViewModel(repository: TokenRepository) = HomeViewModel(
        settingsDataStore = SettingsDataStore(FakePreferenceStore()),
        fetchTokensUseCase = FetchTokensUseCase(repository),
    )
}

private class FakeTokenRepository(
    private val tokens: List<TokenEntry> = emptyList(),
    private val loadError: Throwable? = null,
    private val findError: Throwable? = null,
) : TokenRepository {
    override fun getAllTokens(): List<TokenEntry> = loadError?.let { throw it } ?: tokens
    override fun getTokensCount() = tokens.size.toLong()
    override fun findTokenWithId(tokenId: String): TokenEntry = findError?.let { throw it } ?: error("Not used")
    override fun findTokenWithName(issuer: String, label: String): TokenEntry? = null
    override fun insertTokens(tokens: List<TokenEntry>) = Unit
    override fun insertToken(token: TokenEntry) = Unit
    override fun deleteToken(tokenId: String) = Unit
    override fun updateToken(token: TokenEntry) = Unit
    override fun replaceTokenWith(id: String, token: TokenEntry) = Unit
    override fun updateHotpCounter(tokenId: String, counter: Long) = Unit
}

private class FakePreferenceStore : PreferenceStore {
    private val values = mutableMapOf<String, Any>()

    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
    override fun putString(key: String, value: String) { values[key] = value }
    override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
    override fun putLong(key: String, value: Long) { values[key] = value }
    override fun getLong(key: String, defaultValue: Long) = values[key] as? Long ?: defaultValue
    override fun remove(key: String) { values.remove(key) }
    override fun clear() = values.clear()
}
