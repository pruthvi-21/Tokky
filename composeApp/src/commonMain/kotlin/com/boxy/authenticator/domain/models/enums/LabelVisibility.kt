package com.boxy.authenticator.domain.models.enums

enum class LabelVisibility {
    NEVER,
    WHEN_EXPANDED,
    ALWAYS,
}

fun LabelVisibility.shouldShowLabels(isExpanded: Boolean): Boolean = when (this) {
    LabelVisibility.NEVER -> false
    LabelVisibility.WHEN_EXPANDED -> isExpanded
    LabelVisibility.ALWAYS -> true
}
