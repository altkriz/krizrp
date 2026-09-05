package com.example.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.data.model.*
import com.example.data.repository.CharacterRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.ChubRepository
import com.example.data.repository.SettingsRepository
import com.example.engine.LLMClient
import com.example.engine.PromptBuilder
import com.example.util.CrashLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

class DesktopHttpServer(
    private val context: Context,
    private val port: Int,
    private val characterRepository: CharacterRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val chubRepository: ChubRepository
) {
    private var serverSocket: ServerSocket? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private val llmClient = LLMClient()
    private val activeClients = ConcurrentHashMap.newKeySet<Socket>()

    fun start() {
        if (isRunning) return
        isRunning = true
        serverScope.launch {
            try {
                val socket = ServerSocket(port)
                serverSocket = socket
                DesktopServerManager.log("HTTP server listening on port $port")

                while (isRunning && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        activeClients.add(client)
                        DesktopServerManager.updateClientCount(activeClients.size)
                        serverScope.launch {
                            try {
                                handleClient(client)
                            } catch (e: Exception) {
                                // client disconnected or error
                            } finally {
                                activeClients.remove(client)
                                DesktopServerManager.updateClientCount(activeClients.size)
                                try { client.close() } catch (_: Exception) {}
                            }
                        }
                    } catch (e: Exception) {
                        if (!isRunning || socket.isClosed) break
                    }
                }
            } catch (e: Exception) {
                CrashLogger.logError("DesktopHttpServer", "Server socket error on port $port: ${e.message}", e)
                DesktopServerManager.log("Server error: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        for (c in activeClients) {
            try { c.close() } catch (_: Exception) {}
        }
        activeClients.clear()
        serverScope.cancel()
    }

    private suspend fun handleClient(socket: Socket) {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))

        val requestLine = reader.readLine() ?: return
        DesktopServerManager.incrementRequestCount()

        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0].uppercase()
        val rawUri = parts[1]
        val path = rawUri.substringBefore("?")
        val query = if (rawUri.contains("?")) rawUri.substringAfter("?") else ""

        // Read headers
        val headers = mutableMapOf<String, String>()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            if (line.isNullOrBlank()) break
            val colonIdx = line!!.indexOf(":")
            if (colonIdx > 0) {
                val key = line!!.substring(0, colonIdx).trim().lowercase()
                val value = line!!.substring(colonIdx + 1).trim()
                headers[key] = value
            }
        }

        // Handle CORS preflight
        if (method == "OPTIONS") {
            sendCorsOptionsResponse(output)
            return
        }

        // Read Body for POST / PUT / PATCH
        var body = ""
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength > 0) {
            val charBuffer = CharArray(contentLength)
            var totalRead = 0
            while (totalRead < contentLength) {
                val read = reader.read(charBuffer, totalRead, contentLength - totalRead)
                if (read == -1) break
                totalRead += read
            }
            body = String(charBuffer, 0, totalRead)
        }

        routeRequest(method, path, query, headers, body, output, socket)
    }

    private suspend fun routeRequest(
        method: String,
        path: String,
        query: String,
        headers: Map<String, String>,
        body: String,
        output: OutputStream,
        socket: Socket
    ) {
        when {
            // Web frontend SPA
            path == "/" || path == "/index.html" -> serveWebAsset("web/index.html", "text/html; charset=utf-8", output)
            path.startsWith("/assets/") -> serveWebAsset("web" + path, getMimeType(path), output)

            // SSE Event Stream for live sync
            method == "GET" && path == "/api/events" -> handleEventStream(output, socket)

            // Status & Config
            method == "GET" && path == "/api/status" -> handleGetStatus(output)

            // Characters CRUD
            method == "GET" && path == "/api/characters" -> handleGetCharacters(output)
            method == "GET" && path.matches(Regex("^/api/characters/\\d+$")) -> {
                val id = path.substringAfterLast("/").toLongOrNull() ?: 0L
                handleGetCharacterById(id, output)
            }
            method == "GET" && path.matches(Regex("^/api/characters/\\d+/avatar$")) -> {
                val id = path.removePrefix("/api/characters/").removeSuffix("/avatar").toLongOrNull() ?: 0L
                handleGetCharacterAvatar(id, output)
            }
            method == "POST" && path == "/api/characters" -> handleSaveCharacter(body, output)
            method == "DELETE" && path.matches(Regex("^/api/characters/\\d+$")) -> {
                val id = path.substringAfterLast("/").toLongOrNull() ?: 0L
                handleDeleteCharacter(id, output)
            }

            // Chats CRUD
            method == "GET" && path.matches(Regex("^/api/chats/\\d+$")) -> {
                val charId = path.substringAfterLast("/").toLongOrNull() ?: 0L
                handleGetChats(charId, output)
            }
            method == "POST" && path == "/api/chats/new" -> handleCreateChat(body, output)
            method == "DELETE" && path.matches(Regex("^/api/chats/\\d+$")) -> {
                val chatId = path.substringAfterLast("/").toLongOrNull() ?: 0L
                handleDeleteChat(chatId, output)
            }

            // Messages & Swipes
            method == "GET" && path.matches(Regex("^/api/messages/\\d+$")) -> {
                val chatId = path.substringAfterLast("/").toLongOrNull() ?: 0L
                handleGetMessages(chatId, output)
            }
            method == "POST" && path == "/api/chat/send" -> handleSendMessageStream(body, output)
            method == "POST" && path == "/api/chat/regenerate" -> handleRegenerateMessageStream(body, output)
            method == "POST" && path == "/api/chat/swipe" -> handleSetSwipe(body, output)
            method == "POST" && path == "/api/chat/edit-message" -> handleEditMessage(body, output)
            method == "DELETE" && path.matches(Regex("^/api/chat/message/\\d+$")) -> {
                val messageId = path.substringAfterLast("/").toLongOrNull() ?: 0L
                handleDeleteMessage(messageId, output)
            }

            // Chub 1-Click Import
            method == "POST" && path == "/api/chub/import" -> handleChubImport(body, output)

            // User Personas
            method == "GET" && path == "/api/personas" -> handleGetPersonas(output)
            method == "POST" && path == "/api/personas/active" -> handleSetActivePersona(body, output)

            // Settings & Generation config
            method == "GET" && path == "/api/settings" -> handleGetSettings(output)

            else -> sendJsonResponse(output, 404, JSONObject().put("error", "Not Found: $path").toString())
        }
    }

    private fun serveWebAsset(assetPath: String, mimeType: String, output: OutputStream) {
        try {
            val assetManager = context.assets
            assetManager.open(assetPath).use { input ->
                val bytes = input.readBytes()
                val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: $mimeType\r\n" +
                        "Content-Length: ${bytes.size}\r\n" +
                        "Cache-Control: no-cache\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n"
                output.write(header.toByteArray(StandardCharsets.UTF_8))
                output.write(bytes)
                output.flush()
            }
        } catch (e: Exception) {
            sendJsonResponse(output, 404, JSONObject().put("error", "Asset not found: $assetPath").toString())
        }
    }

    private suspend fun handleGetStatus(output: OutputStream) {
        val config = settingsRepository.getConnectionConfig()
        val chars = characterRepository.getCharacters().firstOrNull() ?: emptyList()
        val activePersonaId = settingsRepository.getActivePersonaId()
        val persona = characterRepository.getCharacterById(activePersonaId)

        val json = JSONObject().apply {
            put("status", "ok")
            put("version", "1.0")
            put("serverTime", System.currentTimeMillis())
            put("characterCount", chars.size)
            put("activePersona", JSONObject().apply {
                put("id", persona?.id ?: 0L)
                put("name", persona?.name ?: "User")
            })
            put("aiProvider", JSONObject().apply {
                put("provider", config.provider)
                put("model", config.modelName)
                put("friendlyName", config.friendlyName)
                put("hasKey", config.apiKey.isNotBlank())
            })
        }
        sendJsonResponse(output, 200, json.toString())
    }

    private suspend fun handleGetCharacters(output: OutputStream) {
        val chars = characterRepository.getCharacters().firstOrNull() ?: emptyList()
        val array = JSONArray()
        chars.forEach { c ->
            array.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("description", c.description)
                put("creator", c.creator)
                put("tags", c.tags)
                put("firstMes", c.firstMes)
                put("hasAvatar", c.avatarUri.isNotBlank())
                put("avatarUrl", "/api/characters/${c.id}/avatar")
                put("lastModified", c.lastModified)
            })
        }
        sendJsonResponse(output, 200, array.toString())
    }

    private suspend fun handleGetCharacterById(id: Long, output: OutputStream) {
        val c = characterRepository.getCharacterById(id)
        if (c == null) {
            sendJsonResponse(output, 404, JSONObject().put("error", "Character not found").toString())
            return
        }
        val json = JSONObject().apply {
            put("id", c.id)
            put("name", c.name)
            put("description", c.description)
            put("personality", c.personality)
            put("scenario", c.scenario)
            put("firstMes", c.firstMes)
            put("mesExample", c.mesExample)
            put("systemPrompt", c.systemPrompt)
            put("postHistoryInstructions", c.postHistoryInstructions)
            put("creatorNotes", c.creatorNotes)
            put("creator", c.creator)
            put("tags", c.tags)
            put("alternateGreetings", c.alternateGreetings)
            put("avatarUrl", "/api/characters/${c.id}/avatar")
            put("lastModified", c.lastModified)
        }
        sendJsonResponse(output, 200, json.toString())
    }

    private suspend fun handleGetCharacterAvatar(id: Long, output: OutputStream) {
        val c = characterRepository.getCharacterById(id)
        if (c == null || c.avatarUri.isBlank()) {
            sendSvgAvatar(c?.name ?: "Char", output)
            return
        }

        try {
            val file = File(c.avatarUri)
            if (file.exists() && file.canRead()) {
                val bytes = file.readBytes()
                val mime = if (c.avatarUri.endsWith(".png", true)) "image/png" else "image/jpeg"
                val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: $mime\r\n" +
                        "Content-Length: ${bytes.size}\r\n" +
                        "Cache-Control: public, max-age=86400\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n"
                output.write(header.toByteArray(StandardCharsets.UTF_8))
                output.write(bytes)
                output.flush()
                return
            }
        } catch (_: Exception) {}

        sendSvgAvatar(c.name, output)
    }

    private fun sendSvgAvatar(name: String, output: OutputStream) {
        val initials = name.trim().take(2).uppercase().ifBlank { "??" }
        val hue = (name.hashCode() and 0x7FFFFFFF) % 360
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">
            <defs>
                <linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stop-color="hsl($hue, 70%, 45%)" />
                    <stop offset="100%" stop-color="hsl(${(hue + 45) % 360}, 80%, 25%)" />
                </linearGradient>
            </defs>
            <rect width="128" height="128" rx="28" fill="url(#grad)" />
            <text x="50%" y="54%" font-family="system-ui, -apple-system, sans-serif" font-size="44" font-weight="bold" fill="#ffffff" dominant-baseline="middle" text-anchor="middle">$initials</text>
        </svg>""".trimIndent()

        val bytes = svg.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: image/svg+xml; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Cache-Control: public, max-age=86400\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private suspend fun handleSaveCharacter(body: String, output: OutputStream) {
        try {
            val json = JSONObject(body)
            val id = json.optLong("id", 0L)
            val existing = if (id > 0) characterRepository.getCharacterById(id) else null

            val char = CharacterEntity(
                id = id,
                type = "character",
                name = json.optString("name", existing?.name ?: "New Character"),
                description = json.optString("description", existing?.description ?: ""),
                personality = json.optString("personality", existing?.personality ?: ""),
                scenario = json.optString("scenario", existing?.scenario ?: ""),
                firstMes = json.optString("firstMes", existing?.firstMes ?: ""),
                mesExample = json.optString("mesExample", existing?.mesExample ?: ""),
                systemPrompt = json.optString("systemPrompt", existing?.systemPrompt ?: ""),
                postHistoryInstructions = json.optString("postHistoryInstructions", existing?.postHistoryInstructions ?: ""),
                creatorNotes = json.optString("creatorNotes", existing?.creatorNotes ?: ""),
                creator = json.optString("creator", existing?.creator ?: "Desktop User"),
                tags = json.optString("tags", existing?.tags ?: ""),
                alternateGreetings = json.optString("alternateGreetings", existing?.alternateGreetings ?: "[]"),
                avatarUri = existing?.avatarUri ?: "",
                lastModified = System.currentTimeMillis()
            )
            val savedId = characterRepository.saveCharacter(char)
            DesktopServerManager.broadcastEvent("character_updated", JSONObject().put("id", savedId).toString())
            sendJsonResponse(output, 200, JSONObject().put("success", true).put("id", savedId).toString())
        } catch (e: Exception) {
            sendJsonResponse(output, 400, JSONObject().put("error", e.message).toString())
        }
    }

    private suspend fun handleDeleteCharacter(id: Long, output: OutputStream) {
        characterRepository.deleteCharacter(id)
        DesktopServerManager.broadcastEvent("character_deleted", JSONObject().put("id", id).toString())
        sendJsonResponse(output, 200, JSONObject().put("success", true).toString())
    }

    private suspend fun handleGetChats(characterId: Long, output: OutputStream) {
        val chats = chatRepository.getChatsForCharacter(characterId).firstOrNull() ?: emptyList()
        val array = JSONArray()
        chats.forEach { c ->
            array.put(JSONObject().apply {
                put("id", c.id)
                put("characterId", c.characterId)
                put("title", c.title)
                put("createdAt", c.createdAt)
                put("lastModified", c.lastModified)
            })
        }
        sendJsonResponse(output, 200, array.toString())
    }

    private suspend fun handleCreateChat(body: String, output: OutputStream) {
        try {
            val json = JSONObject(body)
            val charId = json.getLong("characterId")
            val title = json.optString("title", "New Chat")
            val char = characterRepository.getCharacterById(charId)
            val activePersonaId = settingsRepository.getActivePersonaId()

            if (char == null) {
                sendJsonResponse(output, 404, JSONObject().put("error", "Character not found").toString())
                return
            }

            val altList = try {
                val arr = JSONArray(char.alternateGreetings)
                (0 until arr.length()).mapNotNull { i -> arr.optString(i).ifBlank { null } }
            } catch (_: Exception) { emptyList() }

            val newChatId = chatRepository.createChat(
                characterId = charId,
                userPersonaId = activePersonaId,
                title = title,
                firstGreeting = char.firstMes,
                characterName = char.name,
                alternateGreetings = altList
            )
            DesktopServerManager.broadcastEvent("chat_created", JSONObject().put("chatId", newChatId).toString())
            characterRepository.touchCharacter(charId)
            DesktopServerManager.broadcastEvent("character_updated", JSONObject().put("id", charId).toString())
            sendJsonResponse(output, 200, JSONObject().put("success", true).put("chatId", newChatId).toString())
        } catch (e: Exception) {
            sendJsonResponse(output, 400, JSONObject().put("error", e.message).toString())
        }
    }

    private suspend fun handleDeleteChat(chatId: Long, output: OutputStream) {
        chatRepository.deleteChat(chatId)
        DesktopServerManager.broadcastEvent("chat_deleted", JSONObject().put("chatId", chatId).toString())
        sendJsonResponse(output, 200, JSONObject().put("success", true).toString())
    }

    private suspend fun handleGetMessages(chatId: Long, output: OutputStream) {
        val messagesWithSwipes = chatRepository.getMessagesWithSwipes(chatId).firstOrNull() ?: emptyList()
        val array = JSONArray()
        messagesWithSwipes.forEach { item ->
            val msgObj = JSONObject().apply {
                put("id", item.message.id)
                put("chatId", item.message.chatId)
                put("isUser", item.message.isUser)
                put("senderName", item.message.senderName)
                put("activeSwipeIndex", item.message.activeSwipeIndex)
                put("orderIndex", item.message.orderIndex)
                put("timestamp", item.message.timestamp)
                put("currentContent", item.currentContent)

                val swipesArray = JSONArray()
                item.swipes.forEach { s ->
                    swipesArray.put(JSONObject().apply {
                        put("id", s.id)
                        put("content", s.content)
                        put("createdAt", s.createdAt)
                    })
                }
                put("swipes", swipesArray)
            }
            array.put(msgObj)
        }
        sendJsonResponse(output, 200, array.toString())
    }

    private suspend fun handleSendMessageStream(body: String, output: OutputStream) {
        val json = JSONObject(body)
        val chatId = json.getLong("chatId")
        val characterId = json.getLong("characterId")
        val content = json.getString("content").trim()

        val char = characterRepository.getCharacterById(characterId)
        if (char == null) {
            sendJsonResponse(output, 404, JSONObject().put("error", "Character not found").toString())
            return
        }

        val activePersonaId = settingsRepository.getActivePersonaId()
        val userPersona = characterRepository.getCharacterById(activePersonaId)
        val userName = userPersona?.name ?: "User"

        // 1. Insert user message into phone Room DB
        val currentMessages = chatRepository.getMessagesWithSwipes(chatId).firstOrNull() ?: emptyList()
        val userOrder = currentMessages.size
        chatRepository.addMessageWithSwipe(
            chatId = chatId,
            isUser = true,
            senderName = userName,
            content = content,
            orderIndex = userOrder
        )

        characterRepository.touchCharacter(characterId)
        DesktopServerManager.broadcastEvent("message_added", JSONObject().put("chatId", chatId).toString())
        DesktopServerManager.broadcastEvent("character_updated", JSONObject().put("id", characterId).toString())

        // 2. Prepare AI generation
        val config = settingsRepository.getConnectionConfig()
        val genSettings = settingsRepository.getGenerationSettings()
        val systemPrompt = PromptBuilder.buildSystemPrompt(char, userPersona)
        val updatedHistory = chatRepository.getMessagesWithSwipes(chatId).firstOrNull() ?: emptyList()

        // 3. Stream response to client via SSE
        sendSseHeaders(output)

        var accumulated = ""
        try {
            llmClient.streamChatCompletion(
                config = config,
                settings = genSettings,
                systemPrompt = systemPrompt,
                history = updatedHistory,
                character = char,
                userPersona = userPersona
            ).collect { chunk ->
                accumulated += chunk
                val dataObj = JSONObject().put("chunk", chunk).put("fullText", accumulated)
                val sseMessage = "event: token\ndata: ${dataObj}\n\n"
                output.write(sseMessage.toByteArray(StandardCharsets.UTF_8))
                output.flush()
            }
        } catch (e: Exception) {
            val errObj = JSONObject().put("error", e.message ?: "Generation error")
            output.write("event: error\ndata: ${errObj}\n\n".toByteArray(StandardCharsets.UTF_8))
            output.flush()
        }

        // 4. Save character reply into phone Room DB
        val assistantOrder = userOrder + 1
        val replySwipe = chatRepository.addMessageWithSwipe(
            chatId = chatId,
            isUser = false,
            senderName = char.name,
            content = accumulated,
            orderIndex = assistantOrder
        )

        characterRepository.touchCharacter(characterId)
        DesktopServerManager.broadcastEvent("message_added", JSONObject().put("chatId", chatId).toString())
        DesktopServerManager.broadcastEvent("character_updated", JSONObject().put("id", characterId).toString())

        val doneObj = JSONObject().apply {
            put("messageId", replySwipe.messageId)
            put("swipeId", replySwipe.swipeId)
            put("content", accumulated)
        }
        output.write("event: done\ndata: ${doneObj}\n\n".toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private suspend fun handleRegenerateMessageStream(body: String, output: OutputStream) {
        val json = JSONObject(body)
        val messageId = json.getLong("messageId")
        val characterId = json.getLong("characterId")
        val chatId = json.getLong("chatId")

        val char = characterRepository.getCharacterById(characterId)
        if (char == null) {
            sendJsonResponse(output, 404, JSONObject().put("error", "Character not found").toString())
            return
        }

        val activePersonaId = settingsRepository.getActivePersonaId()
        val userPersona = characterRepository.getCharacterById(activePersonaId)
        val config = settingsRepository.getConnectionConfig()
        val genSettings = settingsRepository.getGenerationSettings()
        val systemPrompt = PromptBuilder.buildSystemPrompt(char, userPersona)

        // Get history before this message
        val allMessages = chatRepository.getMessagesWithSwipes(chatId).firstOrNull() ?: emptyList()
        val targetMsg = allMessages.find { it.message.id == messageId }
        val priorHistory = if (targetMsg != null) {
            allMessages.filter { it.message.orderIndex < targetMsg.message.orderIndex }
        } else allMessages

        // Add a new swipe in DB
        val swipeResult = chatRepository.addSwipeToMessage(messageId, "")
        chatRepository.updateMessageSwipeIndex(messageId, swipeResult.newIndex)

        sendSseHeaders(output)

        var accumulated = ""
        try {
            llmClient.streamChatCompletion(
                config = config,
                settings = genSettings,
                systemPrompt = systemPrompt,
                history = priorHistory,
                character = char,
                userPersona = userPersona
            ).collect { chunk ->
                accumulated += chunk
                val dataObj = JSONObject().put("chunk", chunk).put("fullText", accumulated)
                val sseMessage = "event: token\ndata: ${dataObj}\n\n"
                output.write(sseMessage.toByteArray(StandardCharsets.UTF_8))
                output.flush()
            }
        } catch (e: Exception) {
            val errObj = JSONObject().put("error", e.message ?: "Regeneration error")
            output.write("event: error\ndata: ${errObj}\n\n".toByteArray(StandardCharsets.UTF_8))
            output.flush()
        }

        // Update swipe content in DB
        if (swipeResult.swipeId > 0) {
            chatRepository.updateSwipeContent(swipeResult.swipeId, accumulated)
        }

        characterRepository.touchCharacter(char.id)
        DesktopServerManager.broadcastEvent("message_updated", JSONObject().put("messageId", messageId).toString())
        DesktopServerManager.broadcastEvent("character_updated", JSONObject().put("id", char.id).toString())

        val doneObj = JSONObject().apply {
            put("messageId", messageId)
            put("swipeId", swipeResult.swipeId)
            put("content", accumulated)
        }
        output.write("event: done\ndata: ${doneObj}\n\n".toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private suspend fun handleSetSwipe(body: String, output: OutputStream) {
        val json = JSONObject(body)
        val messageId = json.getLong("messageId")
        val swipeIndex = json.getInt("swipeIndex")
        chatRepository.updateMessageSwipeIndex(messageId, swipeIndex)
        DesktopServerManager.broadcastEvent("message_updated", JSONObject().put("messageId", messageId).toString())
        sendJsonResponse(output, 200, JSONObject().put("success", true).toString())
    }

    private suspend fun handleEditMessage(body: String, output: OutputStream) {
        val json = JSONObject(body)
        val swipeId = json.getLong("swipeId")
        val newContent = json.getString("content")
        chatRepository.updateSwipeContent(swipeId, newContent)
        DesktopServerManager.broadcastEvent("message_updated", JSONObject().put("swipeId", swipeId).toString())
        sendJsonResponse(output, 200, JSONObject().put("success", true).toString())
    }

    private suspend fun handleDeleteMessage(messageId: Long, output: OutputStream) {
        chatRepository.deleteMessage(messageId)
        DesktopServerManager.broadcastEvent("message_deleted", JSONObject().put("messageId", messageId).toString())
        sendJsonResponse(output, 200, JSONObject().put("success", true).toString())
    }

    private suspend fun handleChubImport(body: String, output: OutputStream) {
        try {
            val json = JSONObject(body)
            val url = json.getString("url").trim()
            val result = chubRepository.importCharacterFromUrlOrPath(url)
            result.onSuccess { char ->
                DesktopServerManager.broadcastEvent("character_added", JSONObject().put("id", char.id).toString())
                val resp = JSONObject().apply {
                    put("success", true)
                    put("id", char.id)
                    put("name", char.name)
                    put("description", char.description)
                    put("avatarUrl", "/api/characters/${char.id}/avatar")
                }
                sendJsonResponse(output, 200, resp.toString())
            }.onFailure { err ->
                sendJsonResponse(output, 400, JSONObject().put("error", err.message ?: "Import failed").toString())
            }
        } catch (e: Exception) {
            sendJsonResponse(output, 400, JSONObject().put("error", e.message).toString())
        }
    }

    private suspend fun handleGetPersonas(output: OutputStream) {
        val personas = characterRepository.getUserPersonas().firstOrNull() ?: emptyList()
        val activePersonaId = settingsRepository.getActivePersonaId()
        val array = JSONArray()
        personas.forEach { p ->
            array.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("description", p.description)
                put("isActive", p.id == activePersonaId)
            })
        }
        sendJsonResponse(output, 200, array.toString())
    }

    private suspend fun handleSetActivePersona(body: String, output: OutputStream) {
        val json = JSONObject(body)
        val personaId = json.getLong("personaId")
        settingsRepository.setActivePersonaId(personaId)
        DesktopServerManager.broadcastEvent("persona_changed", JSONObject().put("personaId", personaId).toString())
        sendJsonResponse(output, 200, JSONObject().put("success", true).toString())
    }

    private suspend fun handleGetSettings(output: OutputStream) {
        val gen = settingsRepository.getGenerationSettings()
        val conn = settingsRepository.getConnectionConfig()
        val json = JSONObject().apply {
            put("generation", JSONObject().apply {
                put("temperature", gen.temperature)
                put("topP", gen.topP)
                put("maxTokens", gen.maxTokens)
                put("repetitionPenalty", gen.repetitionPenalty)
            })
            put("connection", JSONObject().apply {
                put("provider", conn.provider)
                put("modelName", conn.modelName)
                put("friendlyName", conn.friendlyName)
            })
        }
        sendJsonResponse(output, 200, json.toString())
    }

    private suspend fun handleEventStream(output: OutputStream, socket: Socket) {
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/event-stream; charset=utf-8\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: keep-alive\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.flush()

        // Send initial heartbeat
        output.write("event: connected\ndata: {\"status\": \"ready\"}\n\n".toByteArray(StandardCharsets.UTF_8))
        output.flush()

        val job = serverScope.launch {
            DesktopServerManager.eventFlow.collect { event ->
                try {
                    val sseMsg = "event: ${event.type}\ndata: ${event.payload}\n\n"
                    output.write(sseMsg.toByteArray(StandardCharsets.UTF_8))
                    output.flush()
                } catch (_: Exception) {
                    cancel()
                }
            }
        }

        // Keep connection open until socket closes
        try {
            while (!socket.isClosed && socket.isConnected && isRunning) {
                delay(15000)
                output.write(": ping\n\n".toByteArray(StandardCharsets.UTF_8))
                output.flush()
            }
        } catch (_: Exception) {} finally {
            job.cancel()
        }
    }

    private fun sendSseHeaders(output: OutputStream) {
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/event-stream; charset=utf-8\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: keep-alive\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun sendCorsOptionsResponse(output: OutputStream) {
        val response = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With\r\n" +
                "Access-Control-Max-Age: 86400\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, json: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            201 -> "Created"
            204 -> "No Content"
            400 -> "Bad Request"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "OK"
        }
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun getMimeType(path: String): String = when {
        path.endsWith(".html", true) -> "text/html; charset=utf-8"
        path.endsWith(".css", true) -> "text/css; charset=utf-8"
        path.endsWith(".js", true) -> "application/javascript; charset=utf-8"
        path.endsWith(".json", true) -> "application/json; charset=utf-8"
        path.endsWith(".png", true) -> "image/png"
        path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
        path.endsWith(".svg", true) -> "image/svg+xml"
        path.endsWith(".webp", true) -> "image/webp"
        path.endsWith(".ico", true) -> "image/x-icon"
        else -> "application/octet-stream"
    }
}
