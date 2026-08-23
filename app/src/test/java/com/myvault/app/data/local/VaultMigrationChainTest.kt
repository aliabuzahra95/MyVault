package com.myvault.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultMigrationChainTest {
    @Test
    fun registeredMigrations_coverEveryDatabaseVersionWithoutGaps() {
        val migrations = VaultDatabase.ALL_MIGRATIONS.sortedBy { it.startVersion }

        assertEquals(1, migrations.first().startVersion)
        migrations.zipWithNext().forEach { (current, next) ->
            assertEquals(current.endVersion, next.startVersion)
        }
        assertEquals(27, migrations.last().endVersion)
    }

    @Test
    fun migration26To27DropsOnlyRetiredAskAiStorageTables() {
        val executedSql = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL" && !args.isNullOrEmpty()) {
                executedSql += args.first() as String
            }
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        } as SupportSQLiteDatabase

        VaultDatabase.MIGRATION_26_27.migrate(database)

        assertEquals(
            listOf(
                "DROP TABLE IF EXISTS ai_messages",
                "DROP TABLE IF EXISTS ai_conversations",
                "DROP TABLE IF EXISTS home_chat_history",
                "DROP TABLE IF EXISTS library_ai_file_cache",
                "DROP TABLE IF EXISTS library_pdf_text_cache",
            ),
            executedSql,
        )
    }
}
