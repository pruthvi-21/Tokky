package com.boxy.authenticator.core

/** Matches SQLite NOCASE: only ASCII letters are folded, on every account-name path. */
fun accountNameKey(issuer: String, label: String): Pair<String, String> =
    issuer.foldAsciiCase() to label.foldAsciiCase()

private fun String.foldAsciiCase(): String = buildString(length) {
    for (character in this@foldAsciiCase) {
        append(if (character in 'A'..'Z') character + 32 else character)
    }
}
