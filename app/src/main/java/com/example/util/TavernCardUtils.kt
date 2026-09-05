package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.data.model.CharacterEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.InflaterInputStream

object TavernCardUtils {

    data class ParsedCard(
        val character: CharacterEntity,
        val avatarBitmap: Bitmap?
    )

    /**
     * Parses a Tavern V2 or V1 character card from an InputStream (PNG or JSON).
     */
    fun parseCardFromStream(inputStream: InputStream, context: Context): ParsedCard? {
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) return null

        // Check if it's a PNG file (header: 89 50 4E 47 0D 0A 1A 0A)
        val isPng = bytes.size > 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte()

        if (isPng) {
            val jsonString = extractJsonFromPng(bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (!jsonString.isNullOrBlank()) {
                val char = parseJsonToCharacter(jsonString)
                if (char != null) {
                    return ParsedCard(char, bitmap)
                }
            }
        }

        // If not PNG or no chunk found, try parsing as raw JSON text
        try {
            val jsonText = String(bytes, Charsets.UTF_8).trim()
            val char = parseJsonToCharacter(jsonText)
            if (char != null) {
                return ParsedCard(char, null)
            }
        } catch (_: Exception) {
        }

        return null
    }

    /**
     * Extracts the 'chara' text chunk from PNG bytes (both tEXt and zTXt chunks).
     */
    fun extractJsonFromPng(pngBytes: ByteArray): String? {
        if (pngBytes.size < 8) return null
        val buffer = ByteBuffer.wrap(pngBytes).order(ByteOrder.BIG_ENDIAN)

        // Skip PNG signature
        buffer.position(8)

        while (buffer.remaining() >= 8) {
            val length = buffer.int
            if (length < 0 || length > buffer.remaining() - 4) break

            val typeBytes = ByteArray(4)
            buffer.get(typeBytes)
            val chunkType = String(typeBytes, Charsets.US_ASCII)

            if (chunkType == "tEXt") {
                val data = ByteArray(length)
                buffer.get(data)
                buffer.int // Skip CRC

                val nullIndex = data.indexOf(0)
                if (nullIndex != -1) {
                    val keyword = String(data, 0, nullIndex, Charsets.ISO_8859_1)
                    if (keyword.equals("chara", ignoreCase = true) || keyword.equals("ccv3", ignoreCase = true)) {
                        val textBytes = data.copyOfRange(nullIndex + 1, data.size)
                        val rawText = String(textBytes, Charsets.UTF_8).trim()
                        return decodeIfBase64(rawText)
                    }
                }
            } else if (chunkType == "zTXt") {
                val data = ByteArray(length)
                buffer.get(data)
                buffer.int // Skip CRC

                val nullIndex = data.indexOf(0)
                if (nullIndex != -1) {
                    val keyword = String(data, 0, nullIndex, Charsets.ISO_8859_1)
                    if (keyword.equals("chara", ignoreCase = true)) {
                        // next byte is compression method (usually 0)
                        val compressedData = data.copyOfRange(nullIndex + 2, data.size)
                        try {
                            val inflater = InflaterInputStream(ByteArrayInputStream(compressedData))
                            val decompressed = inflater.readBytes()
                            val rawText = String(decompressed, Charsets.UTF_8).trim()
                            return decodeIfBase64(rawText)
                        } catch (_: Exception) {
                        }
                    }
                }
            } else {
                // Skip chunk data and CRC
                buffer.position(buffer.position() + length + 4)
                if (chunkType == "IEND") break
            }
        }
        return null
    }

    private fun decodeIfBase64(str: String): String {
        return try {
            if (str.startsWith("{")) {
                str
            } else {
                val decoded = Base64.decode(str, Base64.DEFAULT)
                String(decoded, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            str
        }
    }

    /**
     * Parses Tavern V2 or V1 JSON format to CharacterEntity.
     */
    fun parseJsonToCharacter(jsonStr: String): CharacterEntity? {
        try {
            val root = JSONObject(jsonStr)

            // Tavern V2 format: { "spec": "chara_card_v2", "data": { ... } }
            if (root.has("data") && root.optString("spec").contains("chara_card", ignoreCase = true)) {
                val data = root.getJSONObject("data")
                val tagsArray = data.optJSONArray("tags")
                val tagsList = mutableListOf<String>()
                if (tagsArray != null) {
                    for (i in 0 until tagsArray.length()) {
                        tagsList.add(tagsArray.getString(i))
                    }
                }

                val altGreetings = data.optJSONArray("alternate_greetings")?.toString() ?: "[]"

                return CharacterEntity(
                    type = "character",
                    name = data.optString("name", "Unknown Character"),
                    description = data.optString("description", ""),
                    personality = data.optString("personality", ""),
                    scenario = data.optString("scenario", ""),
                    firstMes = data.optString("first_mes", ""),
                    mesExample = data.optString("mes_example", ""),
                    systemPrompt = data.optString("system_prompt", ""),
                    postHistoryInstructions = data.optString("post_history_instructions", ""),
                    creatorNotes = data.optString("creator_notes", ""),
                    creator = data.optString("creator", ""),
                    version = data.optString("character_version", "1.0"),
                    tags = tagsList.joinToString(", "),
                    alternateGreetings = altGreetings
                )
            }

            // Tavern V1 format: { "name": ..., "description": ... }
            if (root.has("name")) {
                return CharacterEntity(
                    type = "character",
                    name = root.optString("name", "Unknown Character"),
                    description = root.optString("description", ""),
                    personality = root.optString("personality", ""),
                    scenario = root.optString("scenario", ""),
                    firstMes = root.optString("first_mes", ""),
                    mesExample = root.optString("mes_example", ""),
                    systemPrompt = root.optString("system_prompt", ""),
                    postHistoryInstructions = root.optString("post_history_instructions", ""),
                    creatorNotes = root.optString("creator_notes", ""),
                    creator = root.optString("creator", ""),
                    version = "1.0",
                    tags = "",
                    alternateGreetings = "[]"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Converts a CharacterEntity into Tavern V2 JSON.
     */
    fun characterToTavernV2Json(character: CharacterEntity): String {
        val root = JSONObject()
        root.put("spec", "chara_card_v2")
        root.put("spec_version", "2.0")

        val data = JSONObject()
        data.put("name", character.name)
        data.put("description", character.description)
        data.put("personality", character.personality)
        data.put("scenario", character.scenario)
        data.put("first_mes", character.firstMes)
        data.put("mes_example", character.mesExample)
        data.put("system_prompt", character.systemPrompt)
        data.put("post_history_instructions", character.postHistoryInstructions)
        data.put("creator_notes", character.creatorNotes)
        data.put("creator", character.creator)
        data.put("character_version", character.version)

        val tagsJson = JSONArray()
        character.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            tagsJson.put(it)
        }
        data.put("tags", tagsJson)

        try {
            data.put("alternate_greetings", JSONArray(character.alternateGreetings))
        } catch (_: Exception) {
            data.put("alternate_greetings", JSONArray())
        }

        root.put("data", data)
        return root.toString(2)
    }

    /**
     * Exports character as a Tavern V2 PNG card with embedded 'chara' tEXt chunk.
     */
    fun exportToPngCard(bitmap: Bitmap, character: CharacterEntity): ByteArray {
        val jsonStr = characterToTavernV2Json(character)
        val base64Json = Base64.encodeToString(jsonStr.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        val pngStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngStream)
        val rawPng = pngStream.toByteArray()

        return insertCharaChunkIntoPng(rawPng, base64Json)
    }

    private fun insertCharaChunkIntoPng(pngBytes: ByteArray, base64Data: String): ByteArray {
        val out = ByteArrayOutputStream()

        // Write header
        out.write(pngBytes, 0, 8)

        // Create the 'tEXt' chunk for chara
        val keyword = "chara\u0000".toByteArray(Charsets.ISO_8859_1)
        val textData = base64Data.toByteArray(Charsets.ISO_8859_1)
        val chunkData = ByteArray(keyword.size + textData.size)
        System.arraycopy(keyword, 0, chunkData, 0, keyword.size)
        System.arraycopy(textData, 0, chunkData, keyword.size, textData.size)

        // Compute chunk length & CRC
        val chunkLength = chunkData.size
        val chunkType = "tEXt".toByteArray(Charsets.US_ASCII)

        val crc = CRC32()
        crc.update(chunkType)
        crc.update(chunkData)
        val crcValue = crc.value.toInt()

        val lengthBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(chunkLength).array()
        val crcBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crcValue).array()

        // We insert the tEXt chunk right after IHDR chunk
        var pos = 8
        var inserted = false

        while (pos < pngBytes.size) {
            val length = ByteBuffer.wrap(pngBytes, pos, 4).order(ByteOrder.BIG_ENDIAN).int
            val type = String(pngBytes, pos + 4, 4, Charsets.US_ASCII)
            val fullChunkSize = 4 + 4 + length + 4

            out.write(pngBytes, pos, fullChunkSize)
            pos += fullChunkSize

            if (type == "IHDR" && !inserted) {
                // Write our chara tEXt chunk
                out.write(lengthBytes)
                out.write(chunkType)
                out.write(chunkData)
                out.write(crcBytes)
                inserted = true
            }
        }

        return out.toByteArray()
    }
}
