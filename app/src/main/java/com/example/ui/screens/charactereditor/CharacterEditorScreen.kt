package com.example.ui.screens.charactereditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CharacterEntity
import com.example.data.repository.CharacterRepository
import com.example.ui.components.AvatarView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditorScreen(
    characterId: Long,
    characterRepository: CharacterRepository,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var character by remember { mutableStateOf<CharacterEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var scenario by remember { mutableStateOf("") }
    var firstMes by remember { mutableStateOf("") }
    var mesExample by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var postHistoryInstructions by remember { mutableStateOf("") }
    var creatorNotes by remember { mutableStateOf("") }
    var creator by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var avatarColor by remember { mutableStateOf(0xFF7B1FA2) }

    val colorOptions = listOf(
        0xFF7B1FA2, 0xFF6750A4, 0xFF3F51B5, 0xFF00897B,
        0xFF1976D2, 0xFF0097A7, 0xFFE64A19, 0xFFC2185B, 0xFF5D4037
    )

    LaunchedEffect(characterId) {
        if (characterId > 0) {
            val char = characterRepository.getCharacterById(characterId)
            if (char != null) {
                character = char
                name = char.name
                description = char.description
                personality = char.personality
                scenario = char.scenario
                firstMes = char.firstMes
                mesExample = char.mesExample
                systemPrompt = char.systemPrompt
                postHistoryInstructions = char.postHistoryInstructions
                creatorNotes = char.creatorNotes
                creator = char.creator
                tags = char.tags
                avatarColor = char.avatarColor
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (characterId > 0) "Edit Character" else "New Character",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (characterId > 0) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    characterRepository.deleteCharacter(characterId)
                                    onNavigateBack()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (name.isBlank()) return@IconButton
                            coroutineScope.launch {
                                val entity = (character ?: CharacterEntity()).copy(
                                    id = if (characterId > 0) characterId else 0L,
                                    type = "character",
                                    name = name.trim(),
                                    description = description.trim(),
                                    personality = personality.trim(),
                                    scenario = scenario.trim(),
                                    firstMes = firstMes.trim(),
                                    mesExample = mesExample.trim(),
                                    systemPrompt = systemPrompt.trim(),
                                    postHistoryInstructions = postHistoryInstructions.trim(),
                                    creatorNotes = creatorNotes.trim(),
                                    creator = creator.trim(),
                                    tags = tags.trim(),
                                    avatarColor = avatarColor
                                )
                                characterRepository.saveCharacter(entity)
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("save_character_button")
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar preview & color picker
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AvatarView(
                        name = name.ifBlank { "?" },
                        avatarColor = avatarColor,
                        size = 64.dp,
                        shapeRadius = 16.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Avatar Color",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            colorOptions.forEach { colorVal ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorVal))
                                        .then(
                                            if (avatarColor == colorVal) Modifier.border(2.dp, Color.White, CircleShape)
                                            else Modifier
                                        )
                                        .clickable { avatarColor = colorVal }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Character Name *") },
                    placeholder = { Text("e.g. Aria Blake") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("character_name_input")
                )

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("Cyberpunk, Sci-Fi, Roleplay") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Brief background of the character...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = personality,
                    onValueChange = { personality = it },
                    label = { Text("Personality") },
                    placeholder = { Text("Personality traits, quirks, demeanor...") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = scenario,
                    onValueChange = { scenario = it },
                    label = { Text("Scenario / Setting") },
                    placeholder = { Text("{{user}} meets {{char}} in...") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = firstMes,
                    onValueChange = { firstMes = it },
                    label = { Text("First Message (Greeting)") },
                    placeholder = { Text("*The character approaches you...* \"Greetings!\"") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = mesExample,
                    onValueChange = { mesExample = it },
                    label = { Text("Message Examples") },
                    placeholder = { Text("<START>\n{{user}}: Hello\n{{char}}: *smiles* Welcome.") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("Custom System Prompt") },
                    placeholder = { Text("Roleplay instructions specific to this character...") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = creator,
                        onValueChange = { creator = it },
                        label = { Text("Creator") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = creatorNotes,
                        onValueChange = { creatorNotes = it },
                        label = { Text("Notes / Version") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
