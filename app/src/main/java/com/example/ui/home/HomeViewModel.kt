package com.example.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ConversionRepository
import com.example.data.model.ConversionRecord
import com.example.data.model.ConversionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RecentFilter {
    ALL,
    PDF_TO_IMAGE,
    IMAGE_TO_PDF
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ConversionRepository

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ConversionRepository(db.conversionDao())
    }

    private val _selectedFilter = MutableStateFlow(RecentFilter.ALL)
    val selectedFilter: StateFlow<RecentFilter> = _selectedFilter

    val recentRecords: StateFlow<List<ConversionRecord>> = combine(
        repository.allRecords,
        _selectedFilter
    ) { records, filter ->
        when (filter) {
            RecentFilter.ALL -> records
            RecentFilter.PDF_TO_IMAGE -> records.filter { it.type == ConversionType.PDF_TO_IMAGE }
            RecentFilter.IMAGE_TO_PDF -> records.filter { it.type == ConversionType.IMAGE_TO_PDF }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setFilter(filter: RecentFilter) {
        _selectedFilter.value = filter
    }

    fun deleteRecord(record: ConversionRecord) {
        viewModelScope.launch {
            repository.deleteRecord(record)
        }
    }

    fun renameRecord(record: ConversionRecord, newTitle: String) {
        viewModelScope.launch {
            repository.renameRecord(record.id, newTitle)
        }
    }
}
