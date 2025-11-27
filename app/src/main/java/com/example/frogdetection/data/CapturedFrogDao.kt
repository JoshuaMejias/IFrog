package com.example.frogdetection.data

import androidx.room.*
import com.example.frogdetection.model.CapturedFrog
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedFrogDao {

    @Query("SELECT * FROM captured_frogs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<CapturedFrog>>

    @Query("SELECT * FROM captured_frogs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): CapturedFrog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(frog: CapturedFrog): Long

    @Delete
    suspend fun delete(frog: CapturedFrog)

    @Query("UPDATE captured_frogs SET locationName = :name WHERE id = :id")
    suspend fun updateLocation(id: Int, name: String)
}
