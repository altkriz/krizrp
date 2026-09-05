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

data class MessageSwipeResult(val messageId: Long, val swipeId: Long)
data class NewSwipeResult(val swipeId: Long, val newIndex: Int)

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
        characterName: String,
        alternateGreetings: List<String> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        val chatId = chatDao.insertChat(
            ChatSessionEntity(
                characterId = characterId,
                userPersonaId = userPersonaId,
                title = title
            )
        )
        if (firstGreeting.isNotBlank() || alternateGreetings.isNotEmpty()) {
            val msgId = chatDao.insertMessage(
                ChatMessageEntity(
                    chatId = chatId,
                    isUser = false,
                    senderName = characterName,
                    activeSwipeIndex = 0,
                    orderIndex = 0
                )
            )
            if (firstGreeting.isNotBlank()) {
                chatDao.insertSwipe(
                    MessageSwipeEntity(
                        messageId = msgId,
                        content = firstGreeting
                    )
                )
            }
            alternateGreetings.forEach { alt ->
                if (alt.isNotBlank()) {
                    chatDao.insertSwipe(
                        MessageSwipeEntity(
                            messageId = msgId,
                            content = alt
                        )
                    )
                }
            }
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
        return kotlinx.coroutines.flow.combine(
            chatDao.getMessagesForChat(chatId),
            chatDao.getSwipesForChat(chatId)
        ) { messages, swipes ->
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
    ): MessageSwipeResult = withContext(Dispatchers.IO) {
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
        val swipeId = chatDao.insertSwipe(
            MessageSwipeEntity(
                messageId = msgId,
                content = content
            )
        )
        // touch chat last modified
        chatDao.getChatById(chatId)?.let {
            chatDao.updateChat(it.copy(lastModified = System.currentTimeMillis()))
        }
        MessageSwipeResult(msgId, swipeId)
    }

    suspend fun addSwipeToMessage(messageId: Long, content: String): NewSwipeResult =
        withContext(Dispatchers.IO) {
            val swipeId = chatDao.insertSwipe(
                MessageSwipeEntity(
                    messageId = messageId,
                    content = content
                )
            )
            val allSwipes = chatDao.getSwipesListForMessage(messageId)
            val newIdx = (allSwipes.size - 1).coerceAtLeast(0)
            NewSwipeResult(swipeId, newIdx)
        }

    suspend fun updateMessageSwipeIndex(messageId: Long, newIndex: Int) =
        withContext(Dispatchers.IO) {
            chatDao.updateMessageActiveSwipeIndex(messageId, newIndex)
        }

    suspend fun updateSwipeContent(swipeId: Long, newContent: String) =
        withContext(Dispatchers.IO) {
            chatDao.updateSwipeContent(swipeId, newContent)
        }

    suspend fun deleteMessage(messageId: Long) =
        withContext(Dispatchers.IO) {
            chatDao.deleteMessageById(messageId)
        }
}

class SettingsRepository(private val settingDao: SettingDao) {

    suspend fun getAllConnections(): List<ConnectionConfig> = withContext(Dispatchers.IO) {
        val raw = settingDao.getSetting("all_connections")
        if (raw.isNullOrBlank()) {
            val defaultList = listOf(
                ConnectionConfig(
                    id = "gemini_default",
                    friendlyName = "Google Gemini Flash",
                    provider = "gemini",
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
                    modelName = "gemini-2.5-flash",
                    modelEndpoint = "https://generativelanguage.googleapis.com/v1beta/openai/models",
                    availableModels = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash-exp", "gemini-2.0-flash-thinking-exp"),
                    active = true
                ),
                ConnectionConfig(
                    id = "openai_default",
                    friendlyName = "OpenAI GPT-4o Mini",
                    provider = "openai",
                    baseUrl = "https://api.openai.com/v1/",
                    modelName = "gpt-4o-mini",
                    modelEndpoint = "https://api.openai.com/v1/models",
                    availableModels = listOf("gpt-4o-mini", "gpt-4o", "o1-mini", "o3-mini"),
                    active = false
                ),
                ConnectionConfig(
                    id = "openrouter_default",
                    friendlyName = "OpenRouter",
                    provider = "openrouter",
                    baseUrl = "https://openrouter.ai/api/v1/",
                    modelName = "google/gemini-2.5-flash",
                    modelEndpoint = "https://openrouter.ai/api/v1/models",
                    availableModels = listOf("google/gemini-2.5-flash", "deepseek/deepseek-r1", "anthropic/claude-3.5-sonnet"),
                    active = false
                ),
                ConnectionConfig(
                    id = "ollama_default",
                    friendlyName = "Local Ollama",
                    provider = "ollama",
                    baseUrl = "http://10.0.2.2:11434/v1/",
                    modelName = "llama3:8b",
                    modelEndpoint = "http://10.0.2.2:11434/api/tags",
                    availableModels = listOf("llama3:8b", "mistral:7b", "qwen2.5:7b"),
                    active = false
                )
            )
            saveAllConnections(defaultList)
            defaultList
        } else {
            try {
                val array = org.json.JSONArray(raw)
                val list = mutableListOf<ConnectionConfig>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val modelsArr = obj.optJSONArray("availableModels")
                    val modelsList = mutableListOf<String>()
                    if (modelsArr != null) {
                        for (m in 0 until modelsArr.length()) {
                            modelsList.add(modelsArr.getString(m))
                        }
                    }
                    list.add(
                        ConnectionConfig(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            friendlyName = obj.optString("friendlyName", "Connection"),
                            provider = obj.optString("provider", "gemini"),
                            apiKey = obj.optString("apiKey", ""),
                            baseUrl = obj.optString("baseUrl", "https://generativelanguage.googleapis.com/v1beta/openai/"),
                            modelName = obj.optString("modelName", "gemini-2.5-flash"),
                            modelEndpoint = obj.optString("modelEndpoint", ""),
                            availableModels = modelsList,
                            customHeaders = obj.optString("customHeaders", ""),
                            active = obj.optBoolean("active", false)
                        )
                    )
                }
                list
            } catch (e: Exception) {
                listOf(ConnectionConfig())
            }
        }
    }

    suspend fun saveAllConnections(connections: List<ConnectionConfig>) = withContext(Dispatchers.IO) {
        val array = org.json.JSONArray()
        connections.forEach { conn ->
            val obj = org.json.JSONObject()
            obj.put("id", conn.id)
            obj.put("friendlyName", conn.friendlyName)
            obj.put("provider", conn.provider)
            obj.put("apiKey", conn.apiKey)
            obj.put("baseUrl", conn.baseUrl)
            obj.put("modelName", conn.modelName)
            obj.put("modelEndpoint", conn.modelEndpoint)
            val modelsArr = org.json.JSONArray()
            conn.availableModels.forEach { modelsArr.put(it) }
            obj.put("availableModels", modelsArr)
            obj.put("customHeaders", conn.customHeaders)
            obj.put("active", conn.active)
            array.put(obj)
        }
        settingDao.setSetting(SettingEntity("all_connections", array.toString()))
    }

    suspend fun getConnectionConfig(): ConnectionConfig = withContext(Dispatchers.IO) {
        val all = getAllConnections()
        all.firstOrNull { it.active } ?: all.firstOrNull() ?: ConnectionConfig()
    }

    suspend fun saveConnectionConfig(config: ConnectionConfig) = withContext(Dispatchers.IO) {
        val all = getAllConnections().toMutableList()
        val existingIndex = all.indexOfFirst { it.id == config.id }
        if (existingIndex >= 0) {
            all[existingIndex] = config
        } else {
            all.add(config)
        }
        if (config.active) {
            all.forEachIndexed { idx, item ->
                all[idx] = item.copy(active = item.id == config.id)
            }
        }
        saveAllConnections(all)
    }

    suspend fun deleteConnection(id: String) = withContext(Dispatchers.IO) {
        val all = getAllConnections().filter { it.id != id }
        saveAllConnections(all)
    }

    suspend fun setActiveConnection(id: String) = withContext(Dispatchers.IO) {
        val all = getAllConnections().map { it.copy(active = it.id == id) }
        saveAllConnections(all)
    }

    suspend fun getGenerationSettings(): GenerationSettings = withContext(Dispatchers.IO) {
        val raw = settingDao.getSetting("generation_settings")
        if (raw.isNullOrBlank()) {
            GenerationSettings()
        } else {
            try {
                val obj = org.json.JSONObject(raw)
                GenerationSettings(
                    temperature = obj.optDouble("temperature", 0.8).toFloat(),
                    topP = obj.optDouble("topP", 0.95).toFloat(),
                    topK = obj.optInt("topK", 40),
                    minP = obj.optDouble("minP", 0.05).toFloat(),
                    repetitionPenalty = obj.optDouble("repetitionPenalty", 1.1).toFloat(),
                    frequencyPenalty = obj.optDouble("frequencyPenalty", 0.0).toFloat(),
                    presencePenalty = obj.optDouble("presencePenalty", 0.0).toFloat(),
                    maxTokens = obj.optInt("maxTokens", 512),
                    contextLength = obj.optInt("contextLength", 4096),
                    streamResponse = obj.optBoolean("streamResponse", true),
                    reasoningEffort = obj.optString("reasoningEffort", "medium"),
                    reasoningMaxTokens = obj.optInt("reasoningMaxTokens", 1024),
                    excludeReasoning = obj.optBoolean("excludeReasoning", false)
                )
            } catch (e: Exception) {
                GenerationSettings()
            }
        }
    }

    suspend fun saveGenerationSettings(settings: GenerationSettings) = withContext(Dispatchers.IO) {
        val obj = org.json.JSONObject()
        obj.put("temperature", settings.temperature.toDouble())
        obj.put("topP", settings.topP.toDouble())
        obj.put("topK", settings.topK)
        obj.put("minP", settings.minP.toDouble())
        obj.put("repetitionPenalty", settings.repetitionPenalty.toDouble())
        obj.put("frequencyPenalty", settings.frequencyPenalty.toDouble())
        obj.put("presencePenalty", settings.presencePenalty.toDouble())
        obj.put("maxTokens", settings.maxTokens)
        obj.put("contextLength", settings.contextLength)
        obj.put("streamResponse", settings.streamResponse)
        obj.put("reasoningEffort", settings.reasoningEffort)
        obj.put("reasoningMaxTokens", settings.reasoningMaxTokens)
        obj.put("excludeReasoning", settings.excludeReasoning)
        settingDao.setSetting(SettingEntity("generation_settings", obj.toString()))
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
}
