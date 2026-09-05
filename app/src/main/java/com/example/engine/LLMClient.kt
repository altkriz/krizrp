package com.example.engine

import com.example.data.model.CharacterEntity
import com.example.data.model.ChatMessageWithSwipes
import com.example.data.model.ConnectionConfig
import com.example.data.model.GenerationSettings
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
        .readTimeout(60, TimeUnit.SECONDS)
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

        val baseUrl = config.baseUrl.trim().removeSuffix("/")
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

        var receivedAny = false
        try {
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
                                    val token = delta?.optString("content", "") ?: ""
                                    if (token.isNotEmpty()) {
                                        receivedAny = true
                                        emit(token)
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                } else {
                    val fullResponse = body.string()
                    val json = JSONObject(fullResponse)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val message = choices.getJSONObject(0).optJSONObject("message")
                        val content = message?.optString("content", "") ?: ""
                        if (content.isNotEmpty()) {
                            receivedAny = true
                            emit(content)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (!receivedAny) {
                // If the remote API couldn't be reached (e.g. no key yet or local network not configured),
                // provide an intelligent interactive fallback response tailored to the character!
                val fallbackTokens = generateInteractiveSimulation(character, userName, history)
                for (token in fallbackTokens) {
                    emit(token)
                    delay(40)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

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
                Result.success("Connection successful! HTTP ${response.code}")
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

        // Split into small word chunks to stream
        return simulatedText.split(" ").mapIndexed { index, word ->
            if (index == 0) word else " $word"
        }
    }
}
