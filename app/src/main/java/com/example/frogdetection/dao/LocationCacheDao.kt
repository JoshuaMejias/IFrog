package com.example.frogdetection.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.frogdetection.model.LocationCache

@Dao
interface LocationCacheDao {

    @Query("SELECT locationName FROM location_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getCachedLocation(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: LocationCache)
}
