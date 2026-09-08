package com.boxy.authenticator.test.ui

import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.domain.usecases.FetchTokensUseCase
import com.boxy.authenticator.test.InMemoryPreferenceStore
import com.boxy.authenticator.test.RecordingTokenRepository
import com.boxy.authenticator.test.testToken
import com.boxy.authenticator.ui.state.DataLoadState
import com.boxy.authenticator.ui.viewmodels.ExportTokensViewModel
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
class ExportTokensViewModelTest {
    @Test
    fun `load exposes repository tokens`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val token = testToken()
            val viewModel = viewModel(RecordingTokenRepository(listOf(token)))

            viewModel.loadAllTokens()
            val state = viewModel.uiState.first { it.tokensState !is DataLoadState.Loading }

            assertEquals(listOf(token), assertIs<DataLoadState.Data<*>>(state.tokensState).value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `load exposes repository failure for retry UI`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = RecordingTokenRepository().apply {
                error = IllegalStateException("database unavailable")
            }
            val viewModel = viewModel(repository)

            viewModel.loadAllTokens()
            val state = viewModel.uiState.first { it.tokensState !is DataLoadState.Loading }

            assertEquals(
                "database unavailable",
                assertIs<DataLoadState.Error>(state.tokensState).message,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `export dialogs are controlled independently`() {
        val viewModel = viewModel(RecordingTokenRepository())

        viewModel.showPlainTextWarningDialog(true)
        assertTrue(viewModel.uiState.value.showPlainTextWarningDialog)
        assertFalse(viewModel.uiState.value.showSetPasswordDialog)

        viewModel.showSetPasswordDialog(true)
        viewModel.showPlainTextWarningDialog(false)
        assertFalse(viewModel.uiState.value.showPlainTextWarningDialog)
        assertTrue(viewModel.uiState.value.showSetPasswordDialog)
    }

    private fun viewModel(repository: RecordingTokenRepository) = ExportTokensViewModel(
        settingsDataStore = SettingsDataStore(InMemoryPreferenceStore()),
        fetchTokensUseCase = FetchTokensUseCase(repository),
    )
}
