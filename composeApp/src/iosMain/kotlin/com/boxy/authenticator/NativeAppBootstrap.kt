package com.boxy.authenticator

import com.boxy.authenticator.di.platformModule
import com.boxy.authenticator.di.sharedModule
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

object NativeAppBootstrap {
    fun start() {
        if (GlobalContext.getOrNull() != null) return

        startKoin {
            modules(sharedModule, platformModule)
        }
    }
}

data class NativeMigrationStatus(
    val sharedLogicReady: Boolean = true,
    val nativeShellReady: Boolean = true,
    val phase: String = "Phase 4",
)

fun nativeMigrationStatus(): NativeMigrationStatus = NativeMigrationStatus()
