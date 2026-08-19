package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.CrochetPattern
import com.example.data.repository.PatternRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val searchQuery: String = "",
    val selectedCategory: String = "All", // All, Amigurumi, Wearables, Blankets, Accessories, Home Decor
    val selectedDifficulty: String = "All", // All, Beginner, Easy, Intermediate, Advanced
    val freeOnly: Boolean = false,
    val isSearching: Boolean = false,
    val activeTab: Int = 0, // 0: Discover Search, 1: Bookmarks / My Saved Patterns
    val results: List<CrochetPattern> = emptyList(),
    val selectedPatternForDetail: CrochetPattern? = null,
    val isAddCustomDialogOpen: Boolean = false,
    val statusMessage: String? = null
)

class CrochetViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = PatternRepository(database.patternDao())

    val savedPatterns: StateFlow<List<CrochetPattern>> = repository.savedPatternsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Load default results on launch
        performSearch("")
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.value = _uiState.value.copy(searchQuery = newQuery)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350) // Debounce rapid typing
            performSearch(newQuery)
        }
    }

    fun onCategorySelected(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        performSearch(_uiState.value.searchQuery)
    }

    fun onDifficultySelected(difficulty: String) {
        _uiState.value = _uiState.value.copy(selectedDifficulty = difficulty)
        performSearch(_uiState.value.searchQuery)
    }

    fun onToggleFreeOnly(freeOnly: Boolean) {
        _uiState.value = _uiState.value.copy(freeOnly = freeOnly)
        performSearch(_uiState.value.searchQuery)
    }

    fun onTabChanged(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(activeTab = tabIndex)
    }

    fun selectPatternForDetail(pattern: CrochetPattern?) {
        _uiState.value = _uiState.value.copy(selectedPatternForDetail = pattern)
    }

    fun toggleAddCustomDialog(isOpen: Boolean) {
        _uiState.value = _uiState.value.copy(isAddCustomDialogOpen = isOpen)
    }

    fun toggleBookmark(pattern: CrochetPattern) {
        viewModelScope.launch {
            val isCurrentlySaved = savedPatterns.value.any { it.id == pattern.id }
            if (isCurrentlySaved) {
                repository.removePattern(pattern.id)
                showStatusMessage("Removed from bookmarks")
            } else {
                repository.savePattern(pattern)
                showStatusMessage("Saved pattern to bookmarks!")
            }
            // Refresh search results to update bookmark states
            performSearch(_uiState.value.searchQuery)
        }
    }

    fun updatePatternNotes(patternId: String, notes: String) {
        viewModelScope.launch {
            repository.updatePatternNotes(patternId, notes)
            showStatusMessage("Pattern notes updated!")
        }
    }

    fun addCustomUserPattern(
        title: String,
        link: String,
        sourcePlatform: String,
        category: String,
        difficulty: String,
        hookSize: String,
        yarnWeight: String,
        notes: String
    ) {
        viewModelScope.launch {
            val customPattern = CrochetPattern(
                id = "custom_${System.currentTimeMillis()}",
                title = title,
                authorOrSource = "Custom Added Pattern",
                description = if (notes.isNotBlank()) notes else "Custom saved pattern link.",
                category = category,
                difficulty = difficulty,
                isFree = true,
                primaryLink = link,
                sourcePlatform = sourcePlatform,
                hookSize = hookSize.ifBlank { "4.0 mm" },
                yarnWeight = yarnWeight.ifBlank { "Worsted" },
                estimatedTime = "Custom",
                keyStitches = listOf("Custom"),
                isSaved = true,
                userNotes = notes
            )
            repository.savePattern(customPattern, notes)
            toggleAddCustomDialog(false)
            showStatusMessage("Saved custom pattern link!")
        }
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    private fun showStatusMessage(msg: String) {
        _uiState.value = _uiState.value.copy(statusMessage = msg)
    }

    fun triggerQuickTagSearch(tag: String) {
        onQueryChanged(tag)
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            val currentState = _uiState.value
            val results = repository.searchPatterns(
                query = query,
                selectedCategory = currentState.selectedCategory,
                selectedDifficulty = currentState.selectedDifficulty,
                selectedFreeOnly = currentState.freeOnly,
                useAiSearch = true
            )

            // Mark bookmarks state
            val savedIds = savedPatterns.value.map { it.id }.toSet()
            val updatedResults = results.map { p ->
                p.copy(isSaved = savedIds.contains(p.id))
            }

            _uiState.value = _uiState.value.copy(
                results = updatedResults,
                isSearching = false
            )
        }
    }
}
