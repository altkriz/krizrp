package com.example.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var minP by remember { mutableFloatStateOf(0.05f) }
    var repetitionPenalty by remember { mutableFloatStateOf(1.1f) }
    var frequencyPenalty by remember { mutableFloatStateOf(0.0f) }
    var presencePenalty by remember { mutableFloatStateOf(0.0f) }
    var maxTokens by remember { mutableIntStateOf(512) }
    var contextLength by remember { mutableIntStateOf(4096) }
    var streamResponse by remember { mutableStateOf(true) }

    // Reasoning parameters
    var reasoningEffort by remember { mutableStateOf("medium") }
    var reasoningMaxTokens by remember { mutableIntStateOf(1024) }
    var excludeReasoning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val s = settingsRepository.getGenerationSettings()
        temperature = s.temperature
        topP = s.topP
        topK = s.topK
        minP = s.minP
        repetitionPenalty = s.repetitionPenalty
        frequencyPenalty = s.frequencyPenalty
        presencePenalty = s.presencePenalty
        maxTokens = s.maxTokens
        contextLength = s.contextLength
        streamResponse = s.streamResponse
        reasoningEffort = s.reasoningEffort
        reasoningMaxTokens = s.reasoningMaxTokens
        excludeReasoning = s.excludeReasoning
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Sampler & Reasoning",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
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
                                        minP = minP,
                                        repetitionPenalty = repetitionPenalty,
                                        frequencyPenalty = frequencyPenalty,
                                        presencePenalty = presencePenalty,
                                        maxTokens = maxTokens,
                                        contextLength = contextLength,
                                        streamResponse = streamResponse,
                                        reasoningEffort = reasoningEffort,
                                        reasoningMaxTokens = reasoningMaxTokens,
                                        excludeReasoning = excludeReasoning
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Section 1: Standard Samplers
            Text(
                text = "Sampling Parameters",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

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
                    Text("Top P (Nucleus)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(String.format("%.2f", topP), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = topP,
                    onValueChange = { topP = it },
                    valueRange = 0.0f..1.0f
                )
            }

            // Min P
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Min P", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(String.format("%.2f", minP), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = minP,
                    onValueChange = { minP = it },
                    valueRange = 0.0f..0.5f
                )
                Text(
                    "Sets minimum probability threshold relative to most likely token.",
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
            }

            // Frequency Penalty
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Frequency Penalty", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(String.format("%.2f", frequencyPenalty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = frequencyPenalty,
                    onValueChange = { frequencyPenalty = it },
                    valueRange = -2.0f..2.0f
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
                    valueRange = 64f..4096f
                )
            }

            HorizontalDivider()

            // Section 2: Reasoning & Chain of Thought
            Text(
                text = "Reasoning & Thought (o1, o3, R1, Thinking Models)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Reasoning Effort:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("none" to "None", "low" to "Low", "medium" to "Medium", "high" to "High").forEach { (effort, label) ->
                            FilterChip(
                                selected = reasoningEffort == effort,
                                onClick = { reasoningEffort = effort },
                                label = { Text(label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Exclude Reasoning Tokens", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Hides the reasoning thought block from model output.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = excludeReasoning,
                            onCheckedChange = { excludeReasoning = it }
                        )
                    }
                }
            }

            HorizontalDivider()

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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
