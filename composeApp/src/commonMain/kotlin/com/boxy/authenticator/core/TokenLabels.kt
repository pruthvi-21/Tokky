package com.boxy.authenticator.core

/** Canonicalizes user and imported labels while preserving the first spelling entered. */
object TokenLabels {
    const val MAX_LENGTH = 64

    fun normalize(labels: Iterable<String>): Set<String> {
        val seen = mutableSetOf<String>()
        return buildSet {
            labels.forEach { rawLabel ->
                val label = rawLabel.trim()
                require(label.isNotEmpty()) { "A label cannot be empty." }
                require(label.length <= MAX_LENGTH) { "A label is too long." }
                require(label.none(Char::isISOControl)) { "A label contains control characters." }
                if (seen.add(label.lowercase())) add(label)
            }
        }
    }
}
