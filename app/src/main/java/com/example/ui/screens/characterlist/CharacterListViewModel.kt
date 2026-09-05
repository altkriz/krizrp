package com.example.ui.screens.characterlist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CharacterEntity
import com.example.data.repository.CharacterRepository
import com.example.data.repository.ChatRepository
import com.example.util.CrashLogger
import com.example.util.TavernCardUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class CharacterListUiState(
    val characters: List<CharacterEntity> = emptyList(),
    val filteredCharacters: List<CharacterEntity> = emptyList(),
    val allTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val snackbarMessage: String? = null
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
                val tagsSet = linkedSetOf("AI", "Adventure", "Assistant", "Cyberpunk", "Fantasy", "Sci-Fi", "Romance", "Wholesome")
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
                        allTags = tagsSet.toList(),
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
            showSnackbar("Deleted ${character.name}")
        }
    }

    fun duplicateCharacter(character: CharacterEntity) {
        viewModelScope.launch {
            characterRepository.duplicateCharacter(character)
            showSnackbar("Duplicated ${character.name}")
        }
    }

    fun importTavernCard(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    showSnackbar("Could not open card file")
                    return@launch
                }

                val parsed = TavernCardUtils.parseCardFromStream(inputStream, context)
                if (parsed == null) {
                    showSnackbar("Invalid Tavern Card format (expected PNG with 'chara' chunk or JSON)")
                    return@launch
                }

                var avatarPath = ""
                if (parsed.avatarBitmap != null) {
                    val avatarsDir = File(context.filesDir, "avatars")
                    if (!avatarsDir.exists()) avatarsDir.mkdirs()
                    val avatarFile = File(avatarsDir, "char_avatar_${System.currentTimeMillis()}.png")
                    FileOutputStream(avatarFile).use { out ->
                        parsed.avatarBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    avatarPath = avatarFile.absolutePath
                }

                val characterToSave = parsed.character.copy(
                    avatarUri = avatarPath,
                    lastModified = System.currentTimeMillis()
                )

                characterRepository.saveCharacter(characterToSave)
                showSnackbar("Successfully imported card: ${characterToSave.name}")
            } catch (e: Exception) {
                CrashLogger.logError("CardImport", "Import failed: ${e.message}", e)
                showSnackbar("Import failed: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    fun exportCharacterCard(character: CharacterEntity, context: Context, asPng: Boolean): File? {
        return try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()
            val safeName = character.name.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "character" }

            if (asPng) {
                val file = File(exportDir, "${safeName}_card.png")
                val bitmap = if (character.avatarUri.isNotBlank() && File(character.avatarUri).exists()) {
                    BitmapFactory.decodeFile(character.avatarUri)
                } else {
                    val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    val paint = android.graphics.Paint().apply {
                        color = character.avatarColor.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    canvas.drawRect(0f, 0f, 512f, 512f, paint)
                    bmp
                }
                val pngBytes = TavernCardUtils.exportToPngCard(bitmap, character)
                FileOutputStream(file).use { it.write(pngBytes) }
                file
            } else {
                val file = File(exportDir, "${safeName}_card.json")
                val jsonStr = TavernCardUtils.characterToTavernV2Json(character)
                FileOutputStream(file).use { it.write(jsonStr.toByteArray(Charsets.UTF_8)) }
                file
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
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
                    char.tags.split(",").any { it.trim().equals(selectedTag, ignoreCase = true) } ||
                    char.description.contains(selectedTag, ignoreCase = true) ||
                    char.personality.contains(selectedTag, ignoreCase = true)

            matchesQuery && matchesTag
        }
    }
}
