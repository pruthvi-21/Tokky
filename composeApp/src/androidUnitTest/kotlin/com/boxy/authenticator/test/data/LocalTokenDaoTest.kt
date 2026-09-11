package com.boxy.authenticator.test.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.boxy.authenticator.core.ImportedTokenValidator
import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.core.accountNameKey
import com.boxy.authenticator.core.serialization.BoxyJson
import com.boxy.authenticator.data.database.dao.LocalTokenDao
import com.boxy.authenticator.data.database.repository.LocalTokenRepository
import com.boxy.authenticator.db.TokenDatabase
import com.boxy.authenticator.domain.models.ExportableTokenEntry
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.models.otp.TotpInfo
import com.boxy.authenticator.domain.usecases.DeleteTokensUseCase
import com.boxy.authenticator.domain.usecases.FetchTokensUseCase
import com.boxy.authenticator.domain.usecases.UpdateHotpCounterUseCase
import com.boxy.authenticator.domain.usecases.UpdateTokensUseCase
import com.boxy.authenticator.test.InMemoryPreferenceStore
import com.boxy.authenticator.test.testToken
import com.boxy.authenticator.ui.viewmodels.HomeViewModel
import com.boxy.authenticator.utils.StaleTokenException
import com.boxy.authenticator.utils.TokenNameExistsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalTokenDaoTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val database = TokenDatabase(driver)
    private val dao = LocalTokenDao(database)

    @BeforeTest
    fun create() {
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        TokenDatabase.Schema.create(driver)
    }

    @AfterTest
    fun close() = driver.close()

    @Test
    fun `edit cannot create a duplicate name`() {
        val first = testToken(id = "first")
        val second = testToken(id = "second", issuer = "Other")
        dao.insertTokens(listOf(first, second))
        assertFailsWith<TokenNameExistsException> {
            dao.updateToken(second.copy(issuer = first.issuer.uppercase(), labels = setOf("Uncommitted")))
        }
        assertEquals("Other", dao.findTokenWithId(second.id).issuer)
        assertTrue(dao.getLabels().isEmpty())
    }

    @Test
    fun `import conflicts roll back the whole batch including labels`() {
        dao.insertToken(testToken())
        assertFailsWith<TokenNameExistsException> {
            dao.insertTokens(
                listOf(
                    testToken(id = "new", issuer = "New", labels = setOf("New label")),
                    testToken(id = "duplicate"),
                )
            )
        }
        assertEquals(1, dao.getAllTokens().size)
        assertTrue(dao.getLabels().isEmpty())
    }

    @Test
    fun `SQL boundary rejects duplicate writes bypassing the DAO`() {
        dao.insertToken(testToken())
        assertFails {
            driver.execute(
                null, """
                INSERT INTO token_entry SELECT 'duplicate', issuer, label, thumbnail, otpInfo,
                createdOn, updatedOn, addedFrom, isArchived, deletedOn FROM token_entry
            """.trimIndent(), 0
            )
        }
        dao.insertToken(testToken(id = "other", issuer = "Other"))
        assertFails {
            driver.execute(null, "UPDATE token_entry SET issuer = 'Example' WHERE id = 'other'", 0)
        }
    }

    @Test
    fun `stale archive cannot roll back a counter and refreshed archive succeeds`() {
        val token = testToken(otpInfo = HotpInfo(byteArrayOf(1), counter = 0))
        dao.insertToken(token)
        dao.updateHotpCounter(token.id, 1, token.updatedOn)
        assertFailsWith<StaleTokenException> { dao.updateToken(token.copy(isArchived = true)) }
        val refreshed = dao.findTokenWithId(token.id)
        assertEquals(1L, (refreshed.otpInfo as HotpInfo).counter)
        assertTrue(refreshed.updatedOn > token.updatedOn)
        dao.updateToken(refreshed.copy(isArchived = true))
        val archived = dao.findTokenWithId(token.id)
        assertTrue(archived.isArchived)
        assertEquals(1L, (archived.otpInfo as HotpInfo).counter)
    }

    @Test
    fun `counter only advances once and cannot wrap or go backwards`() {
        val token = testToken(otpInfo = HotpInfo(byteArrayOf(1), counter = 4))
        dao.insertToken(token)
        for (counter in listOf(-1L, 3L, 4L, 6L)) {
            assertFailsWith<StaleTokenException> { dao.updateHotpCounter(token.id, counter, 0) }
        }
        dao.updateHotpCounter(token.id, 5, 0)
        assertFailsWith<StaleTokenException> { dao.updateHotpCounter(token.id, 5, 0) }
        assertEquals(5L, (dao.findTokenWithId(token.id).otpInfo as HotpInfo).counter)
        val exhausted = testToken(
            id = "exhausted",
            issuer = "Exhausted",
            otpInfo = HotpInfo(byteArrayOf(1), counter = Long.MAX_VALUE)
        )
        dao.insertToken(exhausted)
        assertFailsWith<StaleTokenException> { dao.updateHotpCounter(exhausted.id, Long.MIN_VALUE, 0) }
        assertEquals(Long.MAX_VALUE, (dao.findTokenWithId(exhausted.id).otpInfo as HotpInfo).counter)
    }

    @Test
    fun `edits reject stale revisions even when the clock has moved backwards`() {
        val token = testToken().copy(updatedOn = Long.MAX_VALUE - 1_000)
        dao.insertToken(token)
        dao.updateToken(token.copy(label = "new"))
        assertFailsWith<StaleTokenException> { dao.updateToken(token.copy(label = "stale")) }
        assertEquals("new", dao.findTokenWithId(token.id).label)
    }

    @Test
    fun `stale recycled or missing edits do not mutate labels`() {
        val token = testToken(labels = setOf("Original"))
        dao.insertToken(token)
        dao.moveTokensToRecycleBin(setOf(token.id), 3_000)
        assertFailsWith<StaleTokenException> { dao.updateToken(token.copy(labels = setOf("Wrong"))) }
        assertEquals(setOf("Original"), dao.getRecycledTokens().single().labels)
        dao.permanentlyDeleteTokens(setOf(token.id))
        assertFailsWith<StaleTokenException> { dao.updateToken(token.copy(labels = setOf("Orphan"))) }
        assertTrue(dao.getLabels().isEmpty())
    }

    @Test
    fun `one stale entry rolls back all edits in a batch`() {
        val first = testToken(id = "first")
        val second = testToken(id = "second", issuer = "Other")
        dao.insertTokens(listOf(first, second))
        dao.moveTokensToRecycleBin(setOf(second.id), 3_000)
        assertFailsWith<StaleTokenException> {
            dao.updateTokens(listOf(first.copy(labels = setOf("Wrong")), second.copy(labels = setOf("Wrong"))))
        }
        assertTrue(dao.findTokenWithId(first.id).labels.isEmpty())
        assertEquals(first.updatedOn, dao.findTokenWithId(first.id).updatedOn)
    }

    @Test
    fun `label changes invalidate earlier edits`() {
        val token = testToken(labels = setOf("Work"))
        dao.insertToken(token)
        dao.renameLabel("Work", "Office", token.updatedOn)
        assertFailsWith<StaleTokenException> { dao.updateToken(token.copy(label = "stale")) }
        val renamed = dao.findTokenWithId(token.id)
        dao.deleteLabel("Office", renamed.updatedOn)
        assertFailsWith<StaleTokenException> { dao.updateToken(renamed) }
        assertTrue(dao.findTokenWithId(token.id).labels.isEmpty())
    }

    @Test
    fun `restore conflict is atomic and permanent deletion preserves active entries`() {
        val recycled = testToken(id = "recycled", labels = setOf("Work"))
        dao.insertToken(recycled)
        dao.moveTokensToRecycleBin(setOf(recycled.id), 3_000)
        dao.insertToken(testToken(id = "active"))
        assertFails { dao.restoreTokens(setOf(recycled.id), 4_000) }
        dao.permanentlyDeleteTokens(setOf("active", recycled.id))
        assertEquals(listOf("active"), dao.getAllTokens().map { it.id })
        assertTrue(dao.getRecycledTokens().isEmpty())
        assertTrue(dao.getLabels().isEmpty())
    }

    @Test
    fun `failed replacement preserves original account and labels`() {
        val original = testToken(labels = setOf("Original"))
        dao.insertToken(original)
        dao.insertToken(testToken(id = "other", issuer = "Other"))
        assertFails { dao.replaceTokenWith(original.id, testToken(id = "replacement", issuer = "Other")) }
        assertEquals(setOf("Original"), dao.findTokenWithId(original.id).labels)
        assertEquals(2, dao.getAllTokens().size)
    }

    @Test
    fun `every write path rejects invalid entries`() {
        val original = testToken()
        dao.insertToken(original)
        val invalids = listOf(
            original.copy(issuer = "   "),
            original.copy(label = "x".repeat(513)),
            original.copy(otpInfo = TotpInfo(byteArrayOf(1), period = Long.MAX_VALUE)),
            original.copy(otpInfo = TotpInfo(byteArrayOf(1), digits = 0)),
            original.copy(otpInfo = HotpInfo(byteArrayOf(1), counter = -1)),
        )
        for (invalid in invalids) {
            assertFails { dao.insertToken(invalid.copy(id = "invalid")) }
            assertFails { dao.updateToken(invalid) }
            assertFails { dao.replaceTokenWith(original.id, invalid.copy(id = "replacement")) }
        }
        assertEquals(original.otpInfo.serialize(), dao.findTokenWithId(original.id).otpInfo.serialize())
    }

    @Test
    fun `valid manual entries round trip through backup validation`() {
        val token = testToken(otpInfo = TotpInfo(byteArrayOf(1), period = 86_400), labels = setOf("Work"))
        dao.insertToken(token)
        val stored = dao.findTokenWithId(token.id)
        val json = BoxyJson.encodeToString(ExportableTokenEntry.fromTokenEntry(stored))
        val restored =
            ImportedTokenValidator.validate(BoxyJson.decodeFromString<ExportableTokenEntry>(json).toTokenEntry())
        assertEquals(stored.otpInfo.serialize(), restored.otpInfo.serialize())
        assertEquals(stored.labels, restored.labels)
    }

    @Test
    fun `migration preserves legacy duplicates and prevents new conflicts`() {
        driver.execute(null, "DROP TRIGGER token_entry_unique_name_insert", 0)
        driver.execute(null, "DROP TRIGGER token_entry_unique_name_update", 0)
        dao.insertToken(testToken())
        driver.execute(
            null, """
            INSERT INTO token_entry SELECT 'legacy-duplicate', issuer, label, thumbnail, otpInfo,
            createdOn, updatedOn, addedFrom, isArchived, deletedOn FROM token_entry
        """.trimIndent(), 0
        )
        TokenDatabase.Schema.migrate(driver, 5, 6)
        assertEquals(2, dao.getAllTokens().size)
        dao.updateToken(dao.findTokenWithId("legacy-duplicate").copy(isArchived = true))
        assertFails { dao.insertToken(testToken(id = "third")) }
        dao.updateToken(dao.findTokenWithId("legacy-duplicate").copy(issuer = "Renamed"))
        assertEquals(2, dao.getAllTokens().size)
    }

    @Test
    fun `entries and counter changes survive reopening the database`() {
        val file = File.createTempFile("boxy-test-", ".db")
        try {
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { disk ->
                TokenDatabase.Schema.create(disk)
                val diskDao = LocalTokenDao(TokenDatabase(disk))
                diskDao.insertToken(testToken(labels = setOf("Work"), otpInfo = HotpInfo(byteArrayOf(1))))
                diskDao.updateHotpCounter("token-id", 1, 3_000)
            }
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { disk ->
                val token = LocalTokenDao(TokenDatabase(disk)).findTokenWithId("token-id")
                assertEquals(1L, (token.otpInfo as HotpInfo).counter)
                assertEquals(setOf("Work"), token.labels)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `original database migrates through every version without losing entries`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { legacy ->
            legacy.execute(
                null, """
                CREATE TABLE token_entry (
                    id TEXT NOT NULL PRIMARY KEY, issuer TEXT NOT NULL, label TEXT NOT NULL,
                    thumbnail TEXT NOT NULL, otpInfo TEXT NOT NULL, createdOn INTEGER NOT NULL,
                    updatedOn INTEGER NOT NULL, addedFrom TEXT NOT NULL
                )
            """.trimIndent(), 0
            )
            val token = testToken()
            legacy.execute(null, "INSERT INTO token_entry VALUES (?, ?, ?, ?, ?, ?, ?, ?)", 8) {
                bindString(0, token.id)
                bindString(1, token.issuer)
                bindString(2, token.label)
                bindString(3, token.thumbnail.serialize())
                bindString(4, token.otpInfo.serialize())
                bindLong(5, token.createdOn)
                bindLong(6, token.updatedOn)
                bindString(7, token.addedFrom.name)
            }
            TokenDatabase.Schema.migrate(legacy, 1, TokenDatabase.Schema.version)
            val migrated = LocalTokenDao(TokenDatabase(legacy))
            val stored = migrated.findTokenWithId(token.id)
            assertEquals(token.otpInfo.serialize(), stored.otpInfo.serialize())
            assertFalse(stored.isArchived)
            assertNull(stored.deletedOn)
            migrated.updateToken(stored.copy(labels = setOf("After upgrade")))
            assertEquals(setOf("After upgrade"), migrated.findTokenWithId(token.id).labels)
            assertFails { migrated.insertToken(testToken(id = "duplicate")) }
        }
    }

    @Test
    fun `restore and replacement preserve their new labels and secrets`() {
        val original = testToken(labels = setOf("Original"))
        dao.insertToken(original)
        dao.moveTokensToRecycleBin(setOf(original.id), 3_000)
        dao.restoreTokens(setOf(original.id), 4_000)
        val restored = dao.findTokenWithId(original.id)
        assertEquals(original.otpInfo.serialize(), restored.otpInfo.serialize())
        assertEquals(original.labels, restored.labels)
        assertTrue(restored.updatedOn > original.updatedOn)
        val replacement =
            testToken(id = "replacement", labels = setOf("Replacement"), otpInfo = HotpInfo(byteArrayOf(2)))
        dao.replaceTokenWith(original.id, replacement)
        assertEquals(listOf("replacement"), dao.getAllTokens().map { it.id })
        assertEquals(replacement.otpInfo.serialize(), dao.findTokenWithId(replacement.id).otpInfo.serialize())
        assertEquals(listOf("Replacement"), dao.getLabels().map { it.name })
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `home uses persisted revisions for consecutive counter and selection operations`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val token = testToken(otpInfo = HotpInfo(byteArrayOf(1)))
            dao.insertToken(token)
            val repository = LocalTokenRepository(dao, dispatcher)
            val home = HomeViewModel(
                SettingsDataStore(InMemoryPreferenceStore()), FetchTokensUseCase(repository),
                UpdateHotpCounterUseCase(repository), UpdateTokensUseCase(repository), DeleteTokensUseCase(repository),
            )
            home.loadTokens()
            var succeeded = false
            home.updateHotpCounter(token.id, 1) { succeeded = it }
            assertTrue(succeeded)
            home.selectToken(token.id)
            home.toggleLabelToApply("Work")
            home.applyLabelsToSelection()
            assertNull(home.uiState.value.selectionError)
            home.selectToken(token.id)
            home.archiveOrRestoreSelection()
            assertNull(home.uiState.value.selectionError)
            val stored = dao.findTokenWithId(token.id)
            assertTrue(stored.isArchived)
            assertEquals(setOf("Work"), stored.labels)
            assertEquals(1L, (stored.otpInfo as HotpInfo).counter)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `concurrent counter requests on separate connections cannot both succeed`() {
        val file = File.createTempFile("boxy-concurrency-", ".db")
        val workers = Executors.newFixedThreadPool(2)
        try {
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { initial ->
                TokenDatabase.Schema.create(initial)
                LocalTokenDao(TokenDatabase(initial)).insertToken(testToken(otpInfo = HotpInfo(byteArrayOf(1))))
            }
            val start = CountDownLatch(1)
            val results = (1..2).map {
                workers.submit<Boolean> {
                    JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { connection ->
                        connection.execute(null, "PRAGMA busy_timeout = 5000", 0)
                        check(start.await(10, TimeUnit.SECONDS))
                        runCatching {
                            LocalTokenDao(TokenDatabase(connection)).updateHotpCounter("token-id", 1, 3_000)
                        }.isSuccess
                    }
                }
            }
            start.countDown()
            assertEquals(1, results.count { it.get(15, TimeUnit.SECONDS) })
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { connection ->
                val token = LocalTokenDao(TokenDatabase(connection)).findTokenWithId("token-id")
                assertEquals(1L, (token.otpInfo as HotpInfo).counter)
            }
        } finally {
            workers.shutdownNow()
            workers.awaitTermination(10, TimeUnit.SECONDS)
            file.delete()
        }
    }

    @Test
    fun `account-name comparison matches SQLite for ASCII and non-ASCII`() {
        dao.insertToken(testToken(issuer = "ÉXAMPLE"))
        assertNotNull(dao.findTokenWithName("Éxample", "PERSON@EXAMPLE.COM"))
        assertNull(dao.findTokenWithName("éxample", "person@example.com"))
        assertEquals(accountNameKey("ÉXAMPLE", "ACCOUNT"), accountNameKey("Éxample", "account"))
        assertNotEquals(accountNameKey("Éxample", "account"), accountNameKey("éxample", "account"))
    }
}
