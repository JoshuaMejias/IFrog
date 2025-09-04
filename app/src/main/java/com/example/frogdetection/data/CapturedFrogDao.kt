package com.example.frogdetection.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.frogdetection.model.CapturedFrog
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedFrogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(frog: CapturedFrog)

    @Query("SELECT * FROM captured_frogs ORDER BY timestamp DESC")
    fun getAllFrogs(): Flow<List<CapturedFrog>>
}
