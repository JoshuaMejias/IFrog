package com.example.frogdetection.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.frogdetection.dao.CapturedFrogDao
import com.example.frogdetection.dao.LocationCacheDao
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.model.LocationCache

@Database(
    entities = [CapturedFrog::class, LocationCache::class],
    version = 5, // bumped from 4 -> 5
    exportSchema = false
)
@TypeConverters(UriTypeConverter::class)
abstract class CapturedFrogDatabase : RoomDatabase() {

    abstract fun capturedFrogDao(): CapturedFrogDao
    abstract fun locationCacheDao(): LocationCacheDao

    companion object {
        @Volatile
        private var INSTANCE: CapturedFrogDatabase? = null

        // Migration: add 'confidence' column (REAL) to captured_frogs
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new NULLABLE column 'confidence' (REAL)
                database.execSQL("ALTER TABLE captured_frogs ADD COLUMN confidence REAL")
            }
        }

        fun getDatabase(context: Context): CapturedFrogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CapturedFrogDatabase::class.java,
                    "captured_frog_db"
                )
                    .addMigrations(MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
