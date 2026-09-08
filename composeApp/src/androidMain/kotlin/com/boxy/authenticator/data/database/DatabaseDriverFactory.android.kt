package com.boxy.authenticator.data.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.boxy.authenticator.db.TokenDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver {
        System.loadLibrary("sqlcipher")
        val key = DatabaseKeyManager.databaseKey
        val factory = SupportOpenHelperFactory(key)

        return AndroidSqliteDriver(
            schema = TokenDatabase.Schema,
            context = context,
            name = "token_database",
            factory = factory
        )
    }
}
