package com.boxy.authenticator

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController(
    configure = {
        NativeAppBootstrap.start()
    }
) {
    App()
}
