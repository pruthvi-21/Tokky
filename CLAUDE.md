# CLAUDE.md

This file provides guidance to Claude Code when working in this repository.

## Project overview

Boxy Authenticator is a Kotlin Multiplatform authenticator app for Android and iOS. The UI is shared with Compose Multiplatform. It stores TOTP, HOTP, and Steam token data locally, supports QR-code import, encrypted import/export, app locking, and biometric authentication.

Treat this as a security-sensitive application. Never log, expose, hard-code, or commit OTP secrets, database keys, passwords, recovery data, exported token payloads, or real `otpauth://` URIs. Use obviously fake test vectors in tests and examples.

## Repository layout

- `composeApp/src/commonMain/` — shared application code and Compose UI.
- `composeApp/src/androidMain/` — Android entry points and platform implementations.
- `composeApp/src/iosMain/` — iOS platform implementations and the shared `MainViewController`.
- `composeApp/src/commonTest/` — shared unit tests.
- `composeApp/src/commonMain/sqldelight/` — SQLDelight schema, queries, and migrations.
- `iosApp/` — SwiftUI host application and Xcode project.
- `preferences/` — shared Compose preference components used by the app.
- `gradle/libs.versions.toml` — dependency and plugin versions.

The shared app follows a layered structure:

- `core/` contains OTP, crypto, parsing, serialization, settings, and platform abstractions.
- `data/` contains SQLDelight and preference implementations.
- `domain/` contains models, repository interfaces, and use cases.
- `ui/` contains screens, reusable components, state, themes, and view models.
- `navigation/` contains typed routes and the root navigation graph.
- `di/` contains Koin modules.

## Build and test commands

Do not run linting, builds, or tests unless the user explicitly asks for them. Static inspection and `git diff --check` are acceptable when reviewing changes, but do not invoke Gradle verification or compilation tasks by default.

Run commands from the repository root with the checked-in Gradle wrapper:

```sh
# Shared tests
./gradlew :composeApp:allTests

# Android unit tests
./gradlew :composeApp:testDebugUnitTest

# Build the Android debug APK
./gradlew :composeApp:assembleDebug

# Compile shared code for the iOS simulator
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

For focused iteration, run the narrowest relevant test task first, then `:composeApp:allTests` before finishing. iOS application builds require Xcode; open `iosApp/iosApp.xcodeproj` or build its scheme with Xcode tooling.

The project expects JDK 17 to run Gradle and compiles Android source to JVM 11 bytecode. Android configuration is in `composeApp/build.gradle.kts`; do not duplicate dependency versions outside the version catalog.

## Implementation conventions

- Put platform-independent behavior in `commonMain`. Add `expect`/`actual` declarations only when a platform API or implementation genuinely differs.
- When adding an `expect` declaration, provide matching Android and iOS `actual` implementations in the same change.
- Keep Composables focused on rendering and user interaction. State and asynchronous work belong in view models; business rules and persistence access belong in use cases and repositories.
- Expose view-model state through the existing state models and flows, and collect it in Compose with lifecycle-aware APIs where the surrounding code does so.
- Register new view models, use cases, repositories, and platform dependencies in the appropriate Koin module.
- Use typed destinations from `navigation/Screen.kt` and wire new destinations in `navigation/RootNavigation.kt`.
- Use Compose resources from `composeApp/src/commonMain/composeResources`; keep platform resources in the corresponding platform source set.
- Follow official Kotlin style and the formatting already present in nearby files. Prefer immutable values and explicit UI state over mutable state spread across Composables.
- Keep changes scoped. Do not edit generated output under `build/` or release artifacts under `composeApp/release/`.

## Database changes

The SQLDelight database is named `TokenDatabase` and its package is `com.boxy.authenticator.db`.

- Edit schema and queries under `composeApp/src/commonMain/sqldelight/`.
- Preserve existing user data. Add a numbered `.sqm` migration for schema changes instead of rewriting history.
- Update DAO mapping, serializers, repository behavior, and tests together when stored fields change.
- Remember that Android uses an encrypted SQLCipher-backed database while iOS uses the native SQLDelight driver; validate platform-specific effects on both targets.

## Security and OTP correctness

- Preserve byte arrays as binary data and avoid unnecessary conversion of secrets to `String`.
- Do not weaken encryption, key derivation, biometric checks, app-lock behavior, or screenshot blocking for convenience.
- Keep cryptographic randomness and key storage on the existing secure/platform-specific paths.
- Treat OTP algorithm, digit count, period, and HOTP counter behavior as compatibility-sensitive. Add tests for boundary cases and known public test vectors when changing parsing or generation.
- HOTP counters are persistent state: ensure successful code use and counter updates cannot silently diverge.
- Import/export and URI parsing consume untrusted input. Validate malformed, missing, oversized, and unsupported values without leaking sensitive content into errors or logs.

## Testing expectations

- Add or update tests in `composeApp/src/commonTest` for shared business logic, parsing, crypto, OTP generation, repositories, and view models.
- Prefer deterministic tests: inject or control time, randomness, dispatchers, and fake repositories as needed.
- Cover success, failure, and duplicate/conflict paths for token mutations and import flows.
- For UI changes, verify both Android and iOS behavior, including back navigation, safe areas/insets, permissions, biometrics, and accessibility-relevant labels.

Before finishing, review the diff for accidental secrets or generated files. If the user requested linting, builds, or tests, report which commands were run, including any command that could not run because its platform toolchain was unavailable.
