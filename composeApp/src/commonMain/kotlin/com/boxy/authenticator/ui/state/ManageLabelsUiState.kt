package com.boxy.authenticator.ui.state

import com.boxy.authenticator.domain.models.LabelSummary

data class ManageLabelsUiState(
    val labelsState: DataLoadState<List<LabelSummary>> = DataLoadState.Initial,
    val editingLabel: LabelSummary? = null,
    val deletingLabel: LabelSummary? = null,
    val editedName: String = "",
    val validationError: String? = null,
    val operationError: String? = null,
    val isSaving: Boolean = false,
)
