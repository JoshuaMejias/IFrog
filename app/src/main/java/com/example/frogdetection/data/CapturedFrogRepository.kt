package com.example.frogdetection.data

import com.example.frogdetection.model.CapturedFrog
import kotlinx.coroutines.flow.Flow

class CapturedFrogRepository(private val dao: CapturedFrogDao) {
    suspend fun insert(frog: CapturedFrog) = dao.insert(frog)
    fun getAllFrogs(): Flow<List<CapturedFrog>> = dao.getAllFrogs()
}
