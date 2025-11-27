package com.example.frogdetection.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.model.Converters

@Database(
    entities = [CapturedFrog::class],
    version = 1,   // reset to clean version
    exportSchema = false
)
@TypeConverters(Converters::class)
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
                    "captured_frog_db"
                )
                    .fallbackToDestructiveMigration() // safe, clean DB
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
