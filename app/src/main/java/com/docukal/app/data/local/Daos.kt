package com.docukal.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name")
    fun all(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories")
    suspend fun listOnce(): List<CategoryEntity>

    @Insert
    suspend fun insert(x: CategoryEntity)

    @Update
    suspend fun update(x: CategoryEntity)

    @Delete
    suspend fun delete(x: CategoryEntity)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE archived = 0 ORDER BY updatedAt DESC")
    fun all(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun byId(id: Long): Flow<DocumentEntity?>

    @Insert
    suspend fun insert(x: DocumentEntity): Long

    @Update
    suspend fun update(x: DocumentEntity)

    @Delete
    suspend fun delete(x: DocumentEntity)

    @Query("DELETE FROM documents")
    suspend fun clear()
}

@Dao
interface WarrantyDao {
    @Query("SELECT * FROM warranties ORDER BY createdAt DESC")
    fun all(): Flow<List<WarrantyEntity>>

    @Insert
    suspend fun insert(x: WarrantyEntity): Long

    @Update
    suspend fun update(x: WarrantyEntity)

    @Delete
    suspend fun delete(x: WarrantyEntity)

    @Query("DELETE FROM warranties")
    suspend fun clear()
}

@Dao
interface ImportantDateDao {
    @Query("SELECT * FROM important_dates ORDER BY date")
    fun all(): Flow<List<ImportantDateEntity>>

    @Insert
    suspend fun insert(x: ImportantDateEntity): Long

    @Update
    suspend fun update(x: ImportantDateEntity)

    @Delete
    suspend fun delete(x: ImportantDateEntity)

    @Query("DELETE FROM important_dates")
    suspend fun clear()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun get(): Flow<SettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(x: SettingsEntity)
}
