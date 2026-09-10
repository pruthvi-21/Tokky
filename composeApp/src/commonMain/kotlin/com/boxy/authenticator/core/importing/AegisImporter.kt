package com.boxy.authenticator.core.importing

import at.asitplus.signum.indispensable.kdf.SCrypt
import at.asitplus.signum.indispensable.misc.BitLength
import at.asitplus.signum.indispensable.symmetric.SymmetricEncryptionAlgorithm
import at.asitplus.signum.indispensable.symmetric.keyFrom
import at.asitplus.signum.supreme.kdf.deriveKey
import at.asitplus.signum.supreme.symmetric.decrypt
import com.boxy.authenticator.core.ImportedTokenValidator
import com.boxy.authenticator.core.encoding.Base32
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.models.enums.AccountEntryMethod
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.models.otp.SteamInfo
import com.boxy.authenticator.domain.models.otp.TotpInfo
import com.boxy.authenticator.utils.Constants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Imports the documented Aegis JSON vault format without accepting ambiguous structures. */
object AegisImporter {
    private const val OUTER_VERSION = 1
    private val SUPPORTED_DATABASE_VERSIONS = 1..3
    private const val PASSWORD_SLOT = 1
    private const val MAX_SCRYPT_MEMORY_BYTES = 256L * 1024L * 1024L
    private const val MAX_SCRYPT_BLOCK_SIZE = 64
    private const val MAX_SCRYPT_PARALLELIZATION = 16
    private const val AES_KEY_BYTES = 32
    private const val AES_GCM_NONCE_BYTES = 12
    private const val AES_GCM_TAG_BYTES = 16

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }

    sealed interface Inspection {
        class Plain internal constructor(internal val database: AegisDatabase) : Inspection
        class Encrypted internal constructor(internal val vault: AegisVault) : Inspection
    }

    fun inspect(contents: String): Inspection {
        val vault = json.decodeFromString<AegisVault>(contents)
        if (vault.version != OUTER_VERSION) throw UnsupportedAegisVersionException(vault.version)

        val isPlain = vault.header.slots == null && vault.header.params == null && vault.db is JsonObject
        val isEncrypted = vault.header.slots != null && vault.header.params != null &&
                vault.db is JsonPrimitive && vault.db.isString
        return when {
            isPlain -> Inspection.Plain(decodeDatabase(vault.db))
            isEncrypted -> {
                if (vault.header.slots.none { it.type == PASSWORD_SLOT }) {
                    throw UnsupportedAegisEncryptionException()
                }
                validateAesParams(vault.header.params)
                vault.header.slots.filter { it.type == PASSWORD_SLOT }.forEach(::validatePasswordSlot)
                Inspection.Encrypted(vault)
            }
            else -> throw InvalidAegisBackupException()
        }
    }

    fun tokens(inspection: Inspection.Plain): List<TokenEntry> = mapTokens(inspection.database)

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun decrypt(inspection: Inspection.Encrypted, password: String): List<TokenEntry> {
        if (password.isEmpty()) throw InvalidAegisPasswordException()
        val vault = inspection.vault
        val encryptedDatabase = runCatching { Base64.decode((vault.db as JsonPrimitive).content) }
            .getOrElse { throw InvalidAegisBackupException() }
        val databaseParams = vault.header.params ?: throw InvalidAegisBackupException()

        for (slot in vault.header.slots.orEmpty().filter { it.type == PASSWORD_SLOT }) {
            val masterKey = runCatching { decryptMasterKey(slot, password) }.getOrNull() ?: continue
            val databaseBytes = try {
                decryptAesGcm(masterKey, databaseParams, encryptedDatabase)
            } catch (_: Throwable) {
                continue
            } finally {
                masterKey.fill(0)
            }
            val databaseText = try {
                databaseBytes.decodeToString(throwOnInvalidSequence = true)
            } catch (_: Throwable) {
                databaseBytes.fill(0)
                throw InvalidAegisBackupException()
            }
            databaseBytes.fill(0)
            return mapTokens(decodeDatabase(json.parseToJsonElement(databaseText)))
        }
        throw InvalidAegisPasswordException()
    }

    private suspend fun decryptMasterKey(slot: AegisSlot, password: String): ByteArray {
        validatePasswordSlot(slot)
        val passwordBytes = password.encodeToByteArray()
        val salt = decodeHex(slot.salt!!)
        val derivedKey = try {
            SCrypt(
                cost = slot.n!!,
                parallelization = slot.p!!,
                blockSize = slot.r!!,
            ).deriveKey(
                salt = salt,
                ikm = passwordBytes,
                derivedKeyLength = BitLength(256u),
            ).getOrThrow()
        } finally {
            passwordBytes.fill(0)
            salt.fill(0)
        }
        return try {
            decryptAesGcm(derivedKey, slot.keyParams!!, decodeHex(slot.key!!)).also {
                if (it.size != AES_KEY_BYTES) {
                    it.fill(0)
                    throw InvalidAegisBackupException()
                }
            }
        } finally {
            derivedKey.fill(0)
        }
    }

    private suspend fun decryptAesGcm(
        key: ByteArray,
        params: AegisAesParams,
        ciphertext: ByteArray,
    ): ByteArray {
        validateAesParams(params)
        val algorithm = SymmetricEncryptionAlgorithm.AES_256.GCM
        val fixedKey = algorithm.keyFrom(key).getOrThrow()
        return fixedKey.decrypt(
            nonce = decodeHex(params.nonce),
            encryptedData = ciphertext,
            authTag = decodeHex(params.tag),
        ).getOrThrow()
    }

    private fun decodeDatabase(element: JsonElement): AegisDatabase {
        val database = try {
            json.decodeFromJsonElement<AegisDatabase>(element)
        } catch (error: UnsupportedAegisVersionException) {
            throw error
        } catch (_: Throwable) {
            throw InvalidAegisBackupException()
        }
        if (database.version !in SUPPORTED_DATABASE_VERSIONS) {
            throw UnsupportedAegisVersionException(database.version)
        }
        if (database.entries.size > Constants.MAX_IMPORT_TOKENS) throw InvalidAegisBackupException()
        return database
    }

    private fun mapTokens(database: AegisDatabase): List<TokenEntry> {
        val groupNames = database.groups.associate { it.uuid to it.name }
        return database.entries.map { entry ->
            val secret = try {
                Base32.decode(entry.info.secret.trim())
            } catch (_: Throwable) {
                throw InvalidAegisBackupException()
            }
            val algorithm = entry.info.algorithm.uppercase().replace("-", "")
            val otpInfo = when (entry.type.lowercase()) {
                "totp" -> TotpInfo(secret, algorithm, entry.info.digits, entry.info.period ?: 30L)
                "hotp" -> HotpInfo(secret, algorithm, entry.info.digits, entry.info.counter ?: 0L)
                "steam" -> SteamInfo(secret)
                else -> throw UnsupportedAegisTokenTypeException(entry.type)
            }
            val issuer = entry.issuer.trim().ifEmpty { entry.name.trim() }
            val label = if (entry.issuer.isBlank()) "" else entry.name.trim()
            ImportedTokenValidator.validate(
                TokenEntry.create(
                    issuer = issuer,
                    label = label,
                    otpInfo = otpInfo,
                    addedFrom = AccountEntryMethod.RESTORED,
                    labels = entry.groups.mapNotNull(groupNames::get).toSet(),
                )
            )
        }
    }

    private fun validatePasswordSlot(slot: AegisSlot) {
        val n = slot.n ?: throw InvalidAegisBackupException()
        val r = slot.r ?: throw InvalidAegisBackupException()
        val p = slot.p ?: throw InvalidAegisBackupException()
        if (n <= 1 || n and (n - 1) != 0 || r !in 1..MAX_SCRYPT_BLOCK_SIZE ||
            p !in 1..MAX_SCRYPT_PARALLELIZATION
        ) {
            throw InvalidAegisBackupException()
        }
        val estimatedMemory = 128L * n * r
        if (estimatedMemory > MAX_SCRYPT_MEMORY_BYTES) throw InvalidAegisBackupException()
        val salt = decodeHex(slot.salt ?: throw InvalidAegisBackupException())
        if (salt.size !in 16..64) throw InvalidAegisBackupException()
        val key = decodeHex(slot.key ?: throw InvalidAegisBackupException())
        if (key.size != AES_KEY_BYTES) throw InvalidAegisBackupException()
        validateAesParams(slot.keyParams ?: throw InvalidAegisBackupException())
    }

    private fun validateAesParams(params: AegisAesParams) {
        if (decodeHex(params.nonce).size != AES_GCM_NONCE_BYTES ||
            decodeHex(params.tag).size != AES_GCM_TAG_BYTES
        ) throw InvalidAegisBackupException()
    }

    private fun decodeHex(value: String): ByteArray {
        if (value.length % 2 != 0 || value.length > 256 || !value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            throw InvalidAegisBackupException()
        }
        return ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }
}

class UnsupportedAegisVersionException(val version: Int) : Exception()
class UnsupportedAegisTokenTypeException(val type: String) : Exception()
class UnsupportedAegisEncryptionException : Exception()
class InvalidAegisPasswordException : Exception()
class InvalidAegisBackupException : Exception()

@Serializable
internal data class AegisVault(
    val version: Int,
    val header: AegisHeader,
    val db: JsonElement,
)

@Serializable
internal data class AegisHeader(
    val slots: List<AegisSlot>? = null,
    val params: AegisAesParams? = null,
)

@Serializable
internal data class AegisSlot(
    val type: Int,
    val n: Int? = null,
    val r: Int? = null,
    val p: Int? = null,
    val salt: String? = null,
    val key: String? = null,
    @SerialName("key_params") val keyParams: AegisAesParams? = null,
)

@Serializable
internal data class AegisAesParams(val nonce: String, val tag: String)

@Serializable
internal data class AegisDatabase(
    val version: Int,
    val entries: List<AegisEntry>,
    val groups: List<AegisGroup> = emptyList(),
)

@Serializable
internal data class AegisGroup(val uuid: String, val name: String)

@Serializable
internal data class AegisEntry(
    val type: String,
    val name: String,
    val issuer: String = "",
    val info: AegisEntryInfo,
    val groups: List<String> = emptyList(),
)

@Serializable
internal data class AegisEntryInfo(
    val secret: String,
    @SerialName("algo") val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Long? = null,
    val counter: Long? = null,
)
