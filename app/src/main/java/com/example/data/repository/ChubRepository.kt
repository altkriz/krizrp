package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.data.api.ChubApiClient
import com.example.data.db.SettingDao
import com.example.data.model.CharacterEntity
import com.example.data.model.ChubCharacterNode
import com.example.data.model.ChubSearchResult
import com.example.data.model.FavoriteAuthor
import com.example.data.model.SettingEntity
import com.example.util.CrashLogger
import com.example.util.TavernCardUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

class ChubRepository(
    private val apiClient: ChubApiClient,
    private val settingDao: SettingDao,
    private val characterRepository: CharacterRepository,
    private val context: Context
) {
    companion object {
        private const val KEY_FAVORITE_AUTHORS = "favorite_chub_authors"
    }

    suspend fun search(
        query: String,
        page: Int = 1,
        pageSize: Int = 24,
        nsfw: Boolean = true,
        sort: String = "default"
    ): ChubSearchResult = withContext(Dispatchers.IO) {
        apiClient.searchCharacters(query, page, pageSize, nsfw, sort)
    }

    suspend fun getAuthorProjects(username: String, nsfw: Boolean = true): List<ChubCharacterNode> =
        withContext(Dispatchers.IO) {
            apiClient.getAuthorProjects(username, nsfw)
        }

    suspend fun getCharacterByPath(fullPath: String): ChubCharacterNode? =
        withContext(Dispatchers.IO) {
            apiClient.getCharacterByPath(fullPath)
        }

    /**
     * Extracts Chub character path (e.g. "Anonymous/ayesha-khan-93a4601a56b0")
     * from full URLs (chub.ai, characterhub.org, venus.chub.ai, charhub.io) or raw author/slug strings.
     */
    fun extractChubFullPath(input: String): String? {
        var raw = input.trim()
        if (raw.isEmpty()) return null
        raw = raw.substringBefore("?").substringBefore("#").trimEnd('/')

        // Match URLs with /characters/ e.g. https://chub.ai/characters/Anonymous/ayesha-khan-93a4601a56b0
        if (raw.contains("/characters/")) {
            val path = raw.substringAfter("/characters/").trim('/')
            val parts = path.split("/").filter { it.isNotBlank() }
            if (parts.size >= 2) {
                return "${parts[0]}/${parts[1]}"
            }
        }

        // Match paths starting with characters/ e.g. characters/Anonymous/ayesha-khan-93a4601a56b0
        if (raw.startsWith("characters/")) {
            val path = raw.removePrefix("characters/").trim('/')
            val parts = path.split("/").filter { it.isNotBlank() }
            if (parts.size >= 2) {
                return "${parts[0]}/${parts[1]}"
            }
        }

        // Match charhub avatar asset URLs e.g. https://avatars.charhub.io/avatars/Anonymous/ayesha-khan-93a4601a56b0/chara_card_v2.png
        if (raw.contains("/avatars/")) {
            val afterAvatars = raw.substringAfter("/avatars/").trim('/')
            val parts = afterAvatars.split("/").filter { it.isNotBlank() }
            if (parts.size >= 2) {
                return "${parts[0]}/${parts[1]}"
            }
        }

        // Match direct "Author/slug" pattern (e.g. "Anonymous/ayesha-khan-93a4601a56b0")
        val directParts = raw.trim('/').split("/").filter { it.isNotBlank() }
        if (directParts.size == 2 && !directParts[0].contains(":") && !directParts[0].contains(".")) {
            return "${directParts[0]}/${directParts[1]}"
        }

        return null
    }

    /**
     * Imports a character from a Chub URL (e.g. https://chub.ai/characters/Anonymous/ayesha-khan-93a4601a56b0)
     * or a raw character path.
     */
    suspend fun importCharacterFromUrlOrPath(input: String): Result<CharacterEntity> = withContext(Dispatchers.IO) {
        val fullPath = extractChubFullPath(input)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Invalid Chub URL or character path. Example:\nhttps://chub.ai/characters/Anonymous/ayesha-khan-93a4601a56b0")
            )

        // 1. Try fetching detailed metadata from Chub API
        val fetchedNode = try {
            apiClient.getCharacterByPath(fullPath)
        } catch (e: Exception) {
            null
        }

        val targetNode = if (fetchedNode != null) {
            fetchedNode
        } else {
            // Synthesize node from path so importCharacter can fetch chara_card_v2.png directly
            val slug = fullPath.substringAfter("/")
            val guessedName = slug.replace(Regex("-[0-9a-f]{8,}$"), "")
                .replace("-", " ")
                .split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                .ifBlank { slug }

            ChubCharacterNode(
                name = guessedName,
                fullPath = fullPath
            )
        }

        importCharacter(targetNode)
    }

    /**
     * Downloads and imports a Chub character into the local database.
     * Supports embedded Tavern V2 / V1 PNG cards as well as direct Chub node data fallback.
     */
    suspend fun importCharacter(node: ChubCharacterNode): Result<CharacterEntity> = withContext(Dispatchers.IO) {
        try {
            var finalCharacter: CharacterEntity? = null
            var savedAvatarPath = ""

            // 1. Try downloading character card PNG (V2 card chunk embedded)
            val cardUrl = node.resolvedCardUrl
            val cardBytes = if (cardUrl.isNotBlank()) apiClient.downloadBytes(cardUrl) else null

            if (cardBytes != null && cardBytes.isNotEmpty()) {
                val parsed = TavernCardUtils.parseCardFromStream(ByteArrayInputStream(cardBytes), context)
                if (parsed != null) {
                    if (parsed.avatarBitmap != null) {
                        savedAvatarPath = saveBitmapToFile(parsed.avatarBitmap)
                    }

                    val char = parsed.character
                    finalCharacter = char.copy(
                        id = 0L,
                        name = char.name.ifBlank { node.name },
                        description = char.description.ifBlank { node.description },
                        creator = char.creator.ifBlank { node.author },
                        tags = if (char.tags.isNotBlank()) char.tags else node.topics.joinToString(","),
                        firstMes = char.firstMes.ifBlank { node.tagline },
                        avatarUri = savedAvatarPath.ifBlank { char.avatarUri },
                        lastModified = System.currentTimeMillis()
                    )
                }
            }

            // 2. If no card chunk found or card download failed, fall back to node metadata
            if (finalCharacter == null) {
                // Try fetching detailed character node if available
                val detailedNode = if (node.description.length < 20 && node.fullPath.isNotBlank()) {
                    apiClient.getCharacterByPath(node.fullPath) ?: node
                } else node

                // Download avatar image
                val avatarUrl = detailedNode.resolvedAvatarUrl
                if (avatarUrl.isNotBlank()) {
                    val avatarBytes = apiClient.downloadBytes(avatarUrl)
                    if (avatarBytes != null && avatarBytes.isNotEmpty()) {
                        val bmp = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.size)
                        if (bmp != null) {
                            savedAvatarPath = saveBitmapToFile(bmp)
                        }
                    }
                }

                val introGreeting = if (detailedNode.tagline.isNotBlank()) {
                    "*${detailedNode.name} looks over at you thoughtfully.*\n\n\"${detailedNode.tagline}\""
                } else {
                    "*${detailedNode.name} greets you warmly.*"
                }

                finalCharacter = CharacterEntity(
                    id = 0L,
                    type = "character",
                    name = detailedNode.name.ifBlank { "Unnamed" },
                    description = detailedNode.description.ifBlank { detailedNode.tagline },
                    personality = detailedNode.tagline,
                    scenario = "{{user}} encounters ${detailedNode.name}.",
                    firstMes = introGreeting,
                    creator = detailedNode.author,
                    tags = detailedNode.topics.joinToString(","),
                    avatarUri = savedAvatarPath,
                    lastModified = System.currentTimeMillis()
                )
            }

            // Save to database
            val savedId = characterRepository.saveCharacter(finalCharacter)
            val completeEntity = finalCharacter.copy(id = savedId)
            Result.success(completeEntity)
        } catch (e: Exception) {
            CrashLogger.logError("ChubImport", "Failed to import character ${node.name}: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap): String {
        val avatarsDir = File(context.filesDir, "avatars")
        if (!avatarsDir.exists()) avatarsDir.mkdirs()
        val file = File(avatarsDir, "chub_avatar_${System.currentTimeMillis()}_${(100..999).random()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        return file.absolutePath
    }

    // ----------------------------------------------------
    // Favorite Authors Management
    // ----------------------------------------------------

    fun observeFavoriteAuthors(): Flow<List<FavoriteAuthor>> {
        return settingDao.observeSetting(KEY_FAVORITE_AUTHORS)
            .map { jsonStr -> parseFavoriteAuthors(jsonStr) }
            .flowOn(Dispatchers.IO)
    }

    suspend fun getFavoriteAuthors(): List<FavoriteAuthor> = withContext(Dispatchers.IO) {
        val jsonStr = settingDao.getSetting(KEY_FAVORITE_AUTHORS)
        parseFavoriteAuthors(jsonStr)
    }

    suspend fun isFavoriteAuthor(username: String): Boolean = withContext(Dispatchers.IO) {
        val list = getFavoriteAuthors()
        list.any { it.username.equals(username.trim(), ignoreCase = true) }
    }

    suspend fun toggleFavoriteAuthor(username: String): Boolean = withContext(Dispatchers.IO) {
        val cleanName = username.trim()
        if (cleanName.isBlank()) return@withContext false

        val currentList = getFavoriteAuthors().toMutableList()
        val existingIndex = currentList.indexOfFirst { it.username.equals(cleanName, ignoreCase = true) }

        val isNowFavorite: Boolean
        if (existingIndex >= 0) {
            currentList.removeAt(existingIndex)
            isNowFavorite = false
        } else {
            currentList.add(0, FavoriteAuthor(username = cleanName))
            isNowFavorite = true
        }

        saveFavoriteAuthors(currentList)
        isNowFavorite
    }

    suspend fun removeFavoriteAuthor(username: String) = withContext(Dispatchers.IO) {
        val currentList = getFavoriteAuthors().filterNot { it.username.equals(username.trim(), ignoreCase = true) }
        saveFavoriteAuthors(currentList)
    }

    private suspend fun saveFavoriteAuthors(list: List<FavoriteAuthor>) {
        val array = JSONArray()
        list.forEach { author ->
            val obj = JSONObject().apply {
                put("username", author.username)
                put("addedAt", author.addedAt)
            }
            array.put(obj)
        }
        settingDao.setSetting(SettingEntity(KEY_FAVORITE_AUTHORS, array.toString()))
    }

    private fun parseFavoriteAuthors(jsonStr: String?): List<FavoriteAuthor> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<FavoriteAuthor>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i)
                if (obj != null) {
                    val name = obj.optString("username")
                    val time = obj.optLong("addedAt", System.currentTimeMillis())
                    if (name.isNotBlank()) {
                        list.add(FavoriteAuthor(username = name, addedAt = time))
                    }
                } else {
                    // String array fallback
                    val name = array.optString(i)
                    if (name.isNotBlank()) {
                        list.add(FavoriteAuthor(username = name))
                    }
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
