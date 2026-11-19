package com.srisu.srisu.core

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.srisu.srisu.core.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CatalogueDatabaseTest {
    private fun open(path: String) = Room.databaseBuilder<CatalogueDatabase>(RuntimeEnvironment.getApplication(), path)
        .setDriver(AndroidSQLiteDriver()).build()

    @Test fun reopensVersionOneWithoutLosingRowsAndReplacementIsAtomic() = runBlocking {
        val path = RuntimeEnvironment.getApplication().getDatabasePath("catalogue-test.db").absolutePath
        var db = open(path)
        val original = CatalogueSnapshot("public-a", "synthetic original", 100)
        db.catalogue().replace(original)
        db.catalogue().replace(CatalogueSnapshot("public-b", "other environment", 100))
        db.close()
        db = open(path)
        assertEquals(original, db.catalogue().read("public-a"))
        db.useWriterConnection { connection ->
            connection.usePrepared("CREATE TRIGGER reject_catalogue BEFORE INSERT ON catalogue_snapshot WHEN NEW.payload = 'reject' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END") { it.step() }
        }
        assertFails { db.catalogue().replace(CatalogueSnapshot("public-a", "reject", 200)) }
        assertEquals(original, db.catalogue().read("public-a")) // delete in replace rolled back
        assertNotNull(db.catalogue().read("public-b"))
        db.close()
    }
    @Test fun missingMigrationFailsWithoutDestructiveFallback() = runBlocking {
        val path = RuntimeEnvironment.getApplication().getDatabasePath("future-catalogue-test.db").absolutePath
        var db = open(path)
        db.catalogue().replace(CatalogueSnapshot("public-a", "preserved", 100))
        db.useWriterConnection { connection -> connection.usePrepared("PRAGMA user_version = 2") { it.step() } }
        db.close()
        db = open(path)
        assertFails { db.catalogue().read("public-a") }
        // AndroidSQLiteDriver's lazy close retries initialization after a failed open.
        try { db.close() } catch (failure: IllegalStateException) { assertTrue(failure.message.orEmpty().contains("migration")) }
        AndroidSQLiteDriver().open(path).use { connection ->
            connection.prepare("SELECT payload FROM catalogue_snapshot WHERE scope = 'public-a'").use {
                assertTrue(it.step()); assertEquals("preserved", it.getText(0))
            }
        }
    }

}
