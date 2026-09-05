package com.example.engine

import com.example.data.model.CharacterEntity
import com.example.data.model.ChatMessageWithSwipes
import com.example.data.model.ConnectionConfig
import com.example.data.model.GenerationSettings
import com.example.util.CrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class LLMClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun streamChatCompletion(
        config: ConnectionConfig,
        settings: GenerationSettings,
        systemPrompt: String,
        history: List<ChatMessageWithSwipes>,
        character: CharacterEntity,
        userPersona: CharacterEntity?
    ): Flow<String> = flow {
        val userName = userPersona?.name ?: "User"
        val charName = character.name

        // Build request payload
        val messagesArray = JSONArray()

        // 1. System Prompt
        val systemMsg = JSONObject()
        systemMsg.put("role", "system")
        systemMsg.put("content", systemPrompt)
        messagesArray.put(systemMsg)

        // 2. Mes Example if present
        if (character.mesExample.isNotBlank()) {
            val examples = PromptBuilder.replaceMacros(
                character.mesExample,
                charName,
                userName,
                character.scenario,
                character.personality
            )
            val exampleMsg = JSONObject()
            exampleMsg.put("role", "system")
            exampleMsg.put("content", "Example Dialogues:\n$examples")
            messagesArray.put(exampleMsg)
        }

        // 3. Conversation history
        history.forEach { item ->
            val content = item.currentContent
            if (content.isNotBlank()) {
                val msg = JSONObject()
                msg.put("role", if (item.message.isUser) "user" else "assistant")
                msg.put("content", content)
                messagesArray.put(msg)
            }
        }

        val jsonBody = JSONObject()
        jsonBody.put("model", config.modelName.ifBlank { "gemini-2.5-flash" })
        jsonBody.put("messages", messagesArray)
        jsonBody.put("temperature", settings.temperature)
        jsonBody.put("top_p", settings.topP)
        jsonBody.put("max_tokens", settings.maxTokens)
        jsonBody.put("stream", settings.streamResponse)

        if (settings.frequencyPenalty != 0.0f) {
            jsonBody.put("frequency_penalty", settings.frequencyPenalty)
        }
        if (settings.presencePenalty != 0.0f) {
            jsonBody.put("presence_penalty", settings.presencePenalty)
        }

        // Reasoning controls
        if (settings.reasoningEffort != "none" && !settings.excludeReasoning) {
            jsonBody.put("reasoning_effort", settings.reasoningEffort)
        }

        var receivedAny = false
        var insideReasoning = false

        try {
            val baseUrl = config.baseUrl.trim().removeSuffix("/")
            if (baseUrl.isBlank() || (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://"))) {
                throw IllegalArgumentException("Invalid base URL: $baseUrl")
            }
            val endpoint = if (baseUrl.endsWith("/chat/completions")) {
                baseUrl
            } else {
                "$baseUrl/chat/completions"
            }

            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

            if (config.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
            }

            // Custom headers if any
            if (config.customHeaders.isNotBlank()) {
                config.customHeaders.lines().forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        requestBuilder.addHeader(parts[0].trim(), parts[1].trim())
                    }
                }
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw Exception("HTTP ${response.code}: $errBody")
            }

            val body = response.body
            if (body != null) {
                if (settings.streamResponse) {
                    val reader = BufferedReader(InputStreamReader(body.byteStream()))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line?.trim() ?: continue
                        if (trimmed.startsWith("data:")) {
                            val data = trimmed.removePrefix("data:").trim()
                            if (data == "[DONE]") break
                            try {
                                val json = JSONObject(data)
                                val choices = json.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                                    if (delta != null) {
                                        // Check for reasoning chunk
                                        val reasoningChunk = delta.optString("reasoning_content", "")
                                            .ifEmpty { delta.optString("reasoning", "") }

                                        if (reasoningChunk.isNotEmpty() && !settings.excludeReasoning) {
                                            if (!insideReasoning) {
                                                insideReasoning = true
                                                emit("<think>\n")
                                            }
                                            receivedAny = true
                                            emit(reasoningChunk)
                                        }

                                        // Regular content chunk
                                        val content = delta.optString("content", "")
                                        if (content.isNotEmpty()) {
                                            if (insideReasoning) {
                                                insideReasoning = false
                                                emit("\n</think>\n\n")
                                            }
                                            receivedAny = true
                                            emit(content)
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                    if (insideReasoning) {
                        emit("\n</think>\n\n")
                    }
                } else {
                    val fullResponse = body.string()
                    val json = JSONObject(fullResponse)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val message = choices.getJSONObject(0).optJSONObject("message")
                        val reasoning = message?.optString("reasoning_content", "")
                            ?.ifEmpty { message.optString("reasoning", "") } ?: ""
                        val content = message?.optString("content", "") ?: ""

                        var fullText = ""
                        if (reasoning.isNotEmpty() && !settings.excludeReasoning) {
                            fullText += "<think>\n$reasoning\n</think>\n\n"
                        }
                        fullText += content
                        if (fullText.isNotEmpty()) {
                            receivedAny = true
                            emit(fullText)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            CrashLogger.logWarning("LLMClient", "Generation error (falling back to interactive simulation): ${e.message}", e)
            if (!receivedAny) {
                // Interactive fallback simulation
                val fallbackTokens = generateInteractiveSimulation(character, userName, history)
                for (token in fallbackTokens) {
                    emit(token)
                    delay(40)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Dynamically fetches the list of available models from the provider endpoint.
     */
    suspend fun fetchAvailableModels(config: ConnectionConfig): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = if (config.modelEndpoint.isNotBlank()) {
                config.modelEndpoint.trim()
            } else {
                val base = config.baseUrl.trim().removeSuffix("/")
                if (config.provider == "ollama") {
                    if (base.endsWith("/v1")) base.removeSuffix("/v1") + "/api/tags" else "$base/api/tags"
                } else if (base.endsWith("/chat/completions")) {
                    base.removeSuffix("/chat/completions") + "/models"
                } else {
                    "$base/models"
                }
            }

            val requestBuilder = Request.Builder().url(endpoint).get()
            if (config.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
            }

            if (config.customHeaders.isNotBlank()) {
                config.customHeaders.lines().forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        requestBuilder.addHeader(parts[0].trim(), parts[1].trim())
                    }
                }
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: ""
                return@withContext Result.failure(Exception("HTTP ${response.code}: $err"))
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val modelList = mutableListOf<String>()

            // 1. OpenAI / Gemini / OpenRouter standard: { "data": [ { "id": "model-id" } ] }
            val dataArr = json.optJSONArray("data")
            if (dataArr != null) {
                for (i in 0 until dataArr.length()) {
                    val obj = dataArr.optJSONObject(i)
                    val id = obj?.optString("id", "") ?: ""
                    if (id.isNotBlank()) modelList.add(id)
                }
            }

            // 2. Ollama format: { "models": [ { "name": "llama3:8b" } ] }
            val modelsArr = json.optJSONArray("models")
            if (modelsArr != null) {
                for (i in 0 until modelsArr.length()) {
                    val obj = modelsArr.optJSONObject(i)
                    val name = obj?.optString("name", "") ?: obj?.optString("id", "") ?: ""
                    if (name.isNotBlank()) modelList.add(name)
                }
            }

            if (modelList.isEmpty()) {
                // Return common defaults if empty
                return@withContext Result.success(listOf("gemini-2.5-flash", "gemini-2.5-pro", "gpt-4o", "gpt-4o-mini", "claude-3.5-sonnet"))
            }

            Result.success(modelList.sorted())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(config: ConnectionConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = config.baseUrl.trim().removeSuffix("/")
            val endpoint = if (baseUrl.endsWith("/chat/completions")) {
                baseUrl
            } else {
                "$baseUrl/chat/completions"
            }

            val jsonBody = JSONObject().apply {
                put("model", config.modelName.ifBlank { "gemini-2.5-flash" })
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Respond with 'Connected' only.")
                    })
                })
                put("max_tokens", 10)
                put("stream", false)
            }

            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))

            if (config.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                Result.success("Connection successful! (HTTP ${response.code})")
            } else {
                val err = response.body?.string() ?: ""
                Result.failure(Exception("HTTP ${response.code}: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateInteractiveSimulation(
        character: CharacterEntity,
        userName: String,
        history: List<ChatMessageWithSwipes>
    ): List<String> {
        val lastUserMsg = history.lastOrNull { it.message.isUser }?.currentContent ?: ""
        val charName = character.name

        val simulatedText = when {
            character.name.contains("Aria", ignoreCase = true) -> {
                "*Aria adjusts her amber visor, a subtle smirk playing on her lips as she leans closer into the neon glow.*\n\n" +
                        "\"You think fast on your feet, $userName. I like that. Let's see if your reflexes match your confidence once the grid goes dark.\""
            }
            character.name.contains("Lyra", ignoreCase = true) -> {
                "*Lyra weaves glowing starlight between her fingertips, the celestial runes orbiting with a gentle chime.*\n\n" +
                        "\"The tides of fate ripple in response to your words, $userName. Listen carefully to the silence between the stars; therein lies our path.\""
            }
            character.name.contains("ECHO", ignoreCase = true) -> {
                "*ECHO's holographic matrix pulses with an agreeable soft cerulean hue as the navigation buffers update.*\n\n" +
                        "\"Analysis complete, $userName. Calculating trajectory now. Sub-engines standing by for your directive.\""
            }
            else -> {
                "*$charName glances over, considering your words thoughtfully.*\n\n" +
                        "\"I hear you, $userName. What do you suppose we should do next?\""
            }
        }

        return simulatedText.split(" ").mapIndexed { index, word ->
            if (index == 0) word else " $word"
        }
    }
}
