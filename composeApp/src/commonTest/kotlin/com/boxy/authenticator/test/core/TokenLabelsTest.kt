package com.boxy.authenticator.test.core

import com.boxy.authenticator.core.TokenLabels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TokenLabelsTest {
    @Test
    fun `normalizes whitespace and case-insensitive duplicates`() {
        assertEquals(
            linkedSetOf("Work", "Personal"),
            TokenLabels.normalize(listOf(" Work ", "work", "Personal")),
        )
    }

    @Test
    fun `allows an arbitrary number of distinct labels`() {
        val labels = (1..500).map { "label-$it" }

        assertEquals(500, TokenLabels.normalize(labels).size)
    }

    @Test
    fun `rejects empty oversized and control-character labels`() {
        assertFailsWith<IllegalArgumentException> { TokenLabels.normalize(listOf(" ")) }
        assertFailsWith<IllegalArgumentException> {
            TokenLabels.normalize(listOf("x".repeat(TokenLabels.MAX_LENGTH + 1)))
        }
        assertFailsWith<IllegalArgumentException> { TokenLabels.normalize(listOf("work\nsecret")) }
    }
}
