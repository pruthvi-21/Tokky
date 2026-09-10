package com.boxy.authenticator.test.ui

import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.core.TokenFormValidator
import com.boxy.authenticator.domain.models.enums.OTPType
import com.boxy.authenticator.domain.models.enums.TokenSetupMode
import com.boxy.authenticator.domain.models.form.TokenFormEvent
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.models.otp.SteamInfo
import com.boxy.authenticator.domain.models.otp.TotpInfo
import com.boxy.authenticator.domain.usecases.DeleteTokenUseCase
import com.boxy.authenticator.domain.usecases.FetchTokenByIdUseCase
import com.boxy.authenticator.domain.usecases.FetchLabelsUseCase
import com.boxy.authenticator.domain.usecases.InsertTokenUseCase
import com.boxy.authenticator.domain.usecases.ReplaceExistingTokenUseCase
import com.boxy.authenticator.domain.usecases.UpdateTokenUseCase
import com.boxy.authenticator.test.InMemoryPreferenceStore
import com.boxy.authenticator.test.RecordingTokenRepository
import com.boxy.authenticator.test.testToken
import com.boxy.authenticator.ui.state.DataLoadState
import com.boxy.authenticator.ui.viewmodels.TokenSetupViewModel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TokenSetupViewModelTest {
    @Test
    fun `TOTP token populates edit form and period visibility`() {
        val viewModel = viewModel()
        val token = testToken(
            otpInfo = TotpInfo(byteArrayOf(1, 2), "SHA256", 8, 45L),
            labels = setOf("Work", "Privileged"),
        )

        viewModel.setStateFromToken(token, TokenSetupMode.UPDATE)
        val state = viewModel.uiState.value

        assertEquals(token.issuer, state.issuer)
        assertEquals(token.label, state.label)
        assertEquals(OTPType.TOTP, state.type)
        assertEquals("SHA256", state.algorithm)
        assertEquals("8", state.digits)
        assertEquals("45", state.period)
        assertEquals(token.labels, state.labels)
        assertTrue(state.isPeriodFieldVisible)
        assertFalse(state.isCounterFieldVisible)
        assertTrue(state.isInEditMode)
        assertFalse(viewModel.isFormUpdated())
    }

    @Test
    fun `HOTP and Steam types expose only their applicable fields`() {
        val viewModel = viewModel()

        viewModel.setStateFromToken(
            testToken(otpInfo = HotpInfo(byteArrayOf(1), counter = 7L)),
            TokenSetupMode.UPDATE,
        )
        assertEquals("7", viewModel.uiState.value.counter)
        assertTrue(viewModel.uiState.value.isCounterFieldVisible)
        assertFalse(viewModel.uiState.value.isPeriodFieldVisible)

        viewModel.setStateFromToken(
            testToken(otpInfo = SteamInfo(byteArrayOf(1))),
            TokenSetupMode.UPDATE,
        )
        val steamState = viewModel.uiState.value
        assertEquals(OTPType.STEAM, steamState.type)
        assertFalse(steamState.isAlgorithmFieldVisible)
        assertFalse(steamState.isDigitsFieldVisible)
        assertFalse(steamState.isPeriodFieldVisible)
        assertFalse(steamState.isCounterFieldVisible)
    }

    @Test
    fun `form events normalize secrets and track meaningful changes`() {
        val viewModel = viewModel()
        viewModel.setStateFromToken(testToken(), TokenSetupMode.UPDATE)

        viewModel.onEvent(TokenFormEvent.SecretKeyChanged(" jb swy3dp\nehpk3pxp "))
        viewModel.onEvent(TokenFormEvent.IssuerChanged("Changed"))

        assertEquals("JBSWY3DPEHPK3PXP", viewModel.uiState.value.secretKey)
        assertEquals("Changed", viewModel.uiState.value.issuer)
        assertTrue(viewModel.isFormUpdated())
    }

    @Test
    fun `available labels can be selected and new labels are added case-insensitively`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = RecordingTokenRepository(
                listOf(
                    testToken(id = "one", labels = setOf("Work")),
                    testToken(id = "two", labels = setOf("Personal")),
                )
            )
            val viewModel = viewModel(repository)
            viewModel.setStateFromToken(repository.tokens.first(), TokenSetupMode.UPDATE)
            viewModel.loadAvailableLabels()
            advanceUntilIdle()

            assertEquals(setOf("Work", "Personal"), viewModel.uiState.value.availableLabels)

            viewModel.onEvent(TokenFormEvent.LabelToggled("Personal"))
            assertEquals(setOf("Work", "Personal"), viewModel.uiState.value.labels)

            viewModel.onEvent(TokenFormEvent.LabelToggled("WORK"))
            assertEquals(setOf("Personal"), viewModel.uiState.value.labels)

            viewModel.onEvent(TokenFormEvent.NewLabelChanged(" work "))
            viewModel.onEvent(TokenFormEvent.AddLabel)
            assertEquals(setOf("Personal", "Work"), viewModel.uiState.value.labels)
            assertEquals(setOf("Work", "Personal"), viewModel.uiState.value.availableLabels)
            assertEquals("", viewModel.uiState.value.newLabel)
            assertFalse(viewModel.uiState.value.showAddLabelDialog)
            assertTrue(viewModel.isFormUpdated())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `invalid auth URL produces an explicit edit error`() {
        val viewModel = viewModel()

        viewModel.setStateFromAuthUrl("not-an-otp-url")

        assertIs<DataLoadState.Error>(viewModel.uiState.value.editLoadState)
        assertFalse(viewModel.uiState.value.isInEditMode)
    }

    @Test
    fun `delete requires a loaded token and forwards its id`() = runTest {
        val repository = RecordingTokenRepository()
        val viewModel = viewModel(repository)
        assertFalse(viewModel.deleteToken())

        val token = testToken()
        viewModel.setStateFromToken(token, TokenSetupMode.UPDATE)

        assertTrue(viewModel.deleteToken())
        assertEquals(token.id, repository.lastDeletedId)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `archive marks the loaded token without deleting it`() = runTest {
        val token = testToken()
        val repository = RecordingTokenRepository(listOf(token))
        val viewModel = viewModel(repository)
        viewModel.setStateFromToken(token, TokenSetupMode.UPDATE)

        assertTrue(viewModel.toggleArchiveToken())

        assertEquals(1, repository.tokens.size)
        assertTrue(repository.tokens.single().isArchived)
        assertFalse(viewModel.uiState.value.isSaving)

        viewModel.setStateFromToken(repository.tokens.single(), TokenSetupMode.UPDATE)
        assertTrue(viewModel.toggleArchiveToken())
        assertFalse(repository.tokens.single().isArchived)
    }

    @Test
    fun `dialog state setters do not mutate form content`() {
        val viewModel = viewModel()
        val initialIssuer = viewModel.uiState.value.issuer

        viewModel.showBackPressDialog(true)
        viewModel.showDeleteTokenDialog(true)
        viewModel.showArchiveTokenDialog(true)
        viewModel.showDuplicateTokenDialog(TokenSetupViewModel.DuplicateTokenDialogArgs(true))

        val state = viewModel.uiState.value
        assertTrue(state.showBackPressDialog)
        assertTrue(state.showDeleteTokenDialog)
        assertTrue(state.showArchiveTokenDialog)
        assertTrue(state.showDuplicateTokenDialog.show)
        assertEquals(initialIssuer, state.issuer)
    }

    private fun viewModel(
        repository: RecordingTokenRepository = RecordingTokenRepository(),
    ) = TokenSetupViewModel(
        settings = SettingsDataStore(InMemoryPreferenceStore()),
        fetchTokenByIdUseCase = FetchTokenByIdUseCase(repository),
        fetchLabelsUseCase = FetchLabelsUseCase(repository),
        insertTokenUseCase = InsertTokenUseCase(repository),
        updateTokenUseCase = UpdateTokenUseCase(repository),
        deleteTokenUseCase = DeleteTokenUseCase(repository),
        replaceExistingTokenUseCase = ReplaceExistingTokenUseCase(repository),
        formValidator = TokenFormValidator(),
    )
}
