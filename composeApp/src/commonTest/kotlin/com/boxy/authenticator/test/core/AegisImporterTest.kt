package com.boxy.authenticator.test.core

import com.boxy.authenticator.core.importing.AegisImporter
import com.boxy.authenticator.core.importing.InvalidAegisBackupException
import com.boxy.authenticator.core.importing.InvalidAegisPasswordException
import com.boxy.authenticator.core.importing.UnsupportedAegisEncryptionException
import com.boxy.authenticator.core.importing.UnsupportedAegisTokenTypeException
import com.boxy.authenticator.core.importing.UnsupportedAegisVersionException
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.models.otp.SteamInfo
import com.boxy.authenticator.domain.models.otp.TotpInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AegisImporterTest {
    @Test
    fun importsAllSupportedTypesAndMapsNames() {
        val inspection = assertIs<AegisImporter.Inspection.Plain>(AegisImporter.inspect(plainVault()))
        val tokens = AegisImporter.tokens(inspection)

        assertEquals(3, tokens.size)
        assertEquals("Example", tokens[0].issuer)
        assertEquals("alice@example.test", tokens[0].label)
        assertEquals(setOf("Work"), tokens[0].labels)
        assertIs<TotpInfo>(tokens[0].otpInfo)
        assertEquals(45, (tokens[0].otpInfo as TotpInfo).period)
        assertIs<HotpInfo>(tokens[1].otpInfo)
        assertEquals(42, (tokens[1].otpInfo as HotpInfo).counter)
        assertEquals("Steam account", tokens[2].issuer)
        assertEquals("", tokens[2].label)
        assertIs<SteamInfo>(tokens[2].otpInfo)
    }

    @Test
    fun acceptsEverySupportedDatabaseVersion() {
        for (version in 1..3) {
            val inspection = assertIs<AegisImporter.Inspection.Plain>(
                AegisImporter.inspect(plainVault(databaseVersion = version))
            )
            assertEquals(3, AegisImporter.tokens(inspection).size)
        }
    }

    @Test
    fun rejectsUnsupportedOuterVersionBeforeImporting() {
        assertFailsWith<UnsupportedAegisVersionException> {
            AegisImporter.inspect(plainVault(outerVersion = 2))
        }
    }

    @Test
    fun rejectsUnsupportedDatabaseVersion() {
        assertFailsWith<UnsupportedAegisVersionException> {
            AegisImporter.inspect(plainVault(databaseVersion = 4))
        }
    }

    @Test
    fun rejectsMixedPlainAndEncryptedStructure() {
        assertFailsWith<InvalidAegisBackupException> {
            AegisImporter.inspect("""{"version":1,"header":{"slots":[],"params":null},"db":{"version":3,"entries":[]}}""")
        }
    }

    @Test
    fun rejectsBiometricOnlyEncryptedVault() {
        assertFailsWith<UnsupportedAegisEncryptionException> {
            AegisImporter.inspect(
                """{"version":1,"header":{"slots":[{"type":2}],"params":{"nonce":"000000000000000000000000","tag":"00000000000000000000000000000000"}},"db":"AA=="}"""
            )
        }
    }

    @Test
    fun rejectsUnsupportedTokenTypeWithoutPartialImport() {
        val input = plainVault().replace("\"totp\"", "\"motp\"")
        val inspection = assertIs<AegisImporter.Inspection.Plain>(AegisImporter.inspect(input))
        assertFailsWith<UnsupportedAegisTokenTypeException> { AegisImporter.tokens(inspection) }
    }

    @Test
    fun rejectsUnsafeScryptCostBeforePasswordPrompt() {
        val input = encryptedVault().replace("\"n\": 1024", "\"n\": 1073741824")
        assertFailsWith<InvalidAegisBackupException> { AegisImporter.inspect(input) }
    }

    @Test
    fun decryptsIndependentAesGcmAndScryptVector() = runTest {
        val inspection = assertIs<AegisImporter.Inspection.Encrypted>(AegisImporter.inspect(encryptedVault()))
        val tokens = AegisImporter.decrypt(inspection, "correct horse battery staple")

        assertEquals(1, tokens.size)
        assertEquals("Example", tokens.single().issuer)
        assertEquals("alice@example.test", tokens.single().label)
        assertIs<TotpInfo>(tokens.single().otpInfo)
    }

    @Test
    fun rejectsWrongPassword() = runTest {
        val inspection = assertIs<AegisImporter.Inspection.Encrypted>(AegisImporter.inspect(encryptedVault()))
        assertFailsWith<InvalidAegisPasswordException> {
            AegisImporter.decrypt(inspection, "wrong password")
        }
    }

    private fun plainVault(outerVersion: Int = 1, databaseVersion: Int = 3) = """
        {
          "version": $outerVersion,
          "header": {"slots": null, "params": null},
          "db": {
            "version": $databaseVersion,
            "entries": [
              {"type":"totp","name":"alice@example.test","issuer":"Example","groups":["group-work"],"info":{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA-256","digits":8,"period":45}},
              {"type":"hotp","name":"hardware","issuer":"Example","info":{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA1","digits":6,"counter":42}},
              {"type":"steam","name":"Steam account","issuer":"","info":{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA1","digits":5,"period":30}}
            ],
            "groups": [{"uuid":"group-work","name":"Work"}]
          }
        }
    """.trimIndent()

    /** Vector generated independently with Node.js scrypt and AES-256-GCM. All data is synthetic. */
    private fun encryptedVault() = """
        {
          "version": 1,
          "header": {
            "slots": [{
              "type": 1,
              "n": 1024,
              "r": 8,
              "p": 1,
              "salt": "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
              "key": "e5d235d6e848ef5fd4814828f811b967be1b08ca6424a58e5568340111b2fc41",
              "key_params": {"nonce":"a0a1a2a3a4a5a6a7a8a9aaab","tag":"5754bb3082f5483135ee3e83d28cfe1a"}
            }],
            "params": {"nonce":"b0b1b2b3b4b5b6b7b8b9babb","tag":"f3b2880fc7ee0ec673fbba9515251b94"}
          },
          "db": "X0YJzbP2rye6uJTY50rvLxxVTQQDLsSvDbD3LcGp6pCXTSYPNGrEDRA7Okoyx5bB9Il+z1hP3O9ZVXplHnyB4vnVSeG2Hwac2csS4ahCeLimk04Ljyvv8CHLFfsPrU+kaxi9enQfhfPjpSoiZcRkCO0Mj2TQ+Qka158jitJjCHbN+9Nl/Lo6OprRJeYc9ftex8xKeaQx0iVtacaXXp4oXq57Bu8UVtQCAm2uV+rH"
        }
    """.trimIndent()
}
