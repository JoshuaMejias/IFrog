package com.example.frogdetection.data

import com.example.frogdetection.model.CapturedFrog
import kotlinx.coroutines.flow.Flow

class CapturedFrogRepository(
    private val dao: CapturedFrogDao
) {

    fun getAllFrogs(): Flow<List<CapturedFrog>> =
        dao.getAllFrogs()

    suspend fun insert(frog: CapturedFrog): Long =
        dao.insert(frog)

    suspend fun update(frog: CapturedFrog) =
        dao.update(frog)

    suspend fun delete(frog: CapturedFrog) =
        dao.delete(frog)

    suspend fun getFrogById(id: Int): CapturedFrog? =
        dao.getFrogById(id)

    suspend fun updateLocation(id: Int, name: String) =
        dao.updateLocation(id, name)

    // FIXED: remoteId is Int?, not String?
    suspend fun updateUploadStatus(id: Int, uploaded: Boolean, remoteId: Int?) =
        dao.updateUploadStatus(id, uploaded, remoteId)
}
