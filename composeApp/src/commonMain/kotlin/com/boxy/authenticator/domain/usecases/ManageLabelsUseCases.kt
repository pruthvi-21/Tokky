package com.boxy.authenticator.domain.usecases

import com.boxy.authenticator.core.runSuspendCatching
import com.boxy.authenticator.domain.models.LabelSummary
import com.boxy.authenticator.domain.repository.TokenRepository

class FetchLabelsUseCase(private val repository: TokenRepository) {
    suspend operator fun invoke(): Result<List<LabelSummary>> = runSuspendCatching {
        repository.getLabels()
    }
}

class RenameLabelUseCase(private val repository: TokenRepository) {
    suspend operator fun invoke(oldName: String, newName: String): Result<Unit> = runSuspendCatching {
        repository.renameLabel(oldName, newName)
    }
}

class DeleteLabelUseCase(private val repository: TokenRepository) {
    suspend operator fun invoke(name: String): Result<Unit> = runSuspendCatching {
        repository.deleteLabel(name)
    }
}
