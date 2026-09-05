package com.example.data.repository

import com.example.data.db.CharacterDao
import com.example.data.db.ChatDao
import com.example.data.db.SettingDao
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CharacterRepository(private val characterDao: CharacterDao) {
    fun getCharacters(): Flow<List<CharacterEntity>> =
        characterDao.getCharactersByType("character").flowOn(Dispatchers.IO)

    fun getUserPersonas(): Flow<List<CharacterEntity>> =
        characterDao.getCharactersByType("user").flowOn(Dispatchers.IO)

    suspend fun getCharacterById(id: Long): CharacterEntity? =
        withContext(Dispatchers.IO) {
            characterDao.getCharacterById(id)
        }

    fun observeCharacterById(id: Long): Flow<CharacterEntity?> =
        characterDao.observeCharacterById(id).flowOn(Dispatchers.IO)

    suspend fun saveCharacter(character: CharacterEntity): Long =
        withContext(Dispatchers.IO) {
            if (character.id == 0L) {
                characterDao.insertCharacter(character.copy(lastModified = System.currentTimeMillis()))
            } else {
                characterDao.updateCharacter(character.copy(lastModified = System.currentTimeMillis()))
                character.id
            }
        }

    suspend fun deleteCharacter(id: Long) =
        withContext(Dispatchers.IO) {
            characterDao.deleteCharacterById(id)
        }

    suspend fun duplicateCharacter(character: CharacterEntity): Long =
        withContext(Dispatchers.IO) {
            val duplicate = character.copy(
                id = 0,
                name = "${character.name} (Copy)",
                lastModified = System.currentTimeMillis()
            )
            characterDao.insertCharacter(duplicate)
        }
}

class ChatRepository(private val chatDao: ChatDao) {
    fun getChatsForCharacter(characterId: Long): Flow<List<ChatSessionEntity>> =
        chatDao.getChatsForCharacter(characterId).flowOn(Dispatchers.IO)

    suspend fun getChatById(id: Long): ChatSessionEntity? =
        withContext(Dispatchers.IO) {
            chatDao.getChatById(id)
        }

    suspend fun getLatestChat(): ChatSessionEntity? =
        withContext(Dispatchers.IO) {
            chatDao.getLatestChat()
        }

    suspend fun createChat(
        characterId: Long,
        userPersonaId: Long,
        title: String,
        firstGreeting: String,
        characterName: String
    ): Long = withContext(Dispatchers.IO) {
        val chatId = chatDao.insertChat(
            ChatSessionEntity(
                characterId = characterId,
                userPersonaId = userPersonaId,
                title = title
            )
        )
        if (firstGreeting.isNotBlank()) {
            val msgId = chatDao.insertMessage(
                ChatMessageEntity(
                    chatId = chatId,
                    isUser = false,
                    senderName = characterName,
                    activeSwipeIndex = 0,
                    orderIndex = 0
                )
            )
            chatDao.insertSwipe(
                MessageSwipeEntity(
                    messageId = msgId,
                    content = firstGreeting
                )
            )
        }
        chatId
    }

    suspend fun updateChatTitle(chatId: Long, newTitle: String) =
        withContext(Dispatchers.IO) {
            val chat = chatDao.getChatById(chatId)
            if (chat != null) {
                chatDao.updateChat(chat.copy(title = newTitle, lastModified = System.currentTimeMillis()))
            }
        }

    suspend fun deleteChat(chatId: Long) =
        withContext(Dispatchers.IO) {
            chatDao.deleteChatById(chatId)
        }

    fun getMessagesWithSwipes(chatId: Long): Flow<List<ChatMessageWithSwipes>> {
        return chatDao.getMessagesForChat(chatId).map { messages ->
            val messageIds = messages.map { it.id }
            val swipes = if (messageIds.isNotEmpty()) chatDao.getSwipesForMessages(messageIds) else emptyList()
            val swipesByMessageId = swipes.groupBy { it.messageId }

            messages.map { msg ->
                ChatMessageWithSwipes(
                    message = msg,
                    swipes = swipesByMessageId[msg.id] ?: emptyList()
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun getMessagesSnapshot(chatId: Long): List<ChatMessageWithSwipes> =
        withContext(Dispatchers.IO) {
            val messages = chatDao.getMessagesListForChat(chatId)
            val messageIds = messages.map { it.id }
            val swipes = if (messageIds.isNotEmpty()) chatDao.getSwipesForMessages(messageIds) else emptyList()
            val swipesByMessageId = swipes.groupBy { it.messageId }

            messages.map { msg ->
                ChatMessageWithSwipes(
                    message = msg,
                    swipes = swipesByMessageId[msg.id] ?: emptyList()
                )
            }
        }

    suspend fun addMessageWithSwipe(
        chatId: Long,
        isUser: Boolean,
        senderName: String,
        content: String,
        orderIndex: Int
    ): Long = withContext(Dispatchers.IO) {
        val msgId = chatDao.insertMessage(
            ChatMessageEntity(
                chatId = chatId,
                isUser = isUser,
                senderName = senderName,
                activeSwipeIndex = 0,
                orderIndex = orderIndex,
                timestamp = System.currentTimeMillis()
            )
        )
        chatDao.insertSwipe(
            MessageSwipeEntity(
                messageId = msgId,
                content = content
            )
        )
        // touch chat last modified
        chatDao.getChatById(chatId)?.let {
            chatDao.updateChat(it.copy(lastModified = System.currentTimeMillis()))
        }
        msgId
    }

    suspend fun addSwipeToMessage(messageId: Long, content: String): Int =
        withContext(Dispatchers.IO) {
            chatDao.insertSwipe(
                MessageSwipeEntity(
                    messageId = messageId,
                    content = content
                )
            )
            val allSwipes = chatDao.getSwipesListForMessage(messageId)
            val newIdx = allSwipes.size - 1
            // update message to point to new swipe
            // find message
            val messages = chatDao.getMessagesListForChat(-1) // or fetch
            // Or get through swipes
            newIdx
        }

    suspend fun updateMessageSwipeIndex(message: ChatMessageEntity, newIndex: Int) =
        withContext(Dispatchers.IO) {
            chatDao.updateMessage(message.copy(activeSwipeIndex = newIndex))
        }

    suspend fun updateSwipeContent(swipeId: Long, newContent: String) =
        withContext(Dispatchers.IO) {
            chatDao.updateSwipe(MessageSwipeEntity(id = swipeId, messageId = 0, content = newContent))
        }

    suspend fun deleteMessage(messageId: Long) =
        withContext(Dispatchers.IO) {
            chatDao.deleteMessageById(messageId)
        }
}

class SettingsRepository(private val settingDao: SettingDao) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getConnectionConfig(): ConnectionConfig = withContext(Dispatchers.IO) {
        val raw = settingDao.getSetting("connection_config")
        if (raw.isNullOrBlank()) {
            ConnectionConfig()
        } else {
            try {
                ConnectionConfig(
                    provider = parseString(raw, "provider") ?: "gemini",
                    apiKey = parseString(raw, "apiKey") ?: "",
                    baseUrl = parseString(raw, "baseUrl") ?: "https://generativelanguage.googleapis.com/v1beta/openai/",
                    modelName = parseString(raw, "modelName") ?: "gemini-2.5-flash",
                    customHeaders = parseString(raw, "customHeaders") ?: ""
                )
            } catch (e: Exception) {
                ConnectionConfig()
            }
        }
    }

    suspend fun saveConnectionConfig(config: ConnectionConfig) = withContext(Dispatchers.IO) {
        val serialized = "{\"provider\":\"${config.provider}\",\"apiKey\":\"${config.apiKey}\",\"baseUrl\":\"${config.baseUrl}\",\"modelName\":\"${config.modelName}\",\"customHeaders\":\"${config.customHeaders}\"}"
        settingDao.setSetting(SettingEntity("connection_config", serialized))
    }

    suspend fun getGenerationSettings(): GenerationSettings = withContext(Dispatchers.IO) {
        val raw = settingDao.getSetting("generation_settings")
        if (raw.isNullOrBlank()) {
            GenerationSettings()
        } else {
            try {
                GenerationSettings(
                    temperature = parseFloat(raw, "temperature") ?: 0.8f,
                    topP = parseFloat(raw, "topP") ?: 0.95f,
                    topK = parseInt(raw, "topK") ?: 40,
                    repetitionPenalty = parseFloat(raw, "repetitionPenalty") ?: 1.1f,
                    maxTokens = parseInt(raw, "maxTokens") ?: 512,
                    streamResponse = parseBool(raw, "streamResponse") ?: true
                )
            } catch (e: Exception) {
                GenerationSettings()
            }
        }
    }

    suspend fun saveGenerationSettings(settings: GenerationSettings) = withContext(Dispatchers.IO) {
        val serialized = "{\"temperature\":${settings.temperature},\"topP\":${settings.topP},\"topK\":${settings.topK},\"repetitionPenalty\":${settings.repetitionPenalty},\"maxTokens\":${settings.maxTokens},\"streamResponse\":${settings.streamResponse}}"
        settingDao.setSetting(SettingEntity("generation_settings", serialized))
    }

    suspend fun getActivePersonaId(): Long = withContext(Dispatchers.IO) {
        settingDao.getSetting("active_persona_id")?.toLongOrNull() ?: 1L
    }

    suspend fun setActivePersonaId(id: Long) = withContext(Dispatchers.IO) {
        settingDao.setSetting(SettingEntity("active_persona_id", id.toString()))
    }

    suspend fun getThemePreference(): String = withContext(Dispatchers.IO) {
        settingDao.getSetting("theme_pref") ?: "dark"
    }

    suspend fun saveThemePreference(theme: String) = withContext(Dispatchers.IO) {
        settingDao.setSetting(SettingEntity("theme_pref", theme))
    }

    private fun parseString(json: String, key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun parseFloat(json: String, key: String): Float? {
        val regex = Regex("\"$key\"\\s*:\\s*([0-9.]+)")
        return regex.find(json)?.groupValues?.get(1)?.toFloatOrNull()
    }

    private fun parseInt(json: String, key: String): Int? {
        val regex = Regex("\"$key\"\\s*:\\s*([0-9]+)")
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun parseBool(json: String, key: String): Boolean? {
        val regex = Regex("\"$key\"\\s*:\\s*(true|false)")
        return regex.find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()
    }
}
