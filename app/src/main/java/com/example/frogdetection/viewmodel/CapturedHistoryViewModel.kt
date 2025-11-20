package com.example.frogdetection.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.frogdetection.R
import com.example.frogdetection.data.CapturedFrogRepository
import com.example.frogdetection.data.CapturedFrogDatabase
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.utils.getReadableLocationFromOpenCage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CapturedHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CapturedFrogRepository
    private val appContext = application.applicationContext

    // Load OpenCage API key from resources
    private val openCageApiKey = appContext.getString(R.string.opencage_api_key)

    init {
        val dao = CapturedFrogDatabase.getDatabase(application).capturedFrogDao()
        repository = CapturedFrogRepository(dao)
    }

    val capturedFrogs: StateFlow<List<CapturedFrog>> =
        repository.getAllFrogs()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Insert a new frog entry (used when saving after detection)
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

    suspend fun getFrogById(id: Int): CapturedFrog? = repository.getFrogById(id)

    fun deleteFrog(frog: CapturedFrog) {
        viewModelScope.launch {
            repository.delete(frog)
        }
    }

    fun updateLocation(id: Int, name: String) {
        viewModelScope.launch {
            repository.updateLocation(id, name)
        }
    }


    // Fix missing locations without inserting duplicates
    fun migrateMissingLocations() {
        viewModelScope.launch {
            capturedFrogs.value
                .filter { it.locationName.isNullOrBlank() && it.latitude != null && it.longitude != null }
                .forEach { frog ->

                    val readable = withContext(Dispatchers.IO) {
                        getReadableLocationFromOpenCage(
                            context = appContext,
                            latitude = frog.latitude ?: 0.0,
                            longitude = frog.longitude ?: 0.0,
                            apiKey = openCageApiKey
                        )
                    }

                    if (readable.isNotBlank()) { repository.updateLocation(frog.id, readable) }

                }
        }
    }
}
