package com.example.frogdetection.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.frogdetection.model.CapturedFrog

@Database(entities = [CapturedFrog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun capturedFrogDao(): CapturedFrogDao
}
