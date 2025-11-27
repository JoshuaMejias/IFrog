package com.example.frogdetection.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.frogdetection.R
import com.example.frogdetection.data.CapturedFrogRepository
import com.example.frogdetection.data.CapturedFrogDatabase
import com.example.frogdetection.data.FrogCloudRepository
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.utils.getReadableLocationFromOpenCage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CapturedHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    // -----------------------------
    // LOCAL DATABASE REPOSITORY
    // -----------------------------
    private val repository: CapturedFrogRepository =
        CapturedFrogRepository(
            CapturedFrogDatabase.getDatabase(application).capturedFrogDao()
        )

    // -----------------------------
    // CLOUD REPOSITORY (Supabase REST + Storage)
    // -----------------------------
    private val cloudRepo = FrogCloudRepository(appContext)

    // -----------------------------
    // OPEN CAGE API KEY
    // -----------------------------
    private val openCageApiKey = appContext.getString(R.string.opencage_api_key)

    // -----------------------------
    // OBSERVABLE LIST OF FROGS
    // -----------------------------
    val capturedFrogs: StateFlow<List<CapturedFrog>> =
        repository.getAllFrogs()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ============================================================================
    // INSERT FROG (LOCAL + CLOUD)
    // ============================================================================
    fun insert(frog: CapturedFrog, onInserted: (Long) -> Unit = {}) {
        viewModelScope.launch {
            // 1️⃣ Try to get readable location
            val locationName = withContext(Dispatchers.IO) {
                getReadableLocationFromOpenCage(
                    context = appContext,
                    latitude = frog.latitude ?: 0.0,
                    longitude = frog.longitude ?: 0.0,
                    apiKey = openCageApiKey
                )
            }

            val updated = frog.copy(locationName = locationName)

            // 2️⃣ Insert locally
            val newId = repository.insert(updated)
            onInserted(newId)

            // 3️⃣ Upload to Supabase in background
            launch(Dispatchers.IO) {
                val ok = cloudRepo.uploadFrog(updated)
                if (!ok) {
                    // TODO: Add offline queue later
                }
            }
        }
    }

    // ============================================================================
    // DELETE FROG (LOCAL + CLOUD)
    // ============================================================================
    fun deleteFrog(frog: CapturedFrog) {
        viewModelScope.launch {
            // Local first
            repository.delete(frog)

            // Cloud second
            launch(Dispatchers.IO) {
                cloudRepo.deleteFromCloud(frog.id)
            }
        }
    }

    // ============================================================================
    // UPDATE LOCATION NAME (LOCAL DB ONLY)
    // ============================================================================
    fun updateLocation(id: Int, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateLocation(id, name)
        }
    }

    // ============================================================================
    // RESOLVE MISSING LOCATIONS (NO CACHE, DIRECT API CALL)
    // ============================================================================
    fun migrateMissingLocations() {
        viewModelScope.launch {
            capturedFrogs.value
                .filter {
                    it.locationName.isNullOrBlank()
                            && it.latitude != null && it.longitude != null
                }
                .forEach { frog ->

                    val readable = withContext(Dispatchers.IO) {
                        getReadableLocationFromOpenCage(
                            context = appContext,
                            latitude = frog.latitude ?: 0.0,
                            longitude = frog.longitude ?: 0.0,
                            apiKey = openCageApiKey
                        )
                    }

                    if (readable.isNotBlank()) {
                        repository.updateLocation(frog.id, readable)
                    }
                }
        }
    }

    // ============================================================================
    // GET SINGLE FROG BY ID
    // ============================================================================
    suspend fun getFrogById(id: Int): CapturedFrog? =
        repository.getFrogById(id)
}
