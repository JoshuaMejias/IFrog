package com.example.frogdetection.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.frogdetection.data.CapturedFrogDatabase
import com.example.frogdetection.data.CapturedFrogRepository
import com.example.frogdetection.model.CapturedFrog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CapturedHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CapturedFrogRepository

    init {
        val dao = CapturedFrogDatabase.getDatabase(application).capturedFrogDao()
        repository = CapturedFrogRepository(dao)
    }

    // Expose frogs as StateFlow (auto-updates in UI)
    val capturedFrogs: StateFlow<List<CapturedFrog>> =
        repository.getAllFrogs()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Insert new frog into database
    fun insert(frog: CapturedFrog) {
        viewModelScope.launch {
            repository.insert(frog)
        }
    }
}
