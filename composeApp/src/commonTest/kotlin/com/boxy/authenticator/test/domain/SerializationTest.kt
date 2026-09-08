package com.boxy.authenticator.test.domain

import com.boxy.authenticator.core.serialization.BoxyJson
import com.boxy.authenticator.domain.models.ExportableTokenEntry
import com.boxy.authenticator.domain.models.Thumbnail
import com.boxy.authenticator.domain.models.enums.AccountEntryMethod
import com.boxy.authenticator.domain.models.enums.ThumbnailIcon
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.models.otp.OtpInfo
import com.boxy.authenticator.domain.models.otp.SteamInfo
import com.boxy.authenticator.domain.models.otp.TotpInfo
import com.boxy.authenticator.test.testToken
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SerializationTest {
    @Test
    fun `color and icon thumbnails round trip`() {
        val thumbnails = listOf(
            Thumbnail.Color("#123456"),
            Thumbnail.Icon(ThumbnailIcon.GITHUB),
        )

        thumbnails.forEach { thumbnail ->
            assertEquals(thumbnail, Thumbnail.deserialize(thumbnail.serialize()))
        }
    }

    @Test
    fun `invalid thumbnail representations are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Thumbnail.deserialize("\"unknown:value\"")
        }
        assertFailsWith<IllegalArgumentException> {
            Thumbnail.deserialize("\"icon:DOES_NOT_EXIST\"")
        }
    }

    @Test
    fun `TOTP preserves all serialized fields`() {
        val original = TotpInfo(byteArrayOf(1, 2, 3), "SHA256", 8, 45L)
        val decoded = assertIs<TotpInfo>(OtpInfo.deserialize(original.serialize()))

        assertContentEquals(original.secretKey, decoded.secretKey)
        assertEquals("SHA256", decoded.algorithm)
        assertEquals(8, decoded.digits)
        assertEquals(45L, decoded.period)
    }

    @Test
    fun `HOTP preserves all serialized fields`() {
        val original = HotpInfo(byteArrayOf(4, 5, 6), "SHA512", 7, 123L)
        val decoded = assertIs<HotpInfo>(OtpInfo.deserialize(original.serialize()))

        assertContentEquals(original.secretKey, decoded.secretKey)
        assertEquals("SHA512", decoded.algorithm)
        assertEquals(7, decoded.digits)
        assertEquals(123L, decoded.counter)
    }

    @Test
    fun `Steam serialization restores Steam subtype and secret`() {
        val original = SteamInfo(byteArrayOf(7, 8, 9))
        val decoded = assertIs<SteamInfo>(OtpInfo.deserialize(original.serialize()))

        assertContentEquals(original.secretKey, decoded.secretKey)
        assertEquals(SteamInfo.DIGITS, decoded.digits)
    }

    @Test
    fun `malformed OTP JSON reports serialization failure`() {
        assertFailsWith<SerializationException> { OtpInfo.deserialize("not-json") }
        assertFailsWith<SerializationException> {
            OtpInfo.deserialize("""{"type":"unknown","secretKey":"AA"}""")
        }
    }

    @Test
    fun `export model drops database metadata and restores as a new token`() {
        val original = testToken(id = "database-id")

        val restored = ExportableTokenEntry.fromTokenEntry(original).toTokenEntry()

        assertEquals(original.issuer, restored.issuer)
        assertEquals(original.label, restored.label)
        assertEquals(original.thumbnail, restored.thumbnail)
        assertContentEquals(original.otpInfo.secretKey, restored.otpInfo.secretKey)
        assertEquals(AccountEntryMethod.RESTORED, restored.addedFrom)
        kotlin.test.assertNotEquals(original.id, restored.id)
    }

    @Test
    fun `exportable token list round trips through Boxy JSON`() {
        val originals = listOf(
            ExportableTokenEntry.fromTokenEntry(testToken(id = "one")),
            ExportableTokenEntry.fromTokenEntry(
                testToken(id = "two", otpInfo = HotpInfo(byteArrayOf(9), counter = 4L)),
            ),
        )

        val decoded = BoxyJson.decodeFromString<List<ExportableTokenEntry>>(
            BoxyJson.encodeToString(originals),
        )

        assertEquals(2, decoded.size)
        assertIs<TotpInfo>(decoded[0].otpInfo)
        assertIs<HotpInfo>(decoded[1].otpInfo)
        assertEquals(4L, (decoded[1].otpInfo as HotpInfo).counter)
    }
}
