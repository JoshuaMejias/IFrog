package com.example.frogdetection.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.frogdetection.dao.LocationCacheDao
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.model.Converters
import com.example.frogdetection.model.LocationCache

@Database(
    entities = [CapturedFrog::class, LocationCache::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CapturedFrogDatabase : RoomDatabase() {

    abstract fun capturedFrogDao(): CapturedFrogDao
    abstract fun locationCacheDao(): LocationCacheDao

    companion object {

        @Volatile
        private var INSTANCE: CapturedFrogDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL("ALTER TABLE captured_frogs ADD COLUMN detectionsJson TEXT")
                db.execSQL("ALTER TABLE captured_frogs ADD COLUMN score REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE captured_frogs ADD COLUMN uploaded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE captured_frogs ADD COLUMN remoteId INTEGER")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS location_cache (
                        cacheKey TEXT NOT NULL PRIMARY KEY,
                        locationName TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): CapturedFrogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CapturedFrogDatabase::class.java,
                    "captured_frog_db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
