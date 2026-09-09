package com.boxy.authenticator.test.core

import com.boxy.authenticator.core.ImportedTokenValidator
import com.boxy.authenticator.domain.models.Thumbnail
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.models.otp.TotpInfo
import com.boxy.authenticator.test.testToken
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ImportedTokenValidatorTest {
    @Test
    fun `accepts a valid token`() {
        val token = testToken()

        assertSame(token, ImportedTokenValidator.validate(token))
    }

    @Test
    fun `rejects empty secrets`() {
        val token = testToken(otpInfo = TotpInfo(ByteArray(0)))

        assertFailsWith<IllegalArgumentException> {
            ImportedTokenValidator.validate(token)
        }
    }

    @Test
    fun `rejects unsupported algorithms`() {
        val token = testToken(otpInfo = TotpInfo(byteArrayOf(1), algorithm = "MD5"))

        assertFailsWith<IllegalArgumentException> {
            ImportedTokenValidator.validate(token)
        }
    }

    @Test
    fun `rejects unsafe otp parameters`() {
        val invalidPeriod = testToken(otpInfo = TotpInfo(byteArrayOf(1), period = 0))
        val invalidCounter = testToken(otpInfo = HotpInfo(byteArrayOf(1), counter = -1))

        assertFailsWith<IllegalArgumentException> { ImportedTokenValidator.validate(invalidPeriod) }
        assertFailsWith<IllegalArgumentException> { ImportedTokenValidator.validate(invalidCounter) }
    }

    @Test
    fun `rejects malformed thumbnail colors`() {
        val token = testToken().copy(thumbnail = Thumbnail.Color("not-a-color"))

        assertFailsWith<IllegalArgumentException> {
            ImportedTokenValidator.validate(token)
        }
    }
}
