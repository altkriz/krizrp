package com.example.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.CharacterRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.SettingsRepository
import com.example.engine.LLMClient
import com.example.engine.PromptBuilder
import com.example.util.CrashLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUiState(
    val character: CharacterEntity? = null,
    val userPersona: CharacterEntity? = null,
    val currentChat: ChatSessionEntity? = null,
    val allChatsForCharacter: List<ChatSessionEntity> = emptyList(),
    val messages: List<ChatMessageWithSwipes> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val streamingMessageId: Long? = null,
    val streamingText: String = "",
    val connectionConfig: ConnectionConfig = ConnectionConfig(),
    val generationSettings: GenerationSettings = GenerationSettings()
)

class ChatViewModel(
    private val characterId: Long,
    private val chatRepository: ChatRepository,
    private val characterRepository: CharacterRepository,
    private val settingsRepository: SettingsRepository,
    private val llmClient: LLMClient = LLMClient()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeGenerationJob: Job? = null

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val char = characterRepository.getCharacterById(characterId)
            val config = settingsRepository.getConnectionConfig()
            val genSettings = settingsRepository.getGenerationSettings()
            val activePersonaId = settingsRepository.getActivePersonaId()
            val persona = characterRepository.getCharacterById(activePersonaId) ?: CharacterEntity(
                type = "user",
                name = "User",
                avatarColor = 0xFF3F51B5
            )

            _uiState.update {
                it.copy(
                    character = char,
                    userPersona = persona,
                    connectionConfig = config,
                    generationSettings = genSettings
                )
            }

            // Observe chats for this character
            chatRepository.getChatsForCharacter(characterId).collect { chats ->
                _uiState.update { it.copy(allChatsForCharacter = chats) }

                if (_uiState.value.currentChat == null && chats.isNotEmpty()) {
                    selectChat(chats.first().id)
                } else if (chats.isEmpty() && char != null) {
                    // Create first chat with all greetings
                    val altList = parseAlternateGreetings(char.alternateGreetings)
                    val newChatId = chatRepository.createChat(
                        characterId = characterId,
                        userPersonaId = persona.id,
                        title = "Chat 1",
                        firstGreeting = char.firstMes,
                        characterName = char.name,
                        alternateGreetings = altList
                    )
                    selectChat(newChatId)
                }
            }
        }
    }

    private fun parseAlternateGreetings(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val g = arr.optString(i, "")
                if (g.isNotBlank()) list.add(g)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun selectChat(chatId: Long) {
        viewModelScope.launch {
            val chat = chatRepository.getChatById(chatId)
            _uiState.update { it.copy(currentChat = chat) }

            // Observe messages for selected chat
            chatRepository.getMessagesWithSwipes(chatId).collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
    }

    fun createNewChat(title: String = "New Chat") {
        viewModelScope.launch {
            val char = _uiState.value.character ?: return@launch
            val persona = _uiState.value.userPersona
            val altList = parseAlternateGreetings(char.alternateGreetings)
            val newChatId = chatRepository.createChat(
                characterId = characterId,
                userPersonaId = persona?.id ?: 0L,
                title = title,
                firstGreeting = char.firstMes,
                characterName = char.name,
                alternateGreetings = altList
            )
            selectChat(newChatId)
        }
    }

    fun renameChat(chatId: Long, newTitle: String) {
        viewModelScope.launch {
            chatRepository.updateChatTitle(chatId, newTitle)
        }
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            chatRepository.deleteChat(chatId)
            val remaining = _uiState.value.allChatsForCharacter.filter { it.id != chatId }
            if (remaining.isNotEmpty()) {
                selectChat(remaining.first().id)
            } else {
                createNewChat("Chat 1")
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val chat = _uiState.value.currentChat ?: return
        val char = _uiState.value.character ?: return
        val userPersona = _uiState.value.userPersona
        val userName = userPersona?.name ?: "User"

        if (text.isBlank()) return

        _uiState.update { it.copy(inputText = "") }

        viewModelScope.launch {
            try {
                val currentOrder = _uiState.value.messages.size
                // 1. Insert user message
                chatRepository.addMessageWithSwipe(
                    chatId = chat.id,
                    isUser = true,
                    senderName = userName,
                    content = text,
                    orderIndex = currentOrder
                )

                // 2. Trigger character response
                generateCharacterResponse(chat.id, char, userPersona, currentOrder + 1)
            } catch (e: Exception) {
                CrashLogger.logError("ChatViewModel", "Error sending message: ${e.message}", e)
                android.util.Log.e("ChatViewModel", "Error sending message", e)
            }
        }
    }

    fun regenerateLastMessage(messageItem: ChatMessageWithSwipes) {
        val chat = _uiState.value.currentChat ?: return
        val char = _uiState.value.character ?: return
        val userPersona = _uiState.value.userPersona

        viewModelScope.launch {
            try {
                val currentHistory = _uiState.value.messages.filter { it.message.orderIndex < messageItem.message.orderIndex }
                val systemPrompt = PromptBuilder.buildSystemPrompt(char, userPersona)
                val config = _uiState.value.connectionConfig
                val genSettings = _uiState.value.generationSettings

                // Add an empty new swipe to the message
                val swipeResult = chatRepository.addSwipeToMessage(messageItem.message.id, "")
                chatRepository.updateMessageSwipeIndex(messageItem.message.id, swipeResult.newIndex)

                val newSwipeId = swipeResult.swipeId

                _uiState.update {
                    it.copy(
                        isGenerating = true,
                        streamingMessageId = messageItem.message.id,
                        streamingText = ""
                    )
                }

                var accumulated = ""
                activeGenerationJob?.cancel()
                activeGenerationJob = viewModelScope.launch {
                    try {
                        llmClient.streamChatCompletion(
                            config = config,
                            settings = genSettings,
                            systemPrompt = systemPrompt,
                            history = currentHistory,
                            character = char,
                            userPersona = userPersona
                        ).collect { chunk ->
                            accumulated += chunk
                            _uiState.update { it.copy(streamingText = accumulated) }
                        }
                    } catch (e: Exception) {
                        CrashLogger.logError("ChatViewModel", "Error streaming regeneration: ${e.message}", e)
                        android.util.Log.e("ChatViewModel", "Error streaming regeneration", e)
                    } finally {
                        if (newSwipeId > 0) {
                            chatRepository.updateSwipeContent(newSwipeId, accumulated)
                        }
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                streamingMessageId = null,
                                streamingText = ""
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                CrashLogger.logError("ChatViewModel", "Error initiating regenerate: ${e.message}", e)
                android.util.Log.e("ChatViewModel", "Error initiating regenerate", e)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingMessageId = null,
                        streamingText = ""
                    )
                }
            }
        }
    }

    private fun generateCharacterResponse(
        chatId: Long,
        character: CharacterEntity,
        userPersona: CharacterEntity?,
        orderIndex: Int
    ) {
        val config = _uiState.value.connectionConfig
        val genSettings = _uiState.value.generationSettings
        val systemPrompt = PromptBuilder.buildSystemPrompt(character, userPersona)

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            try {
                // Snapshot current history before assistant message
                val history = chatRepository.getMessagesSnapshot(chatId)

                // Insert placeholder message for assistant
                val result = chatRepository.addMessageWithSwipe(
                    chatId = chatId,
                    isUser = false,
                    senderName = character.name,
                    content = "",
                    orderIndex = orderIndex
                )
                val assistantMsgId = result.messageId
                val assistantSwipeId = result.swipeId

                _uiState.update {
                    it.copy(
                        isGenerating = true,
                        streamingMessageId = assistantMsgId,
                        streamingText = ""
                    )
                }

                var accumulated = ""
                try {
                    llmClient.streamChatCompletion(
                        config = config,
                        settings = genSettings,
                        systemPrompt = systemPrompt,
                        history = history,
                        character = character,
                        userPersona = userPersona
                    ).collect { chunk ->
                        accumulated += chunk
                        _uiState.update { it.copy(streamingText = accumulated) }
                    }
                } catch (e: Exception) {
                    CrashLogger.logError("ChatViewModel", "Stream collection error: ${e.message}", e)
                    android.util.Log.e("ChatViewModel", "Stream collection error", e)
                } finally {
                    if (assistantSwipeId > 0) {
                        chatRepository.updateSwipeContent(assistantSwipeId, accumulated)
                    }
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            streamingMessageId = null,
                            streamingText = ""
                        )
                    }
                }
            } catch (e: Exception) {
                CrashLogger.logError("ChatViewModel", "Error generating response: ${e.message}", e)
                android.util.Log.e("ChatViewModel", "Error generating response", e)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingMessageId = null,
                        streamingText = ""
                    )
                }
            }
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        _uiState.update {
            it.copy(
                isGenerating = false,
                streamingMessageId = null,
                streamingText = ""
            )
        }
    }

    fun switchSwipe(messageItem: ChatMessageWithSwipes, newIndex: Int) {
        if (newIndex in 0 until messageItem.swipeCount) {
            viewModelScope.launch {
                chatRepository.updateMessageSwipeIndex(messageItem.message.id, newIndex)
            }
        }
    }

    fun editSwipeContent(swipeId: Long, newContent: String) {
        viewModelScope.launch {
            chatRepository.updateSwipeContent(swipeId, newContent)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId)
        }
    }
}
