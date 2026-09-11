package com.boxy.authenticator.test.ui

import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.usecases.FetchRecycledTokensUseCase
import com.boxy.authenticator.domain.usecases.PermanentlyDeleteTokensUseCase
import com.boxy.authenticator.domain.usecases.RestoreTokensUseCase
import com.boxy.authenticator.test.RecordingTokenRepository
import com.boxy.authenticator.test.testToken
import com.boxy.authenticator.ui.state.DataLoadState
import com.boxy.authenticator.ui.viewmodels.RecycleBinViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RecycleBinViewModelTest {
    @Test
    fun `load shows only recycled accounts and restore returns one to active accounts`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val active = testToken(id = "active", issuer = "Different")
            val recycled = testToken(id = "recycled").copy(deletedOn = 3_000L)
            val repository = RecordingTokenRepository(listOf(active, recycled))
            val viewModel = viewModel(repository)

            viewModel.loadTokens()

            val loaded = assertIs<DataLoadState.Data<List<TokenEntry>>>(
                viewModel.uiState.value.tokensState,
            ).value
            assertEquals(listOf("recycled"), loaded.map { it.id })

            viewModel.restoreToken(recycled.id)

            assertEquals(emptyList(), repository.getRecycledTokens())
            assertEquals(setOf("active", "recycled"), repository.getAllTokens().mapTo(mutableSetOf()) { it.id })
            assertEquals(emptyList(), assertIs<DataLoadState.Data<List<TokenEntry>>>(
                viewModel.uiState.value.tokensState,
            ).value)
            assertNull(viewModel.uiState.value.processingTokenId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `permanent delete requires confirmation target and removes recycled account`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val recycled = testToken(id = "recycled").copy(deletedOn = 3_000L)
            val repository = RecordingTokenRepository(listOf(recycled))
            val viewModel = viewModel(repository)
            viewModel.loadTokens()

            viewModel.permanentlyDeleteToken()
            assertEquals(listOf(recycled), repository.tokens)

            viewModel.showPermanentDeleteDialog(recycled)
            viewModel.permanentlyDeleteToken()

            assertEquals(emptyList(), repository.tokens)
            assertNull(viewModel.uiState.value.permanentlyDeletingToken)
            assertEquals(emptyList(), assertIs<DataLoadState.Data<List<TokenEntry>>>(
                viewModel.uiState.value.tokensState,
            ).value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed permanent delete keeps account and confirmation available for retry`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val recycled = testToken(id = "recycled").copy(deletedOn = 3_000L)
            val repository = RecordingTokenRepository(listOf(recycled))
            val viewModel = viewModel(repository)
            viewModel.loadTokens()
            viewModel.showPermanentDeleteDialog(recycled)
            repository.error = IllegalStateException("write failed")

            viewModel.permanentlyDeleteToken()

            assertEquals(recycled, viewModel.uiState.value.permanentlyDeletingToken)
            assertEquals("Unable to permanently delete the account.", viewModel.uiState.value.operationError)
            assertEquals(listOf(recycled), repository.tokens)
            assertNull(viewModel.uiState.value.processingTokenId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed restore keeps recycled account visible`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val recycled = testToken(id = "recycled").copy(deletedOn = 3_000L)
            val repository = RecordingTokenRepository(listOf(recycled))
            val viewModel = viewModel(repository)
            viewModel.loadTokens()
            repository.error = IllegalStateException("write failed")

            viewModel.restoreToken(recycled.id)

            assertEquals("Unable to restore the account.", viewModel.uiState.value.operationError)
            assertEquals(listOf(recycled), assertIs<DataLoadState.Data<List<TokenEntry>>>(
                viewModel.uiState.value.tokensState,
            ).value)
            assertNull(viewModel.uiState.value.processingTokenId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `load failure exposes retryable error state`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = RecordingTokenRepository()
            repository.error = IllegalStateException("read failed")
            val viewModel = viewModel(repository)

            viewModel.loadTokens()

            val state = assertIs<DataLoadState.Error>(viewModel.uiState.value.tokensState)
            assertEquals("Unable to load Recycle Bin.", state.message)
            assertFalse(viewModel.uiState.value.tokensState == DataLoadState.Loading)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `restore refuses a recycled duplicate of an active account`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val active = testToken(id = "active")
            val recycled = testToken(
                id = "recycled",
                issuer = active.issuer.lowercase(),
                label = active.label.uppercase(),
            ).copy(deletedOn = 3_000L)
            val repository = RecordingTokenRepository(listOf(active, recycled))
            val viewModel = viewModel(repository)
            viewModel.loadTokens()

            viewModel.restoreToken(recycled.id)

            assertEquals("Unable to restore the account.", viewModel.uiState.value.operationError)
            assertEquals(listOf(recycled), repository.getRecycledTokens())
            assertEquals(listOf(active), repository.getAllTokens())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(repository: RecordingTokenRepository) = RecycleBinViewModel(
        fetchRecycledTokensUseCase = FetchRecycledTokensUseCase(repository),
        restoreTokensUseCase = RestoreTokensUseCase(repository),
        permanentlyDeleteTokensUseCase = PermanentlyDeleteTokensUseCase(repository),
    )
}
