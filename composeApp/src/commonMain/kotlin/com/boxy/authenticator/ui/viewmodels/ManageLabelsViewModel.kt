package com.boxy.authenticator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxy.authenticator.core.Logger
import com.boxy.authenticator.core.TokenLabels
import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.domain.models.LabelSummary
import com.boxy.authenticator.domain.usecases.DeleteLabelUseCase
import com.boxy.authenticator.domain.usecases.FetchLabelsUseCase
import com.boxy.authenticator.domain.usecases.RenameLabelUseCase
import com.boxy.authenticator.ui.state.DataLoadState
import com.boxy.authenticator.ui.state.ManageLabelsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ManageLabelsViewModel(
    private val fetchLabelsUseCase: FetchLabelsUseCase,
    private val renameLabelUseCase: RenameLabelUseCase,
    private val deleteLabelUseCase: DeleteLabelUseCase,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {
    private val logger = Logger("ManageLabelsViewModel")
    private val _uiState = MutableStateFlow(ManageLabelsUiState())
    val uiState = _uiState.asStateFlow()

    fun loadLabels() {
        if (_uiState.value.labelsState == DataLoadState.Loading) return
        _uiState.value = _uiState.value.copy(labelsState = DataLoadState.Loading)
        viewModelScope.launch {
            fetchLabelsUseCase().fold(
                onSuccess = { labels ->
                    _uiState.value = _uiState.value.copy(labelsState = DataLoadState.Data(labels))
                },
                onFailure = {
                    logger.e("Failed to load labels", it)
                    _uiState.value = _uiState.value.copy(
                        labelsState = DataLoadState.Error("Unable to load labels."),
                    )
                },
            )
        }
    }

    fun showRenameDialog(label: LabelSummary?) {
        _uiState.value = _uiState.value.copy(
            editingLabel = label,
            editedName = label?.name.orEmpty(),
            validationError = null,
        )
    }

    fun updateEditedName(name: String) {
        _uiState.value = _uiState.value.copy(editedName = name, validationError = null)
    }

    fun renameLabel() {
        if (_uiState.value.isSaving) return
        val label = _uiState.value.editingLabel ?: return
        val normalizedName = runCatching {
            TokenLabels.normalize(listOf(_uiState.value.editedName)).single()
        }.getOrElse {
            _uiState.value = _uiState.value.copy(validationError = LABEL_ERROR)
            return
        }
        if (normalizedName == label.name) {
            showRenameDialog(null)
            return
        }
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            renameLabelUseCase(label.name, normalizedName).fold(
                onSuccess = {
                    if (runCatching { settingsDataStore.getDefaultLabelFilter() }.getOrNull()
                            ?.equals(label.name, ignoreCase = true) == true
                    ) {
                        runCatching { settingsDataStore.setDefaultLabelFilter(normalizedName) }
                            .onFailure { logger.e("Failed to update the default label", it) }
                    }
                    _uiState.value = _uiState.value.copy(
                        editingLabel = null,
                        editedName = "",
                        isSaving = false,
                    )
                    loadLabels()
                },
                onFailure = {
                    logger.e("Failed to rename label", it)
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        operationError = "Unable to rename the label.",
                    )
                },
            )
        }
    }

    fun showDeleteDialog(label: LabelSummary?) {
        _uiState.value = _uiState.value.copy(deletingLabel = label)
    }

    fun deleteLabel() {
        if (_uiState.value.isSaving) return
        val label = _uiState.value.deletingLabel ?: return
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            deleteLabelUseCase(label.name).fold(
                onSuccess = {
                    if (runCatching { settingsDataStore.getDefaultLabelFilter() }.getOrNull()
                            ?.equals(label.name, ignoreCase = true) == true
                    ) {
                        runCatching { settingsDataStore.setDefaultLabelFilter(null) }
                            .onFailure { logger.e("Failed to clear the default label", it) }
                    }
                    _uiState.value = _uiState.value.copy(deletingLabel = null, isSaving = false)
                    loadLabels()
                },
                onFailure = {
                    logger.e("Failed to delete label", it)
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        operationError = "Unable to delete the label.",
                    )
                },
            )
        }
    }

    fun clearOperationError() {
        _uiState.value = _uiState.value.copy(operationError = null)
    }

    private companion object {
        const val LABEL_ERROR = "Labels must be 1–${TokenLabels.MAX_LENGTH} characters."
    }
}
