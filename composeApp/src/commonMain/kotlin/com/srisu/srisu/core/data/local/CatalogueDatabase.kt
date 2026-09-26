package com.srisu.srisu.core.data.local

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Transaction

/** Public catalogue snapshots only. Never credentials, profiles or private chat. */
@Entity(tableName = "catalogue_snapshot")
data class CatalogueSnapshot(
    @PrimaryKey val scope: String,
    val payload: String,
    val fetchedAtMillis: Long,
)

@Dao
abstract class CatalogueDao {
    @Query("SELECT * FROM catalogue_snapshot WHERE scope = :scope")
    abstract suspend fun read(scope: String): CatalogueSnapshot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(snapshot: CatalogueSnapshot)

    @Query("DELETE FROM catalogue_snapshot WHERE scope = :scope")
    abstract suspend fun delete(scope: String)

    @Transaction
    open suspend fun replace(snapshot: CatalogueSnapshot) {
        delete(snapshot.scope)
        insert(snapshot)
    }
}

@Database(entities = [CatalogueSnapshot::class], version = 1, exportSchema = true)
@ConstructedBy(CatalogueDatabaseConstructor::class)
abstract class CatalogueDatabase : RoomDatabase() {
    abstract fun catalogue(): CatalogueDao
}

@Suppress("KotlinNoActualForExpect")
expect object CatalogueDatabaseConstructor : RoomDatabaseConstructor<CatalogueDatabase> {
    override fun initialize(): CatalogueDatabase
}
