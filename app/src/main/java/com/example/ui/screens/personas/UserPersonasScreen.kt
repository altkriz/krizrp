package com.example.ui.screens.personas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.example.data.repository.SettingsRepository
import com.example.ui.components.AvatarView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPersonasScreen(
    characterRepository: CharacterRepository,
    settingsRepository: SettingsRepository,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val personas by characterRepository.getUserPersonas().collectAsState(initial = emptyList())
    var activePersonaId by remember { mutableStateOf(1L) }

    var editingPersona by remember { mutableStateOf<CharacterEntity?>(null) }
    var isNewPersona by remember { mutableStateOf(false) }
    var personaNameInput by remember { mutableStateOf("") }
    var personaDescInput by remember { mutableStateOf("") }
    var personaColorInput by remember { mutableStateOf(0xFF3F51B5) }

    val colorOptions = listOf(
        0xFF3F51B5, 0xFF00897B, 0xFF1976D2, 0xFF7B1FA2,
        0xFFE64A19, 0xFFC2185B, 0xFF455A64, 0xFF5D4037
    )

    LaunchedEffect(Unit) {
        activePersonaId = settingsRepository.getActivePersonaId()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("User Personas", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("personas_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingPersona = CharacterEntity(type = "user", name = "", description = "")
                    isNewPersona = true
                    personaNameInput = ""
                    personaDescInput = ""
                    personaColorInput = 0xFF3F51B5
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_persona_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Persona")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Select which persona you want characters to address you as in your roleplay conversations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(personas, key = { it.id }) { persona ->
                val isActive = persona.id == activePersonaId

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            coroutineScope.launch {
                                activePersonaId = persona.id
                                settingsRepository.setActivePersonaId(persona.id)
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarView(
                            name = persona.name,
                            avatarColor = persona.avatarColor,
                            size = 48.dp,
                            shapeRadius = 12.dp
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = persona.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                if (isActive) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (persona.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = persona.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                editingPersona = persona
                                isNewPersona = false
                                personaNameInput = persona.name
                                personaDescInput = persona.description
                                personaColorInput = persona.avatarColor
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                        }

                        if (personas.size > 1 && !isActive) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        characterRepository.deleteCharacter(persona.id)
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
                    }
                }
            }
        }
    }

    if (editingPersona != null) {
        AlertDialog(
            onDismissRequest = { editingPersona = null },
            title = { Text(if (isNewPersona) "New Persona" else "Edit Persona") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = personaNameInput,
                        onValueChange = { personaNameInput = it },
                        label = { Text("Persona Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = personaDescInput,
                        onValueChange = { personaDescInput = it },
                        label = { Text("Description & Roleplay Background") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Avatar Color", style = MaterialTheme.typography.labelSmall)
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
                                        if (personaColorInput == colorVal) Modifier.border(2.dp, Color.White, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { personaColorInput = colorVal }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (personaNameInput.isNotBlank()) {
                            coroutineScope.launch {
                                val toSave = (editingPersona ?: CharacterEntity()).copy(
                                    id = if (isNewPersona) 0L else editingPersona!!.id,
                                    type = "user",
                                    name = personaNameInput.trim(),
                                    description = personaDescInput.trim(),
                                    avatarColor = personaColorInput
                                )
                                val savedId = characterRepository.saveCharacter(toSave)
                                if (isNewPersona) {
                                    activePersonaId = savedId
                                    settingsRepository.setActivePersonaId(savedId)
                                }
                                editingPersona = null
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPersona = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
