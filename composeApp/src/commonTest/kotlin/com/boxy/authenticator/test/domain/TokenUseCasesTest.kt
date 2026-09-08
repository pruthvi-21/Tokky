package com.boxy.authenticator.test.domain

import com.boxy.authenticator.domain.usecases.DeleteTokenUseCase
import com.boxy.authenticator.domain.usecases.FetchTokenByIdUseCase
import com.boxy.authenticator.domain.usecases.FetchTokenByNameUseCase
import com.boxy.authenticator.domain.usecases.FetchTokensUseCase
import com.boxy.authenticator.domain.usecases.InsertTokenUseCase
import com.boxy.authenticator.domain.usecases.InsertTokensUseCase
import com.boxy.authenticator.domain.usecases.ReplaceExistingTokenUseCase
import com.boxy.authenticator.domain.usecases.UpdateHotpCounterUseCase
import com.boxy.authenticator.domain.usecases.UpdateTokenUseCase
import com.boxy.authenticator.test.RecordingTokenRepository
import com.boxy.authenticator.test.testToken
import com.boxy.authenticator.utils.TokenNameExistsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TokenUseCasesTest {
    @Test
    fun `fetch use cases return repository data`() = runTest {
        val token = testToken()
        val repository = RecordingTokenRepository(listOf(token))

        assertEquals(listOf(token), FetchTokensUseCase(repository)().getOrThrow())
        assertSame(token, FetchTokenByIdUseCase(repository)(token.id).getOrThrow())
        assertSame(
            token,
            FetchTokenByNameUseCase(repository)("example", "PERSON@EXAMPLE.COM").getOrThrow(),
        )
    }

    @Test
    fun `use cases wrap ordinary repository failures`() = runTest {
        val failure = IllegalStateException("database unavailable")
        val repository = RecordingTokenRepository().apply { error = failure }

        assertSame(failure, FetchTokensUseCase(repository)().exceptionOrNull())
        assertSame(failure, FetchTokenByIdUseCase(repository)("id").exceptionOrNull())
        assertSame(failure, DeleteTokenUseCase(repository)("id").exceptionOrNull())
    }

    @Test
    fun `cancellation is never converted to a failed Result`() = runTest {
        val repository = RecordingTokenRepository().apply {
            error = CancellationException("cancelled")
        }

        assertFailsWith<CancellationException> { FetchTokensUseCase(repository)() }
    }

    @Test
    fun `insert rejects another token with the same case-insensitive name`() = runTest {
        val existing = testToken(id = "existing")
        val incoming = testToken(id = "incoming", issuer = "example", label = "PERSON@example.com")
        val repository = RecordingTokenRepository(listOf(existing))

        val failure = InsertTokenUseCase(repository)(incoming).exceptionOrNull()

        val duplicate = assertIs<TokenNameExistsException>(failure)
        assertSame(existing, duplicate.token)
        assertEquals(listOf(existing), repository.tokens)
    }

    @Test
    fun `insert permits saving the same token identity`() = runTest {
        val token = testToken()
        val repository = RecordingTokenRepository(listOf(token))

        assertTrue(InsertTokenUseCase(repository)(token).isSuccess)
        assertEquals(2, repository.tokens.size)
    }

    @Test
    fun `bulk insert forwards every token`() = runTest {
        val repository = RecordingTokenRepository()
        val tokens = listOf(testToken(id = "one"), testToken(id = "two"))

        assertTrue(InsertTokensUseCase(repository)(tokens).isSuccess)
        assertEquals(tokens, repository.tokens)
    }

    @Test
    fun `delete update replace and HOTP operations preserve arguments`() = runTest {
        val original = testToken(id = "original")
        val updated = original.copy(label = "updated")
        val replacement = testToken(id = "replacement")
        val repository = RecordingTokenRepository(listOf(original))

        assertTrue(UpdateTokenUseCase(repository)(updated).isSuccess)
        assertEquals(updated, repository.tokens.single())

        assertTrue(UpdateHotpCounterUseCase(repository)(updated.id, 9L).isSuccess)
        assertEquals(updated.id to 9L, repository.lastHotpUpdate)

        assertTrue(ReplaceExistingTokenUseCase(repository)(updated, replacement).isSuccess)
        assertEquals(updated.id to replacement, repository.lastReplacement)
        assertEquals(listOf(replacement), repository.tokens)

        assertTrue(DeleteTokenUseCase(repository)(replacement.id).isSuccess)
        assertEquals(replacement.id, repository.lastDeletedId)
        assertTrue(repository.tokens.isEmpty())
    }
}
