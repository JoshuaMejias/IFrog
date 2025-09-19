package com.example.frogdetection.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.frogdetection.R
import com.example.frogdetection.database.CapturedFrogDatabase
import com.example.frogdetection.data.CapturedFrogRepository
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.utils.getReadableLocation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class CapturedHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CapturedFrogRepository
    private val appContext = application.applicationContext
    private val apiKey = appContext.getString(R.string.google_maps_key) // ✅ get once

    init {
        val dao = CapturedFrogDatabase.getDatabase(application).capturedFrogDao()
        repository = CapturedFrogRepository(dao)
    }

    val capturedFrogs: StateFlow<List<CapturedFrog>> =
        repository.getAllFrogs()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun insert(frog: CapturedFrog, onInserted: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val updatedFrog = frog.copy(
                locationName = getReadableLocation(
                    context = appContext,
                    latitude = frog.latitude,
                    longitude = frog.longitude,
                    apiKey = apiKey // ✅ always include API key
                )
            )
            val newId = repository.insert(updatedFrog) // REPLACE on conflict
            onInserted(newId)
        }
    }

    suspend fun getFrogById(id: Int): CapturedFrog? = repository.getFrogById(id)

    fun deleteFrog(frog: CapturedFrog) {
        viewModelScope.launch {
            repository.delete(frog)
        }
    }

    fun migrateMissingLocations() {
        viewModelScope.launch {
            capturedFrogs.value.filter { it.locationName.isNullOrBlank() }.forEach { frog ->
                val updated = frog.copy(
                    locationName = runBlocking {
                        getReadableLocation(
                            context = appContext,
                            latitude = frog.latitude,
                            longitude = frog.longitude,
                            apiKey = apiKey // ✅ pass API key here too
                        )
                    }
                )
                repository.insert(updated) // ✅ REPLACE ensures update, not duplicate
            }
        }
    }
}
