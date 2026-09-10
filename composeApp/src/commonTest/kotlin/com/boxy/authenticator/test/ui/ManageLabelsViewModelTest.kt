package com.boxy.authenticator.test.ui

import com.boxy.authenticator.domain.models.LabelSummary
import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.domain.models.enums.LabelVisibility
import com.boxy.authenticator.domain.models.enums.shouldShowLabels
import com.boxy.authenticator.domain.usecases.DeleteLabelUseCase
import com.boxy.authenticator.domain.usecases.FetchLabelsUseCase
import com.boxy.authenticator.domain.usecases.RenameLabelUseCase
import com.boxy.authenticator.test.RecordingTokenRepository
import com.boxy.authenticator.test.testToken
import com.boxy.authenticator.test.InMemoryPreferenceStore
import com.boxy.authenticator.ui.state.DataLoadState
import com.boxy.authenticator.ui.viewmodels.ManageLabelsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ManageLabelsViewModelTest {
    @Test
    fun `visibility modes match collapsed and expanded cards`() {
        assertFalse(LabelVisibility.NEVER.shouldShowLabels(isExpanded = false))
        assertFalse(LabelVisibility.NEVER.shouldShowLabels(isExpanded = true))
        assertFalse(LabelVisibility.WHEN_EXPANDED.shouldShowLabels(isExpanded = false))
        assertTrue(LabelVisibility.WHEN_EXPANDED.shouldShowLabels(isExpanded = true))
        assertTrue(LabelVisibility.ALWAYS.shouldShowLabels(isExpanded = false))
        assertTrue(LabelVisibility.ALWAYS.shouldShowLabels(isExpanded = true))
    }

    @Test
    fun `load returns labels with token counts`() = runTest {
        withMainDispatcher(UnconfinedTestDispatcher(testScheduler)) {
            val repository = RecordingTokenRepository(
                listOf(
                    testToken(id = "one", labels = setOf("Work", "Admin")),
                    testToken(id = "two", labels = setOf("work")),
                )
            )
            val viewModel = viewModel(repository)

            viewModel.loadLabels()

            val labels = assertIs<DataLoadState.Data<List<LabelSummary>>>(
                viewModel.uiState.value.labelsState
            ).value
            assertEquals(listOf(LabelSummary("Admin", 1), LabelSummary("Work", 2)), labels)
        }
    }

    @Test
    fun `renaming to an existing label merges memberships`() = runTest {
        withMainDispatcher(UnconfinedTestDispatcher(testScheduler)) {
            val repository = RecordingTokenRepository(
                listOf(
                    testToken(id = "one", labels = setOf("Personal", "Work")),
                    testToken(id = "two", labels = setOf("personal")),
                )
            )
            val viewModel = viewModel(repository)
            viewModel.showRenameDialog(LabelSummary("Personal", 2))
            viewModel.updateEditedName(" Work ")

            viewModel.renameLabel()

            assertNull(viewModel.uiState.value.editingLabel)
            assertEquals(setOf("Work"), repository.tokens[0].labels)
            assertEquals(setOf("Work"), repository.tokens[1].labels)
        }
    }

    @Test
    fun `deleting a label keeps every account`() = runTest {
        withMainDispatcher(UnconfinedTestDispatcher(testScheduler)) {
            val repository = RecordingTokenRepository(
                listOf(testToken(labels = setOf("Work", "Admin")))
            )
            val viewModel = viewModel(repository)
            viewModel.showDeleteDialog(LabelSummary("Work", 1))

            viewModel.deleteLabel()

            assertNull(viewModel.uiState.value.deletingLabel)
            assertEquals(1, repository.tokens.size)
            assertEquals(setOf("Admin"), repository.tokens.single().labels)
        }
    }

    @Test
    fun `invalid rename stays in dialog and does not mutate labels`() = runTest {
        withMainDispatcher(UnconfinedTestDispatcher(testScheduler)) {
            val repository = RecordingTokenRepository(
                listOf(testToken(labels = setOf("Work")))
            )
            val viewModel = viewModel(repository)
            viewModel.showRenameDialog(LabelSummary("Work", 1))
            viewModel.updateEditedName("   ")

            viewModel.renameLabel()

            assertEquals("Work", viewModel.uiState.value.editingLabel?.name)
            assertTrue(viewModel.uiState.value.validationError?.isNotBlank() == true)
            assertEquals(setOf("Work"), repository.tokens.single().labels)
        }
    }

    @Test
    fun `renaming and deleting a default label keeps the preference consistent`() = runTest {
        withMainDispatcher(UnconfinedTestDispatcher(testScheduler)) {
            val settings = SettingsDataStore(InMemoryPreferenceStore()).apply {
                setDefaultLabelFilter("Personal")
            }
            val repository = RecordingTokenRepository(
                listOf(testToken(labels = setOf("Personal")))
            )
            val viewModel = viewModel(repository, settings)
            viewModel.showRenameDialog(LabelSummary("Personal", 1))
            viewModel.updateEditedName("Private")

            viewModel.renameLabel()
            assertEquals("Private", settings.getDefaultLabelFilter())

            viewModel.showDeleteDialog(LabelSummary("Private", 1))
            viewModel.deleteLabel()
            assertNull(settings.getDefaultLabelFilter())
        }
    }

    private fun viewModel(
        repository: RecordingTokenRepository,
        settings: SettingsDataStore = SettingsDataStore(InMemoryPreferenceStore()),
    ) = ManageLabelsViewModel(
        fetchLabelsUseCase = FetchLabelsUseCase(repository),
        renameLabelUseCase = RenameLabelUseCase(repository),
        deleteLabelUseCase = DeleteLabelUseCase(repository),
        settingsDataStore = settings,
    )

    private suspend fun withMainDispatcher(
        dispatcher: CoroutineDispatcher,
        block: suspend () -> Unit,
    ) {
        Dispatchers.setMain(dispatcher)
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
