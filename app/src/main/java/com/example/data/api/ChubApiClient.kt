package com.example.data.api

import android.net.Uri
import com.example.data.model.ChubCharacterNode
import com.example.data.model.ChubSearchResult
import com.example.util.CrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ChubApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val RO_CHUB_SEARCH_URL = "https://ro.chub.ai/search"
        private const val GATEWAY_SEARCH_URL = "https://gateway.chub.ai/search"
        private const val GATEWAY_USERS_URL = "https://gateway.chub.ai/api/users"
        private const val GATEWAY_CHAR_URL = "https://gateway.chub.ai/api/characters"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }

    suspend fun searchCharacters(
        query: String = "",
        page: Int = 1,
        pageSize: Int = 24,
        nsfw: Boolean = true,
        sort: String = "default" // "default", "trending"
    ): ChubSearchResult = withContext(Dispatchers.IO) {
        // Try ro.chub.ai primary search first
        try {
            val result = searchRoChub(query, page, pageSize, nsfw, sort)
            if (result.characters.isNotEmpty() || page > 1) {
                return@withContext result
            }
        } catch (e: Exception) {
            CrashLogger.logWarning("ChubApiClient", "ro.chub.ai search failed, attempting gateway fallback: ${e.message}")
        }

        // Fallback to gateway.chub.ai/search
        try {
            return@withContext searchGatewayChub(query, page, pageSize, nsfw, sort)
        } catch (e: Exception) {
            CrashLogger.logError("ChubApiClient", "Gateway search failed: ${e.message}", e)
            return@withContext ChubSearchResult(emptyList(), page, 0, null)
        }
    }

    private fun searchRoChub(
        query: String,
        page: Int,
        pageSize: Int,
        nsfw: Boolean,
        sort: String
    ): ChubSearchResult {
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val url = buildString {
            append(RO_CHUB_SEARCH_URL).append("?")
            append("search=").append(encodedQuery)
            append("&page=").append(page)
            append("&first=").append(pageSize)
            append("&namespace=*")
            append("&include_forks=true")
            append("&nsfw=").append(nsfw)
            append("&nsfw_only=false")
            append("&nsfl=").append(nsfw)
            append("&sort=").append(sort)
            append("&min_ai_rating=0")
            append("&min_tokens=50")
            append("&max_tokens=100000")
            append("&chub=true")
            append("&asc=false")
            append("&bypass=true")
            append("&count=false")
        }

        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody(null)) // Empty body as observed in Chub research
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .addHeader("Origin", "https://chub.ai")
            .addHeader("Referer", "https://chub.ai/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string().orEmpty()
            return parseSearchResponse(body, page)
        }
    }

    private fun searchGatewayChub(
        query: String,
        page: Int,
        pageSize: Int,
        nsfw: Boolean,
        sort: String
    ): ChubSearchResult {
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val url = buildString {
            append(GATEWAY_SEARCH_URL).append("?")
            append("search=").append(encodedQuery)
            append("&page=").append(page)
            append("&first=").append(pageSize)
            append("&nsfw=").append(nsfw)
            append("&nsfl=").append(nsfw)
            append("&count=false")
            append("&sort=").append(sort)
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("User-Agent", "CharacterChatApp/1.0")
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Gateway HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string().orEmpty()
            return parseSearchResponse(body, page)
        }
    }

    suspend fun getAuthorProjects(
        username: String,
        nsfw: Boolean = true
    ): List<ChubCharacterNode> = withContext(Dispatchers.IO) {
        try {
            val encodedUsername = URLEncoder.encode(username.trim(), "UTF-8")
            val url = "$GATEWAY_USERS_URL/$encodedUsername?include_projects=true&nsfw=$nsfw"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    CrashLogger.logWarning("ChubApiClient", "Author HTTP ${response.code}: ${response.message}")
                    return@withContext emptyList()
                }
                val body = response.body?.string().orEmpty()
                return@withContext parseAuthorProjectsResponse(body, username)
            }
        } catch (e: Exception) {
            CrashLogger.logError("ChubApiClient", "Failed to fetch author projects: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getCharacterByPath(fullPath: String): ChubCharacterNode? = withContext(Dispatchers.IO) {
        val candidateUrls = listOf(
            "$GATEWAY_CHAR_URL/$fullPath",
            "https://chub.ai/api/characters/$fullPath",
            "https://ro.chub.ai/api/characters/$fullPath"
        )
        for (url in candidateUrls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            val nodeObj = if (json.has("data")) {
                                val data = json.optJSONObject("data")
                                data?.optJSONObject("node") ?: json.optJSONObject("node")
                            } else {
                                json.optJSONObject("node")
                            }
                            if (nodeObj != null) {
                                return@withContext parseNode(nodeObj)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                CrashLogger.logWarning("ChubApiClient", "Endpoint $url check failed: ${e.message}")
            }
        }
        null
    }

    suspend fun downloadBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            CrashLogger.logWarning("ChubApiClient", "Failed to download from $url: ${e.message}")
            null
        }
    }

    private fun parseSearchResponse(jsonStr: String, defaultPage: Int): ChubSearchResult {
        val root = JSONObject(jsonStr)
        val dataObj = root.optJSONObject("data") ?: root

        val page = dataObj.optInt("page", defaultPage)
        val count = dataObj.optInt("count", 0)
        val cursor = dataObj.optString("cursor", "").takeIf { it.isNotBlank() }

        val nodesArray = dataObj.optJSONArray("nodes")
            ?: root.optJSONArray("nodes")
            ?: JSONArray()

        val list = mutableListOf<ChubCharacterNode>()
        for (i in 0 until nodesArray.length()) {
            val nodeObj = nodesArray.optJSONObject(i) ?: continue
            val node = parseNode(nodeObj)
            // Only add character projects (ignore pure lorebooks if desired, or include characters)
            if (node.projectSpace.isEmpty() || node.projectSpace.equals("characters", ignoreCase = true)) {
                list.add(node)
            }
        }

        return ChubSearchResult(
            characters = list,
            page = page,
            totalCount = count,
            cursor = cursor
        )
    }

    private fun parseAuthorProjectsResponse(jsonStr: String, defaultAuthor: String): List<ChubCharacterNode> {
        val root = JSONObject(jsonStr)
        val list = mutableListOf<ChubCharacterNode>()

        // Check various response locations: projects.nodes, data.projects.nodes, node.projects.nodes, nodes
        var nodesArray: JSONArray? = null
        val projectsObj = root.optJSONObject("projects")
            ?: root.optJSONObject("data")?.optJSONObject("projects")
            ?: root.optJSONObject("node")?.optJSONObject("projects")

        if (projectsObj != null) {
            nodesArray = projectsObj.optJSONArray("nodes")
        }
        if (nodesArray == null) {
            nodesArray = root.optJSONArray("nodes")
        }

        if (nodesArray != null) {
            for (i in 0 until nodesArray.length()) {
                val nodeObj = nodesArray.optJSONObject(i) ?: continue
                val node = parseNode(nodeObj)
                list.add(node)
            }
        }
        return list
    }

    private fun parseNode(nodeObj: JSONObject): ChubCharacterNode {
        val topicsList = mutableListOf<String>()
        val topicsArr = nodeObj.optJSONArray("topics")
        if (topicsArr != null) {
            for (j in 0 until topicsArr.length()) {
                val topic = topicsArr.optString(j)
                if (topic.isNotBlank()) topicsList.add(topic)
            }
        }

        // Try extracting token counts if available from nTokens or labels
        var nTokens = nodeObj.optInt("nTokens", 0)
        if (nTokens == 0) {
            val labelsArr = nodeObj.optJSONArray("labels")
            if (labelsArr != null) {
                for (j in 0 until labelsArr.length()) {
                    val label = labelsArr.optJSONObject(j) ?: continue
                    if (label.optString("title") == "TOKEN_COUNTS") {
                        val desc = label.optString("description")
                        try {
                            val tokenJson = JSONObject(desc)
                            nTokens = tokenJson.optInt("total", 0)
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        return ChubCharacterNode(
            id = nodeObj.optLong("id", 0L),
            name = nodeObj.optString("name", "Unnamed"),
            fullPath = nodeObj.optString("fullPath", ""),
            description = nodeObj.optString("description", ""),
            tagline = nodeObj.optString("tagline", ""),
            starCount = nodeObj.optInt("starCount", 0),
            forksCount = nodeObj.optInt("forksCount", 0),
            rating = nodeObj.optDouble("rating", 0.0),
            ratingCount = nodeObj.optInt("ratingCount", 0),
            nChats = nodeObj.optInt("nChats", nodeObj.optInt("n_public_chats", 0)),
            nMessages = nodeObj.optInt("nMessages", 0),
            nTokens = nTokens,
            avatarUrl = nodeObj.optString("avatar_url", ""),
            maxResUrl = nodeObj.optString("max_res_url", ""),
            topics = topicsList,
            createdAt = nodeObj.optString("createdAt", ""),
            lastActivityAt = nodeObj.optString("lastActivityAt", ""),
            verified = nodeObj.optBoolean("verified", false),
            projectSpace = nodeObj.optString("projectSpace", "characters"),
            nsfwImage = nodeObj.optBoolean("nsfw_image", false)
        )
    }
}
