# Database readiness: Android and shared code

Updated 2026-09-11. iOS is deferred and was not changed or tested.

## Resolved findings

- **Stale writes and HOTP rollback:** the DAO checks the caller's `updatedOn` revision inside the write transaction. Missing, recycled, or changed entries fail before labels or account data are touched. One conflict rolls back an entire batch. Each successful mutation advances the revision, including label changes, recycling, and restoring; repeated or backwards clock values cannot reuse the previous revision.
- **HOTP increments:** the persisted counter must advance exactly one step. Repeated requests, backwards values, and overflow fail. The home screen reloads persisted entries after changes so subsequent edits carry the new revision; the counter UI follows refreshed state and disables increment at `Long.MAX_VALUE`.
- **Duplicate names:** inserts, imports, and name-changing edits check for conflicts inside the transaction. Database triggers also reject conflicting direct writes and restores. Import preview and SQLite use the same ASCII case-folding rule.
- **Recycled labels:** stale edits fail before replacing labels. Failed replacements preserve the original entry and labels.
- **Validation and backup compatibility:** all DAO insert, batch, replacement, and edit paths use the same entry validation as backup import. Manual form validation rejects blank issuers, oversized secrets, and periods outside 1–86,400 seconds. Invalid form construction now produces a visible save error.

## Migration policy

Migration `5.sqm` upgrades schema version 5 to 6 by adding duplicate-name triggers. It does not delete, rename, recycle, or rewrite existing entries. Existing duplicates remain readable and may have metadata edited or be renamed to an available name. New conflicts are rejected. This preserves users' secrets and avoids an upgrade failure that a unique index over existing duplicates would cause.

`updatedOn` now also serves as the optimistic edit revision. Callers must keep the value they read when submitting an edit, and reload after a successful mutation. The DAO assigns the next timestamp only after verifying that revision.

## Verification

The Android unit suite includes real SQLite/SQLDelight DAO tests for:

- Duplicate edit/import rejection and transaction rollback, including direct SQL writes.
- Stale edits, counter rollback prevention, overflow, and concurrent counter requests on separate database connections.
- Consecutive home-screen counter, label, and archive operations through the real repository.
- Recycling, restoring, permanent deletion, and atomic replacement with labels.
- Shared validation and backup serialization round-trips.
- Migration from the original schema through the current schema, preserving legacy entries and duplicates.
- Persistence of entries, labels, and counter changes after closing and reopening a file-backed database.

Run `./gradlew :composeApp:testDebugUnitTest :composeApp:verifyCommonMainTokenDatabaseMigration`.

These tests use the JVM SQLite driver. Android SQLCipher/Keystore behavior, upgrades of actual shipped encrypted databases, disk-full handling, and process interruption still need device verification before release certification. No APK packaging or iOS validation was performed.
