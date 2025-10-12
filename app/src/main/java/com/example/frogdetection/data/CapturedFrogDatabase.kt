package com.example.frogdetection.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.frogdetection.dao.CapturedFrogDao
import com.example.frogdetection.dao.LocationCacheDao
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.model.Converters
import com.example.frogdetection.model.LocationCache

@Database(
    entities = [CapturedFrog::class, LocationCache::class],
    version = 4, // ✅ incremented from 3 → 4 because schema changed
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CapturedFrogDatabase : RoomDatabase() {

    abstract fun capturedFrogDao(): CapturedFrogDao
    abstract fun locationCacheDao(): LocationCacheDao  // ✅ new DAO for caching

    companion object {
        @Volatile
        private var INSTANCE: CapturedFrogDatabase? = null

        fun getDatabase(context: Context): CapturedFrogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CapturedFrogDatabase::class.java,
                    "captured_frog_db"
                )
                    .fallbackToDestructiveMigration() // ✅ Safe schema rebuild
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
