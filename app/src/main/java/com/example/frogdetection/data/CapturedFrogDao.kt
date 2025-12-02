package com.example.frogdetection.data

import androidx.room.*
import com.example.frogdetection.model.CapturedFrog
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedFrogDao {

    // ---------------------------------------------------
    // INSERT
    // ---------------------------------------------------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(frog: CapturedFrog): Long

    // ---------------------------------------------------
    // UPDATE — whole entity
    // ---------------------------------------------------
    @Update
    suspend fun update(frog: CapturedFrog)

    // ---------------------------------------------------
    // DELETE
    // ---------------------------------------------------
    @Delete
    suspend fun delete(frog: CapturedFrog)

    // ---------------------------------------------------
    // QUERY ALL
    // ---------------------------------------------------
    @Query("SELECT * FROM captured_frogs ORDER BY timestamp DESC")
    fun getAllFrogs(): Flow<List<CapturedFrog>>

    // ---------------------------------------------------
    // GET BY ID
    // ---------------------------------------------------
    @Query("SELECT * FROM captured_frogs WHERE id = :id LIMIT 1")
    suspend fun getFrogById(id: Int): CapturedFrog?

    // ---------------------------------------------------
    // UPDATE LOCATION
    // ---------------------------------------------------
    @Query("UPDATE captured_frogs SET locationName = :name WHERE id = :id")
    suspend fun updateLocation(id: Int, name: String)

    // ---------------------------------------------------
    // UPDATE upload status + remoteId after cloud sync
    // ---------------------------------------------------
    @Query("UPDATE captured_frogs SET uploaded = :uploaded, remoteId = :remoteId WHERE id = :id")
    suspend fun updateUploadStatus(id: Int, uploaded: Boolean, remoteId: Int?)

}
