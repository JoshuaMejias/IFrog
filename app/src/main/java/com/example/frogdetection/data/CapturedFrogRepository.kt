package com.example.frogdetection.data

import com.example.frogdetection.model.CapturedFrog
import kotlinx.coroutines.flow.Flow

class CapturedFrogRepository(private val dao: CapturedFrogDao) {

    fun getAllFrogs(): Flow<List<CapturedFrog>> = dao.getAll()

    suspend fun getFrogById(id: Int): CapturedFrog? = dao.getById(id)

    suspend fun insert(frog: CapturedFrog): Long = dao.insert(frog)

    suspend fun delete(frog: CapturedFrog) = dao.delete(frog)

    suspend fun updateLocation(id: Int, name: String) = dao.updateLocation(id, name)
}
