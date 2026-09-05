package com.example.ui.screens.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChubCharacterNode
import com.example.data.repository.ChubRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthorProfileUiState(
    val authorName: String = "",
    val characters: List<ChubCharacterNode> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isFavorite: Boolean = false,
    val importingCharacterId: Long? = null,
    val selectedCharacterForDetail: ChubCharacterNode? = null,
    val snackbarMessage: String? = null
)

class AuthorProfileViewModel(
    private val authorName: String,
    private val chubRepository: ChubRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthorProfileUiState(authorName = authorName))
    val uiState: StateFlow<AuthorProfileUiState> = _uiState.asStateFlow()

    init {
        // Observe if this author is marked favorite
        viewModelScope.launch {
            chubRepository.observeFavoriteAuthors().collectLatest { favorites ->
                val fav = favorites.any { it.username.equals(authorName, ignoreCase = true) }
                _uiState.update { it.copy(isFavorite = fav) }
            }
        }

        loadAuthorProjects()
    }

    fun loadAuthorProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val projects = chubRepository.getAuthorProjects(authorName, nsfw = true)
                _uiState.update {
                    it.copy(
                        characters = projects,
                        isLoading = false,
                        errorMessage = if (projects.isEmpty()) "No public characters found for @$authorName" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Could not load author's characters: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val isNowFav = chubRepository.toggleFavoriteAuthor(authorName)
            val msg = if (isNowFav) "Added @$authorName to favorite authors" else "Removed @$authorName from favorites"
            _uiState.update {
                it.copy(isFavorite = isNowFav, snackbarMessage = msg)
            }
        }
    }

    fun openCharacterDetail(node: ChubCharacterNode) {
        _uiState.update { it.copy(selectedCharacterForDetail = node) }
    }

    fun closeCharacterDetail() {
        _uiState.update { it.copy(selectedCharacterForDetail = null) }
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

class AuthorProfileViewModelFactory(
    private val authorName: String,
    private val chubRepository: ChubRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AuthorProfileViewModel(authorName, chubRepository) as T
    }
}
