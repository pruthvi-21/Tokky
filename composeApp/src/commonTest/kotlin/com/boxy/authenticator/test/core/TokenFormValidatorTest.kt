package com.boxy.authenticator.test.core

import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.error_counter_invalid
import boxy_authenticator.composeapp.generated.resources.error_issuer_empty
import boxy_authenticator.composeapp.generated.resources.error_period_empty
import boxy_authenticator.composeapp.generated.resources.error_period_invalid
import boxy_authenticator.composeapp.generated.resources.error_secret_key_empty
import boxy_authenticator.composeapp.generated.resources.error_secret_key_invalid
import com.boxy.authenticator.core.TokenFormValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TokenFormValidatorTest {
    private val validator = TokenFormValidator()

    @Test
    fun `issuer must not be empty`() {
        assertFailure(validator.validateIssuer(""), Res.string.error_issuer_empty)
        assertFailure(validator.validateIssuer("   "), Res.string.error_issuer_empty)
        assertIs<TokenFormValidator.Result.Failure>(validator.validateIssuer("x".repeat(257)))
        assertIs<TokenFormValidator.Result.Success>(validator.validateIssuer("Example"))
    }

    @Test
    fun `valid base32 secrets are accepted case insensitively`() {
        assertIs<TokenFormValidator.Result.Success>(
            validator.validateSecretKey("jbswy3dpehpk3pxp"),
        )
    }

    @Test
    fun `valid base32 secrets are accepted with whitespace and padding`() {
        assertIs<TokenFormValidator.Result.Success>(
            validator.validateSecretKey(" JBSW Y3DP\nEHPK3PXP==== "),
        )
    }

    @Test
    fun `empty decoded secret and invalid alphabet are rejected separately`() {
        assertFailure(validator.validateSecretKey(""), Res.string.error_secret_key_empty)
        assertFailure(validator.validateSecretKey("   "), Res.string.error_secret_key_empty)
        assertFailure(validator.validateSecretKey("NOT-BASE32"), Res.string.error_secret_key_invalid)
    }

    @Test
    fun `period must be a positive integer`() {
        assertFailure(validator.validatePeriod(""), Res.string.error_period_empty)
        listOf("0", "-1", "1.5", "abc", "86401", Long.MAX_VALUE.toString()).forEach {
            assertFailure(validator.validatePeriod(it), Res.string.error_period_invalid)
        }
        assertIs<TokenFormValidator.Result.Success>(validator.validatePeriod("30"))
        assertIs<TokenFormValidator.Result.Success>(validator.validatePeriod("86400"))
    }

    @Test
    fun `counter accepts zero and full Long range but rejects invalid values`() {
        assertIs<TokenFormValidator.Result.Success>(validator.validateCounter("0"))
        assertIs<TokenFormValidator.Result.Success>(
            validator.validateCounter(Long.MAX_VALUE.toString()),
        )
        listOf("", "-1", "1.5", "abc").forEach {
            assertFailure(validator.validateCounter(it), Res.string.error_counter_invalid)
        }
    }

    private fun assertFailure(
        result: TokenFormValidator.Result,
        expected: org.jetbrains.compose.resources.StringResource,
    ) {
        val failure = assertIs<TokenFormValidator.Result.Failure>(result)
        assertEquals(expected, failure.errorMessage)
    }
}
