package com.myvault.app.data.local

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
        assertEquals(21, migrations.last().endVersion)
    }
}
