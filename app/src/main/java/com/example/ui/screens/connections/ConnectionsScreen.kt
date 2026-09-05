package com.example.ui.screens.connections

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionConfig
import com.example.data.repository.SettingsRepository
import com.example.engine.LLMClient
import com.example.ui.components.TagChip
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    settingsRepository: SettingsRepository,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val llmClient = remember { LLMClient() }

    var provider by remember { mutableStateOf("gemini") }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://generativelanguage.googleapis.com/v1beta/openai/") }
    var modelName by remember { mutableStateOf("gemini-2.5-flash") }
    var customHeaders by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testIsSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val cfg = settingsRepository.getConnectionConfig()
        provider = cfg.provider
        apiKey = cfg.apiKey
        baseUrl = cfg.baseUrl
        modelName = cfg.modelName
        customHeaders = cfg.customHeaders
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("API Connections", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("connections_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                settingsRepository.saveConnectionConfig(
                                    ConnectionConfig(
                                        provider = provider,
                                        apiKey = apiKey,
                                        baseUrl = baseUrl,
                                        modelName = modelName,
                                        customHeaders = customHeaders
                                    )
                                )
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("save_connection_button")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Preset Providers",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    "Google Gemini" to ("gemini" to Triple(
                        "https://generativelanguage.googleapis.com/v1beta/openai/",
                        "gemini-2.5-flash",
                        ""
                    )),
                    "OpenAI" to ("openai" to Triple(
                        "https://api.openai.com/v1/",
                        "gpt-4o-mini",
                        ""
                    )),
                    "Local Ollama" to ("ollama" to Triple(
                        "http://10.0.2.2:11434/v1/",
                        "llama3:8b",
                        ""
                    )),
                    "KoboldCPP" to ("kobold" to Triple(
                        "http://10.0.2.2:5001/v1/",
                        "kobold",
                        ""
                    )),
                    "OpenRouter" to ("openrouter" to Triple(
                        "https://openrouter.ai/api/v1/",
                        "google/gemini-2.5-flash",
                        ""
                    ))
                )

                presets.forEach { (label, data) ->
                    val (pKey, details) = data
                    TagChip(
                        tag = label,
                        isSelected = provider == pKey,
                        onClick = {
                            provider = pKey
                            baseUrl = details.first
                            modelName = details.second
                        }
                    )
                }
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL / API Endpoint *") },
                placeholder = { Text("https://api.example.com/v1/") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model Name *") },
                placeholder = { Text("gemini-2.5-flash / gpt-4o-mini") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("Enter your API key or leave blank for local") },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showApiKey) "Hide API Key" else "Show API Key"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = customHeaders,
                onValueChange = { customHeaders = it },
                label = { Text("Custom HTTP Headers (Optional)") },
                placeholder = { Text("X-Title: KrizRP\nHTTP-Referer: https://krizrp.app") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    isTesting = true
                    testResult = null
                    coroutineScope.launch {
                        val cfg = ConnectionConfig(
                            provider = provider,
                            apiKey = apiKey,
                            baseUrl = baseUrl,
                            modelName = modelName,
                            customHeaders = customHeaders
                        )
                        val res = llmClient.testConnection(cfg)
                        isTesting = false
                        res.fold(
                            onSuccess = { msg ->
                                testResult = msg
                                testIsSuccess = true
                            },
                            onFailure = { err ->
                                testResult = err.message ?: "Connection failed"
                                testIsSuccess = false
                            }
                        )
                    }
                },
                enabled = !isTesting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing Endpoint...")
                } else {
                    Icon(imageVector = Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Connection")
                }
            }

            if (testResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (testIsSuccess)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = testResult!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testIsSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
