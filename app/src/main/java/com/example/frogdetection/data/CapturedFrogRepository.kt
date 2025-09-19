package com.example.frogdetection.data

import com.example.frogdetection.dao.CapturedFrogDao
import com.example.frogdetection.model.CapturedFrog
import kotlinx.coroutines.flow.Flow

class CapturedFrogRepository(private val dao: CapturedFrogDao) {

    // ✅ Insert or update automatically
    suspend fun insert(frog: CapturedFrog): Long = dao.insert(frog)

    fun getAllFrogs(): Flow<List<CapturedFrog>> = dao.getAllFrogs()

    suspend fun getFrogById(id: Int): CapturedFrog? = dao.getFrogById(id)

    suspend fun delete(frog: CapturedFrog) = dao.delete(frog)
}
