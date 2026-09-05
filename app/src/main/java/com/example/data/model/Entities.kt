package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "characters",
    indices = [Index(value = ["type"]), Index(value = ["lastModified"])]
)
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String = "character", // "character" or "user"
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val creatorNotes: String = "",
    val creator: String = "",
    val version: String = "1.0",
    val avatarColor: Long = 0xFF6750A4,
    val avatarUri: String = "",
    val tags: String = "", // comma-separated tags
    val alternateGreetings: String = "[]", // JSON array
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_sessions",
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["characterId"]), Index(value = ["lastModified"])]
)
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: Long,
    val userPersonaId: Long = 0,
    val title: String = "New Chat",
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chatId"]), Index(value = ["orderIndex"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val isUser: Boolean,
    val senderName: String,
    val activeSwipeIndex: Int = 0,
    val orderIndex: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "message_swipes",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["messageId"])]
)
data class MessageSwipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

data class ChatMessageWithSwipes(
    val message: ChatMessageEntity,
    val swipes: List<MessageSwipeEntity>
) {
    val currentContent: String
        get() = if (swipes.isNotEmpty()) {
            val idx = message.activeSwipeIndex.coerceIn(0, swipes.size - 1)
            swipes[idx].content
        } else ""

    val swipeCount: Int
        get() = swipes.size
}

data class GenerationSettings(
    val temperature: Float = 0.8f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val repetitionPenalty: Float = 1.1f,
    val maxTokens: Int = 512,
    val streamResponse: Boolean = true
)

data class ConnectionConfig(
    val provider: String = "gemini", // "gemini", "openai", "ollama", "custom"
    val apiKey: String = "",
    val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai/",
    val modelName: String = "gemini-2.5-flash",
    val customHeaders: String = ""
)
