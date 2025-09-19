package com.example.frogdetection.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.frogdetection.model.CapturedFrog
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedFrogDao {

    // ✅ Acts as insert or update depending on conflict
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(frog: CapturedFrog): Long

    @Query("SELECT * FROM captured_frogs ORDER BY timestamp DESC")
    fun getAllFrogs(): Flow<List<CapturedFrog>>

    @Query("SELECT * FROM captured_frogs WHERE id = :id LIMIT 1")
    suspend fun getFrogById(id: Int): CapturedFrog?

    @Query("DELETE FROM captured_frogs")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(frog: CapturedFrog)
}
