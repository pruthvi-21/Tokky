package com.boxy.authenticator.utils

class StaleTokenException : IllegalStateException(
    "This account has changed or is no longer available. Reload it and try again."
)
