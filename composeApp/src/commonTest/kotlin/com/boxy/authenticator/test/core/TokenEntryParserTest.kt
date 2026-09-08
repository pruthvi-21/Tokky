package com.boxy.authenticator.test.core

import com.boxy.authenticator.core.TokenEntryParser
import com.boxy.authenticator.core.encoding.Base32
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.models.enums.AccountEntryMethod
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.models.otp.SteamInfo
import com.boxy.authenticator.domain.models.otp.TotpInfo
import com.boxy.authenticator.utils.BadlyFormedURLException
import com.boxy.authenticator.utils.EmptyURLContentException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertIs

class TokenEntryParserTest {

    @Test
    fun `should parse valid TOTP URL correctly`() {
        val url = "otpauth://totp/jilebi%40gmail.com?secret=JBSWY3DPEHPK3PXP&issuer=Jilebi%20Labs"
        val tokenEntry: TokenEntry = TokenEntryParser.buildFromUrl(url)

        assertNotNull(tokenEntry)
        assertEquals("Jilebi Labs", tokenEntry.issuer)
        assertEquals("jilebi@gmail.com", tokenEntry.label)
        assertEquals("JBSWY3DPEHPK3PXP", Base32.encode(tokenEntry.otpInfo.secretKey))
    }

    @Test
    fun `should throw EmptyURLContentException when URL is null or empty`() {
        assertFailsWith<EmptyURLContentException> {
            TokenEntryParser.buildFromUrl(null)
        }
        assertFailsWith<EmptyURLContentException> {
            TokenEntryParser.buildFromUrl("")
        }
    }

    @Test
    fun `should throw BadlyFormedURLException when URL format is incorrect`() {
        val invalidUrl = "https://example.com/totp?secret=INVALID"
        assertFailsWith<BadlyFormedURLException> {
            TokenEntryParser.buildFromUrl(invalidUrl)
        }
    }

    @Test
    fun `should throw error when required parameters are missing`() {
        val missingSecretUrl = "otpauth://totp/Google:myemail@gmail.com?issuer=Google"
        assertFailsWith<BadlyFormedURLException> {
            TokenEntryParser.buildFromUrl(missingSecretUrl)
        }
    }

    @Test
    fun `TOTP defaults algorithm digits and period`() {
        val token = TokenEntryParser.buildFromUrl(
            "otpauth://totp/Example:person?secret=JBSWY3DPEHPK3PXP&issuer=Example",
        )

        val info = assertIs<TotpInfo>(token.otpInfo)
        assertEquals("SHA1", info.algorithm)
        assertEquals(6, info.digits)
        assertEquals(30L, info.period)
        assertEquals(AccountEntryMethod.QR_CODE, token.addedFrom)
    }

    @Test
    fun `TOTP parses custom algorithm digits period and encoded label`() {
        val token = TokenEntryParser.buildFromUrl(
            "otpauth://totp/Example%20Inc:user%2Btag%40example.com" +
                    "?secret=JBSWY3DPEHPK3PXP&issuer=Example%20Inc" +
                    "&algorithm=SHA256&digits=8&period=45",
        )

        val info = assertIs<TotpInfo>(token.otpInfo)
        assertEquals("Example Inc", token.issuer)
        assertEquals("user+tag@example.com", token.label)
        assertEquals("SHA256", info.algorithm)
        assertEquals(8, info.digits)
        assertEquals(45L, info.period)
    }

    @Test
    fun `HOTP parses counter and defaults it to zero when absent`() {
        val explicit = TokenEntryParser.buildFromUrl(
            "otpauth://hotp/Example:counter?secret=JBSWY3DPEHPK3PXP&issuer=Example&counter=99",
        )
        val defaulted = TokenEntryParser.buildFromUrl(
            "otpauth://hotp/Example:counter?secret=JBSWY3DPEHPK3PXP&issuer=Example",
        )

        assertEquals(99L, assertIs<HotpInfo>(explicit.otpInfo).counter)
        assertEquals(0L, assertIs<HotpInfo>(defaulted.otpInfo).counter)
    }

    @Test
    fun `Steam URL creates Steam-specific OTP info`() {
        val token = TokenEntryParser.buildFromUrl(
            "otpauth://steam/Steam:user?secret=JBSWY3DPEHPK3PXP&issuer=Steam",
        )

        val info = assertIs<SteamInfo>(token.otpInfo)
        assertEquals(5, info.digits)
    }

    @Test
    fun `secret accepts spaces and lowercase`() {
        val token = TokenEntryParser.buildFromUrl(
            "otpauth://totp/Example:user?secret=jbsw%20y3dp%20ehpk3pxp&issuer=Example",
        )

        assertEquals(
            "JBSWY3DPEHPK3PXP",
            Base32.encode(token.otpInfo.secretKey).trimEnd('='),
        )
    }

    @Test
    fun `unsupported OTP type and invalid numeric values fail`() {
        assertFailsWith<IllegalArgumentException> {
            TokenEntryParser.buildFromUrl(
                "otpauth://unknown/Example:user?secret=JBSWY3DPEHPK3PXP",
            )
        }
        assertFailsWith<NumberFormatException> {
            TokenEntryParser.buildFromUrl(
                "otpauth://totp/Example:user?secret=JBSWY3DPEHPK3PXP&period=abc",
            )
        }
    }
}
