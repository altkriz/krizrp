package com.example.data.db

import androidx.room.*
import com.example.data.model.CharacterEntity
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSessionEntity
import com.example.data.model.MessageSwipeEntity
import com.example.data.model.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters WHERE type = :type ORDER BY lastModified DESC")
    fun getCharactersByType(type: String): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun getCharacterById(id: Long): CharacterEntity?

    @Query("SELECT * FROM characters WHERE id = :id")
    fun observeCharacterById(id: Long): Flow<CharacterEntity?>

    @Query("SELECT COUNT(*) FROM characters WHERE type = :type")
    suspend fun countByType(type: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: CharacterEntity): Long

    @Update
    suspend fun updateCharacter(character: CharacterEntity)

    @Delete
    suspend fun deleteCharacter(character: CharacterEntity)

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteCharacterById(id: Long)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions WHERE characterId = :characterId ORDER BY lastModified DESC")
    fun getChatsForCharacter(characterId: Long): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getChatById(id: Long): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions ORDER BY lastModified DESC LIMIT 1")
    suspend fun getLatestChat(): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatSessionEntity): Long

    @Update
    suspend fun updateChat(chat: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :chatId")
    suspend fun deleteChatById(chatId: Long)

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY orderIndex ASC, id ASC")
    fun getMessagesForChat(chatId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY orderIndex ASC, id ASC")
    suspend fun getMessagesListForChat(chatId: Long): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Query("DELETE FROM chat_messages WHERE chatId = :chatId")
    suspend fun clearMessagesForChat(chatId: Long)

    @Query("SELECT * FROM message_swipes WHERE messageId = :messageId ORDER BY id ASC")
    fun getSwipesForMessage(messageId: Long): Flow<List<MessageSwipeEntity>>

    @Query("SELECT * FROM message_swipes WHERE messageId = :messageId ORDER BY id ASC")
    suspend fun getSwipesListForMessage(messageId: Long): List<MessageSwipeEntity>

    @Query("SELECT * FROM message_swipes WHERE messageId IN (:messageIds) ORDER BY id ASC")
    suspend fun getSwipesForMessages(messageIds: List<Long>): List<MessageSwipeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSwipe(swipe: MessageSwipeEntity): Long

    @Update
    suspend fun updateSwipe(swipe: MessageSwipeEntity)

    @Query("DELETE FROM message_swipes WHERE id = :swipeId")
    suspend fun deleteSwipeById(swipeId: Long)
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): String?

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    fun observeSetting(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: SettingEntity)
}
