package com.example.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GenerationSettings
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplerSettingsScreen(
    settingsRepository: SettingsRepository,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var temperature by remember { mutableFloatStateOf(0.8f) }
    var topP by remember { mutableFloatStateOf(0.95f) }
    var topK by remember { mutableIntStateOf(40) }
    var repetitionPenalty by remember { mutableFloatStateOf(1.1f) }
    var maxTokens by remember { mutableIntStateOf(512) }
    var streamResponse by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val s = settingsRepository.getGenerationSettings()
        temperature = s.temperature
        topP = s.topP
        topK = s.topK
        repetitionPenalty = s.repetitionPenalty
        maxTokens = s.maxTokens
        streamResponse = s.streamResponse
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Sampler Settings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("sampler_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                settingsRepository.saveGenerationSettings(
                                    GenerationSettings(
                                        temperature = temperature,
                                        topP = topP,
                                        topK = topK,
                                        repetitionPenalty = repetitionPenalty,
                                        maxTokens = maxTokens,
                                        streamResponse = streamResponse
                                    )
                                )
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("save_sampler_button")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Temperature
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Temperature", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(String.format("%.2f", temperature), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0.0f..2.0f
                )
                Text(
                    "Controls randomness. Lower is more predictable, higher is more creative.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Top P
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Top P (Nucleus Sampling)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(String.format("%.2f", topP), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = topP,
                    onValueChange = { topP = it },
                    valueRange = 0.0f..1.0f
                )
                Text(
                    "Limits the pool of candidate tokens based on cumulative probability.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Top K
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Top K", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("$topK", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = topK.toFloat(),
                    onValueChange = { topK = it.roundToInt() },
                    valueRange = 1f..100f
                )
            }

            // Repetition Penalty
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Repetition Penalty", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(String.format("%.2f", repetitionPenalty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = repetitionPenalty,
                    onValueChange = { repetitionPenalty = it },
                    valueRange = 1.0f..1.5f
                )
                Text(
                    "Penalizes repeated phrases to reduce looping in roleplay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Max Tokens
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Max Output Tokens", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("$maxTokens", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = { maxTokens = (it / 32).roundToInt() * 32 },
                    valueRange = 64f..2048f
                )
            }

            // Stream response toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Stream Responses", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Display tokens dynamically as they are generated.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = streamResponse,
                    onCheckedChange = { streamResponse = it }
                )
            }
        }
    }
}
