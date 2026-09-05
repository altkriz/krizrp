package com.example.ui.screens.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChubCharacterNode
import com.example.data.model.FavoriteAuthor
import com.example.data.repository.ChubRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GalleryTab {
    DISCOVER,
    TRENDING,
    FAVORITE_AUTHORS
}

data class ChubGalleryUiState(
    val searchQuery: String = "",
    val characters: List<ChubCharacterNode> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val currentPage: Int = 1,
    val hasMorePages: Boolean = true,
    val nsfwEnabled: Boolean = true,
    val sortMode: String = "default", // "default", "trending"
    val selectedTab: GalleryTab = GalleryTab.DISCOVER,
    val favoriteAuthors: List<FavoriteAuthor> = emptyList(),
    val importingCharacterId: Long? = null,
    val selectedCharacterForDetail: ChubCharacterNode? = null,
    val isSelectedAuthorFavorite: Boolean = false,
    val snackbarMessage: String? = null
)

class ChubGalleryViewModel(
    private val chubRepository: ChubRepository
) : ViewModel() {

    val repository: ChubRepository get() = chubRepository

    private val _uiState = MutableStateFlow(ChubGalleryUiState())
    val uiState: StateFlow<ChubGalleryUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Collect favorite authors live from repository
        viewModelScope.launch {
            chubRepository.observeFavoriteAuthors().collectLatest { favorites ->
                _uiState.update { current ->
                    val selectedAuthor = current.selectedCharacterForDetail?.author
                    val isFav = selectedAuthor?.let { name ->
                        favorites.any { it.username.equals(name, ignoreCase = true) }
                    } ?: false
                    current.copy(favoriteAuthors = favorites, isSelectedAuthorFavorite = isFav)
                }
            }
        }

        // Initial load
        performSearch(reset = true)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400) // Debounce search
            performSearch(reset = true)
        }
    }

    fun setSortMode(sort: String) {
        if (_uiState.value.sortMode == sort) return
        _uiState.update { it.copy(sortMode = sort) }
        performSearch(reset = true)
    }

    fun toggleNsfw() {
        val newNsfw = !_uiState.value.nsfwEnabled
        _uiState.update { it.copy(nsfwEnabled = newNsfw) }
        performSearch(reset = true)
    }

    fun setSelectedTab(tab: GalleryTab) {
        if (tab == GalleryTab.TRENDING) {
            _uiState.update { it.copy(selectedTab = tab, sortMode = "trending") }
            performSearch(reset = true)
        } else if (tab == GalleryTab.DISCOVER) {
            _uiState.update { it.copy(selectedTab = tab, sortMode = "default") }
            performSearch(reset = true)
        } else {
            _uiState.update { it.copy(selectedTab = tab) }
        }
    }

    fun performSearch(reset: Boolean = true) {
        val current = _uiState.value
        val page = if (reset) 1 else current.currentPage + 1

        if (!reset && (!current.hasMorePages || current.isLoadingMore || current.isLoading)) {
            return
        }

        viewModelScope.launch {
            if (reset) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            } else {
                _uiState.update { it.copy(isLoadingMore = true) }
            }

            try {
                val result = chubRepository.search(
                    query = current.searchQuery,
                    page = page,
                    pageSize = 24,
                    nsfw = current.nsfwEnabled,
                    sort = current.sortMode
                )

                val newCharacters = if (reset) {
                    result.characters
                } else {
                    current.characters + result.characters
                }

                _uiState.update {
                    it.copy(
                        characters = newCharacters,
                        isLoading = false,
                        isLoadingMore = false,
                        currentPage = page,
                        hasMorePages = result.characters.isNotEmpty(),
                        errorMessage = if (newCharacters.isEmpty() && !reset) null else if (newCharacters.isEmpty()) "No characters found" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = "Error loading characters: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun openCharacterDetail(node: ChubCharacterNode) {
        val isFav = _uiState.value.favoriteAuthors.any { it.username.equals(node.author, ignoreCase = true) }
        _uiState.update {
            it.copy(
                selectedCharacterForDetail = node,
                isSelectedAuthorFavorite = isFav
            )
        }
    }

    fun closeCharacterDetail() {
        _uiState.update { it.copy(selectedCharacterForDetail = null) }
    }

    fun toggleFavoriteAuthor(authorName: String) {
        viewModelScope.launch {
            val isNowFav = chubRepository.toggleFavoriteAuthor(authorName)
            val msg = if (isNowFav) "Added @$authorName to favorite authors" else "Removed @$authorName from favorites"
            _uiState.update { current ->
                val isSelectedFav = current.selectedCharacterForDetail?.author.equals(authorName, ignoreCase = true)
                current.copy(
                    isSelectedAuthorFavorite = if (isSelectedFav) isNowFav else current.isSelectedAuthorFavorite,
                    snackbarMessage = msg
                )
            }
        }
    }

    fun importCharacter(node: ChubCharacterNode) {
        viewModelScope.launch {
            _uiState.update { it.copy(importingCharacterId = node.id) }
            val result = chubRepository.importCharacter(node)
            _uiState.update { it.copy(importingCharacterId = null) }

            result.onSuccess { savedChar ->
                _uiState.update {
                    it.copy(snackbarMessage = "Successfully imported '${savedChar.name}' to your local characters!")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(snackbarMessage = "Import failed: ${error.localizedMessage ?: error.message}")
                }
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

class ChubGalleryViewModelFactory(
    private val chubRepository: ChubRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChubGalleryViewModel(chubRepository) as T
    }
}
