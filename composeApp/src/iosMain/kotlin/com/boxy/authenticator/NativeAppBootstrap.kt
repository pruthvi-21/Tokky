package com.boxy.authenticator

import com.boxy.authenticator.di.platformModule
import com.boxy.authenticator.di.sharedModule
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

object NativeAppBootstrap {
    fun start() {
        if (KoinPlatform.getKoinOrNull() != null) return

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
