package com.example.frogdetection.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.frogdetection.model.CapturedFrog

@Database(
    entities = [CapturedFrog::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(UriTypeConverter::class) // If you're storing image Uri
abstract class CapturedFrogDatabase : RoomDatabase() {
    abstract fun capturedFrogDao(): CapturedFrogDao

    companion object {
        @Volatile
        private var INSTANCE: CapturedFrogDatabase? = null

        fun getDatabase(context: Context): CapturedFrogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CapturedFrogDatabase::class.java,
                    "captured_frogs_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
