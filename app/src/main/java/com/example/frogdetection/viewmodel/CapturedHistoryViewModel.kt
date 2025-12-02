// File: app/src/main/java/com/example/frogdetection/viewmodel/CapturedHistoryViewModel.kt
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

    private val repository: CapturedFrogRepository =
        CapturedFrogRepository(
            CapturedFrogDatabase.getDatabase(application).capturedFrogDao()
        )

    private val cloudRepo = FrogCloudRepository(appContext)

    private val openCageApiKey = appContext.getString(R.string.opencage_api_key)

    val capturedFrogs: StateFlow<List<CapturedFrog>> =
        repository.getAllFrogs()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Insert local + resolve location (same as before)
    fun insert(frog: CapturedFrog, onInserted: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val locationName = withContext(Dispatchers.IO) {
                getReadableLocationFromOpenCage(
                    context = appContext,
                    latitude = frog.latitude ?: 0.0,
                    longitude = frog.longitude ?: 0.0,
                    apiKey = openCageApiKey
                )
            }

            val updatedFrog = frog.copy(locationName = locationName)

            val newId = repository.insert(updatedFrog)
            onInserted(newId)
        }
    }

    // Delete local + cloud
    fun deleteFrog(frog: CapturedFrog) {
        viewModelScope.launch {
            // Delete local first
            repository.delete(frog)

            // Delete from cloud only if uploaded
            if (frog.uploaded) {
                launch(Dispatchers.IO) {
                    cloudRepo.deleteFrogFromCloud(frog.id.toLong())
                }
            }
        }
    }



    // Upload a single frog to cloud (called when user presses "Upload to Map")
    suspend fun uploadFrogToCloud(frog: CapturedFrog): Boolean {
        val supabaseId = cloudRepo.uploadFrogAndReturnId(frog)

        if (supabaseId != null) {
            repository.updateUploadStatus(
                id = frog.id,
                uploaded = true,
                remoteId = supabaseId
            )
            return true
        }

        return false
    }



    // Update location name locally
    fun updateLocation(id: Int, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateLocation(id, name)
        }
    }

    // Fix missing locations
    fun migrateMissingLocations() {
        viewModelScope.launch {
            capturedFrogs.value
                .filter {
                    it.locationName.isNullOrBlank() &&
                            it.latitude != null &&
                            it.longitude != null
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

    suspend fun getFrogById(id: Int): CapturedFrog? =
        repository.getFrogById(id)
}
