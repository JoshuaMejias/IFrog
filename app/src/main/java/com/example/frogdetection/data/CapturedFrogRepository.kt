package com.example.frogdetection.data

import com.example.frogdetection.dao.CapturedFrogDao
import com.example.frogdetection.model.CapturedFrog
import kotlinx.coroutines.flow.Flow

class CapturedFrogRepository(private val dao: CapturedFrogDao) {

    suspend fun insert(frog: CapturedFrog): Long = dao.insert(frog)

    fun getAllFrogs() = dao.getAllFrogs()

    suspend fun getFrogById(id: Int) = dao.getFrogById(id)

    suspend fun delete(frog: CapturedFrog) = dao.delete(frog)

    // NEW
    suspend fun updateLocation(id: Int, name: String) = dao.updateLocation(id, name)
}

