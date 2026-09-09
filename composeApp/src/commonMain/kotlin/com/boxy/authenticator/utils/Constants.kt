package com.boxy.authenticator.utils

object Constants {
    val THUMBNAIL_COlORS = listOf(
        "#A0522D",
        "#376B97",
        "#556B2F",
        "#B18F96",
        "#C8AA4B",
    )

    const val THUMBNAIL_ICON_PATH = "drawable"
    const val EXPORT_FILE_NAME_PREFIX = "authenticator_backup_"
    const val EXPORT_PLAIN_FILE_EXTENSION = "txt"
    const val EXPORT_ENCRYPTED_FILE_EXTENSION = "encrypted"
    const val MAX_IMPORT_FILE_BYTES = 10L * 1024 * 1024
    const val MAX_IMPORT_TOKENS = 10_000
}
