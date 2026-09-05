package com.example.engine

import com.example.data.model.CharacterEntity
import com.example.data.model.ChatMessageWithSwipes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PromptBuilder {
    fun replaceMacros(
        text: String,
        charName: String,
        userName: String,
        scenario: String = "",
        personality: String = ""
    ): String {
        val now = Date()
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)

        return text
            .replace("{{char}}", charName, ignoreCase = true)
            .replace("<CHAR>", charName, ignoreCase = true)
            .replace("{{user}}", userName, ignoreCase = true)
            .replace("<USER>", userName, ignoreCase = true)
            .replace("{{time}}", timeStr, ignoreCase = true)
            .replace("{{date}}", dateStr, ignoreCase = true)
            .replace("{{scenario}}", scenario, ignoreCase = true)
            .replace("{{personality}}", personality, ignoreCase = true)
    }

    fun buildSystemPrompt(
        character: CharacterEntity,
        userPersona: CharacterEntity?
    ): String {
        val charName = character.name
        val userName = userPersona?.name ?: "User"

        val sb = StringBuilder()
        sb.append("You are to roleplay as the following character in an immersive interactive conversation.\n")
        sb.append("Character Name: ").append(charName).append("\n")

        if (character.description.isNotBlank()) {
            sb.append("Description: ").append(replaceMacros(character.description, charName, userName)).append("\n")
        }
        if (character.personality.isNotBlank()) {
            sb.append("Personality: ").append(replaceMacros(character.personality, charName, userName)).append("\n")
        }
        if (character.scenario.isNotBlank()) {
            sb.append("Current Scenario / Setting: ").append(replaceMacros(character.scenario, charName, userName)).append("\n")
        }
        if (character.systemPrompt.isNotBlank()) {
            sb.append("Directives: ").append(replaceMacros(character.systemPrompt, charName, userName)).append("\n")
        }
        if (character.postHistoryInstructions.isNotBlank()) {
            sb.append("Style instructions: ").append(replaceMacros(character.postHistoryInstructions, charName, userName)).append("\n")
        }

        sb.append("\nRoleplay Guidelines:\n")
        sb.append("- Stay in character at all times as ").append(charName).append(".\n")
        sb.append("- Portray feelings, physical actions, and narrative thoughts inside asterisks (e.g. *smiles gently*).\n")
        sb.append("- Keep speech in quotation marks.\n")
        sb.append("- Respond directly to the user (").append(userName).append(").\n")

        return sb.toString()
    }
}
