package com.boxy.authenticator.test.ui

import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.core.TokenFormValidator
import com.boxy.authenticator.data.preferences.PreferenceStore
import com.boxy.authenticator.domain.repository.TokenRepository
import com.boxy.authenticator.domain.models.Thumbnail
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.models.enums.AccountEntryMethod
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.usecases.FetchTokensUseCase
import com.boxy.authenticator.domain.usecases.DeleteTokenUseCase
import com.boxy.authenticator.domain.usecases.FetchTokenByIdUseCase
import com.boxy.authenticator.domain.usecases.FetchLabelsUseCase
import com.boxy.authenticator.domain.usecases.InsertTokenUseCase
import com.boxy.authenticator.domain.usecases.ReplaceExistingTokenUseCase
import com.boxy.authenticator.domain.usecases.UpdateTokenUseCase
import com.boxy.authenticator.domain.usecases.UpdateHotpCounterUseCase
import com.boxy.authenticator.domain.usecases.UpdateTokensUseCase
import com.boxy.authenticator.domain.usecases.DeleteTokensUseCase
import com.boxy.authenticator.test.RecordingTokenRepository
import com.boxy.authenticator.ui.state.DataLoadState
import com.boxy.authenticator.ui.state.HomeUiState
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun `label filters use case-insensitive AND matching`() {
        val workAdmin = hotpToken(1).copy(id = "one", labels = setOf("Work", "Admin"))
        val workOnly = hotpToken(2).copy(id = "two", labels = setOf("work"))
        val state = HomeUiState(
            tokensState = DataLoadState.Data(listOf(workAdmin, workOnly)),
            selectedLabels = setOf("WORK", "admin"),
        )

        assertEquals(listOf("Admin", "Work"), state.availableLabels)
        assertEquals(listOf(workAdmin), state.visibleTokens)
        assertEquals(2, state.labelCount("WORK"))
    }

    @Test
    fun `archived accounts appear only in the virtual archived label`() {
        val active = hotpToken(1).copy(id = "active", labels = setOf("Work"))
        val archived = hotpToken(2).copy(
            id = "archived",
            labels = setOf("Work", "Private"),
            isArchived = true,
        )
        val normalState = HomeUiState(tokensState = DataLoadState.Data(listOf(active, archived)))

        assertEquals(listOf("Work"), normalState.availableLabels)
        assertEquals(listOf(active), normalState.visibleTokens)
        assertEquals(1, normalState.labelCount("Work"))
        assertTrue(normalState.hasArchivedTokens)

        val archivedState = normalState.copy(isArchivedSelected = true)
        assertEquals(listOf(archived), archivedState.visibleTokens)
    }

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
    fun `default label is applied while archived accounts stay hidden`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val settings = SettingsDataStore(FakePreferenceStore()).apply {
                setDefaultLabelFilter("work")
            }
            val active = hotpToken(1).copy(id = "active", labels = setOf("Work"))
            val archived = hotpToken(2).copy(
                id = "archived",
                labels = setOf("Archive"),
                isArchived = true,
            )
            val repository = FakeTokenRepository(tokens = listOf(active, archived))
            val viewModel = homeViewModel(repository, settings)

            viewModel.loadTokens()

            val state = viewModel.uiState.value
            assertEquals(
                listOf(active, archived),
                assertIs<DataLoadState.Data<List<TokenEntry>>>(state.tokensState).value,
            )
            assertEquals(listOf(active), state.visibleTokens)
            assertEquals(setOf("Work"), state.selectedLabels)
            assertEquals("Work", state.defaultLabel)

            viewModel.requestDefaultLabel("WORK")
            viewModel.confirmDefaultLabel()
            assertNull(settings.getDefaultLabelFilter())
            assertTrue(viewModel.uiState.value.selectedLabels.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `archived can be saved and restored as the default virtual label`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val settings = SettingsDataStore(FakePreferenceStore())
            val archived = hotpToken(1).copy(isArchived = true)
            val repository = FakeTokenRepository(tokens = listOf(archived))
            val first = HomeViewModel(
                settings,
                FetchTokensUseCase(repository),
                UpdateHotpCounterUseCase(repository),
                UpdateTokensUseCase(repository),
                DeleteTokensUseCase(repository),
            )
            first.loadTokens()
            first.requestDefaultArchived()
            first.confirmDefaultLabel()

            val reopened = HomeViewModel(
                settings,
                FetchTokensUseCase(repository),
                UpdateHotpCounterUseCase(repository),
                UpdateTokensUseCase(repository),
                DeleteTokensUseCase(repository),
            )
            reopened.loadTokens()

            assertTrue(reopened.uiState.value.isArchivedSelected)
            assertTrue(reopened.uiState.value.isArchivedDefault)
            assertEquals(listOf(archived), reopened.uiState.value.visibleTokens)
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
    fun `HOTP counter changes only after it is persisted`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val token = hotpToken(counter = 4)
            val repository = FakeTokenRepository(tokens = listOf(token))
            val viewModel = homeViewModel(repository)
            viewModel.loadTokens()

            var succeeded = false
            viewModel.updateHotpCounter(token.id, 5) { succeeded = it }

            assertTrue(succeeded)
            assertEquals(5L, repository.persistedHotpCounter)
            val tokens = assertIs<DataLoadState.Data<List<TokenEntry>>>(
                viewModel.uiState.value.tokensState,
            ).value
            assertEquals(5L, (tokens.single().otpInfo as HotpInfo).counter)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed HOTP persistence leaves the visible counter unchanged`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val token = hotpToken(counter = 4)
            val repository = FakeTokenRepository(
                tokens = listOf(token),
                hotpError = IllegalStateException("write failed"),
            )
            val viewModel = homeViewModel(repository)
            viewModel.loadTokens()

            var succeeded = true
            viewModel.updateHotpCounter(token.id, 5) { succeeded = it }

            assertFalse(succeeded)
            val tokens = assertIs<DataLoadState.Data<List<TokenEntry>>>(
                viewModel.uiState.value.tokensState,
            ).value
            assertEquals(4L, (tokens.single().otpInfo as HotpInfo).counter)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `selection applies labels additively and archives or deletes in batches`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val first = hotpToken(1).copy(id = "one", labels = setOf("Personal"))
            val second = hotpToken(2).copy(id = "two")
            val repository = RecordingTokenRepository(listOf(first, second))
            val viewModel = homeViewModel(repository)
            viewModel.loadTokens()

            viewModel.selectToken(first.id)
            viewModel.toggleTokenSelection(second.id)
            assertEquals(setOf("one", "two"), viewModel.uiState.value.selectedTokenIds)

            viewModel.showApplyLabelsDialog(true)
            viewModel.toggleLabelToApply("Work")
            viewModel.applyLabelsToSelection()
            assertTrue(repository.tokens.all { "Work" in it.labels })
            assertTrue("Personal" in repository.tokens.first { it.id == "one" }.labels)
            assertFalse(viewModel.uiState.value.isSelectionMode)

            viewModel.selectToken(first.id)
            viewModel.toggleTokenSelection(second.id)
            viewModel.showArchiveSelectionDialog(true)
            viewModel.archiveOrRestoreSelection()
            assertTrue(repository.tokens.all(TokenEntry::isArchived))

            viewModel.toggleArchivedFilter()
            viewModel.selectToken(first.id)
            viewModel.showDeleteSelectionDialog(true)
            assertTrue(viewModel.uiState.value.showDeleteSelectionDialog)
            viewModel.deleteSelection()
            assertEquals(listOf("two"), repository.getAllTokens().map { it.id })
            assertEquals(listOf("one"), repository.getRecycledTokens().map { it.id })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed batch update keeps selection and visible tokens unchanged`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val first = hotpToken(1).copy(id = "one", labels = setOf("Personal"))
            val second = hotpToken(2).copy(id = "two")
            val repository = RecordingTokenRepository(listOf(first, second))
            val viewModel = homeViewModel(repository)
            viewModel.loadTokens()
            viewModel.selectToken(first.id)
            viewModel.toggleTokenSelection(second.id)
            viewModel.showApplyLabelsDialog(true)
            viewModel.toggleLabelToApply("Work")
            repository.error = IllegalStateException("write failed")

            viewModel.applyLabelsToSelection()

            assertEquals(setOf("one", "two"), viewModel.uiState.value.selectedTokenIds)
            assertEquals("Unable to update selected accounts.", viewModel.uiState.value.selectionError)
            assertTrue(viewModel.uiState.value.showApplyLabelsDialog)
            assertEquals(listOf(first, second), repository.tokens)
            val visibleTokens = assertIs<DataLoadState.Data<List<TokenEntry>>>(
                viewModel.uiState.value.tokensState,
            ).value
            assertEquals(listOf(first, second), visibleTokens)
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
                fetchLabelsUseCase = FetchLabelsUseCase(repository),
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

    private fun homeViewModel(
        repository: TokenRepository,
        settings: SettingsDataStore = SettingsDataStore(FakePreferenceStore()),
    ) = HomeViewModel(
        settingsDataStore = settings,
        fetchTokensUseCase = FetchTokensUseCase(repository),
        updateHotpCounterUseCase = UpdateHotpCounterUseCase(repository),
        updateTokensUseCase = UpdateTokensUseCase(repository),
        deleteTokensUseCase = DeleteTokensUseCase(repository),
    )

    private fun hotpToken(counter: Long) = TokenEntry(
        id = "hotp-id",
        issuer = "Example",
        label = "account",
        thumbnail = Thumbnail.Color("#000000"),
        otpInfo = HotpInfo(secretKey = byteArrayOf(1), counter = counter),
        createdOn = 1L,
        updatedOn = 1L,
        addedFrom = AccountEntryMethod.FORM,
    )
}

private class FakeTokenRepository(
    private var tokens: List<TokenEntry> = emptyList(),
    private val loadError: Throwable? = null,
    private val findError: Throwable? = null,
    private val hotpError: Throwable? = null,
) : TokenRepository {
    var persistedHotpCounter: Long? = null
        private set

    override suspend fun getAllTokens(): List<TokenEntry> = loadError?.let { throw it } ?: tokens
    override suspend fun getRecycledTokens(): List<TokenEntry> = emptyList()
    override suspend fun getTokensCount() = tokens.size.toLong()
    override suspend fun findTokenWithId(tokenId: String): TokenEntry =
        findError?.let { throw it } ?: error("Not used")
    override suspend fun findTokenWithName(issuer: String, label: String): TokenEntry? = null
    override suspend fun insertTokens(tokens: List<TokenEntry>) = Unit
    override suspend fun insertToken(token: TokenEntry) = Unit
    override suspend fun deleteToken(tokenId: String) = Unit
    override suspend fun deleteTokens(tokenIds: Set<String>) = Unit
    override suspend fun restoreTokens(tokenIds: Set<String>) = Unit
    override suspend fun permanentlyDeleteTokens(tokenIds: Set<String>) = Unit
    override suspend fun updateToken(token: TokenEntry) = Unit
    override suspend fun updateTokens(tokens: List<TokenEntry>) = Unit
    override suspend fun replaceTokenWith(id: String, token: TokenEntry) = Unit
    override suspend fun updateHotpCounter(tokenId: String, counter: Long) {
        hotpError?.let { throw it }
        persistedHotpCounter = counter
        tokens = tokens.map { token ->
            if (token.id != tokenId) token else token.copy(
                otpInfo = HotpInfo(token.otpInfo.secretKey, counter = counter),
                updatedOn = token.updatedOn + 1,
            )
        }
    }
    override suspend fun getLabels() = emptyList<com.boxy.authenticator.domain.models.LabelSummary>()
    override suspend fun renameLabel(oldName: String, newName: String) = Unit
    override suspend fun deleteLabel(name: String) = Unit
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
