package com.example.ui.screens.characterlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CharacterEntity
import com.example.data.repository.CharacterRepository
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CharacterListUiState(
    val characters: List<CharacterEntity> = emptyList(),
    val filteredCharacters: List<CharacterEntity> = emptyList(),
    val allTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

class CharacterListViewModel(
    private val characterRepository: CharacterRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharacterListUiState())
    val uiState: StateFlow<CharacterListUiState> = _uiState.asStateFlow()

    init {
        loadCharacters()
    }

    private fun loadCharacters() {
        viewModelScope.launch {
            characterRepository.getCharacters().collect { charList ->
                val tagsSet = mutableSetOf<String>()
                charList.forEach { char ->
                    if (char.tags.isNotBlank()) {
                        char.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                            tagsSet.add(it)
                        }
                    }
                }

                _uiState.update { current ->
                    val filtered = applyFilter(charList, current.searchQuery, current.selectedTag)
                    current.copy(
                        characters = charList,
                        filteredCharacters = filtered,
                        allTags = tagsSet.toList().sorted(),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredCharacters = applyFilter(current.characters, query, current.selectedTag)
            )
        }
    }

    fun onTagSelected(tag: String?) {
        _uiState.update { current ->
            val newTag = if (current.selectedTag == tag) null else tag
            current.copy(
                selectedTag = newTag,
                filteredCharacters = applyFilter(current.characters, current.searchQuery, newTag)
            )
        }
    }

    fun deleteCharacter(character: CharacterEntity) {
        viewModelScope.launch {
            characterRepository.deleteCharacter(character.id)
        }
    }

    fun duplicateCharacter(character: CharacterEntity) {
        viewModelScope.launch {
            characterRepository.duplicateCharacter(character)
        }
    }

    private fun applyFilter(
        list: List<CharacterEntity>,
        query: String,
        selectedTag: String?
    ): List<CharacterEntity> {
        return list.filter { char ->
            val matchesQuery = query.isBlank() ||
                    char.name.contains(query, ignoreCase = true) ||
                    char.description.contains(query, ignoreCase = true) ||
                    char.tags.contains(query, ignoreCase = true)

            val matchesTag = selectedTag == null ||
                    char.tags.split(",").map { it.trim().lowercase() }.contains(selectedTag.lowercase())

            matchesQuery && matchesTag
        }
    }
}
