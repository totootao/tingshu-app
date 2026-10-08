package com.totootao.tingshu.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedTime DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE sourceId = :sourceId AND bookUrl = :bookUrl LIMIT 1")
    suspend fun get(sourceId: String, bookUrl: String): FavoriteEntity?

    @Query("SELECT * FROM favorites")
    suspend fun getAll(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteEntity)

    @Delete
    suspend fun delete(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE sourceId = :sourceId AND bookUrl = :bookUrl")
    suspend fun deleteByKey(sourceId: String, bookUrl: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface ExternalSourceDao {
    @Query("SELECT * FROM external_sources ORDER BY id DESC")
    fun observeAll(): Flow<List<ExternalSourceEntity>>

    @Query("SELECT * FROM external_sources")
    suspend fun getAll(): List<ExternalSourceEntity>

    @Query("SELECT * FROM external_sources WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): ExternalSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ExternalSourceEntity): Long

    @Update
    suspend fun update(entity: ExternalSourceEntity)

    @Delete
    suspend fun delete(entity: ExternalSourceEntity)
}
