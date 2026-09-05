package com.example.ui.screens.connections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionConfig
import com.example.data.model.GenerationSettings
import com.example.data.repository.SettingsRepository
import com.example.engine.LLMClient
import com.example.ui.components.ApiProviderIcon
import com.example.ui.theme.AccentGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    settingsRepository: SettingsRepository,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val llmClient = remember { LLMClient() }

    var allConnections by remember { mutableStateOf<List<ConnectionConfig>>(emptyList()) }
    var selectedConnectionId by remember { mutableStateOf("") }

    // Active editing fields
    var friendlyName by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("gemini") }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://generativelanguage.googleapis.com/v1beta/openai/") }
    var modelName by remember { mutableStateOf("gemini-1.5-flash") }
    var modelEndpoint by remember { mutableStateOf("") }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var customHeaders by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }

    // Generation / Reasoning settings
    var generationSettings by remember { mutableStateOf(GenerationSettings()) }

    // Model dropdown expansion & filter
    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    var modelSearchQuery by remember { mutableStateOf("") }
    var isFetchingModels by remember { mutableStateOf(false) }
    var modelFetchStatus by remember { mutableStateOf<String?>(null) }

    // Connection test state
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testIsSuccess by remember { mutableStateOf(false) }

    fun loadConnectionIntoForm(conn: ConnectionConfig) {
        selectedConnectionId = conn.id
        friendlyName = conn.friendlyName
        provider = conn.provider
        apiKey = conn.apiKey
        baseUrl = conn.baseUrl
        modelName = conn.modelName
        modelEndpoint = conn.modelEndpoint
        availableModels = conn.availableModels
        customHeaders = conn.customHeaders
        testResult = null
        modelFetchStatus = null
    }

    LaunchedEffect(Unit) {
        val connections = settingsRepository.getAllConnections()
        allConnections = connections
        val activeConn = connections.firstOrNull { it.active } ?: connections.firstOrNull()
        if (activeConn != null) {
            loadConnectionIntoForm(activeConn)
        }
        generationSettings = settingsRepository.getGenerationSettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "API Manager",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
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
                    // Add Profile action
                    IconButton(
                        onClick = {
                            val newConn = ConnectionConfig(
                                id = java.util.UUID.randomUUID().toString(),
                                friendlyName = "New Provider Profile",
                                provider = "gemini",
                                apiKey = "",
                                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
                                modelName = "gemini-1.5-flash",
                                active = false
                            )
                            coroutineScope.launch {
                                settingsRepository.saveConnectionConfig(newConn)
                                allConnections = settingsRepository.getAllConnections()
                                loadConnectionIntoForm(newConn)
                            }
                        },
                        modifier = Modifier.testTag("add_connection_top_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Profile")
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
            // Section 1: Connection Profiles Header & List (Matching Image 3)
            Text(
                text = "Connection Profiles",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                allConnections.forEach { conn ->
                    val isSelected = conn.id == selectedConnectionId
                    var profileMenuExpanded by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { loadConnectionIntoForm(conn) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // API Provider Icon with authentic branding
                            ApiProviderIcon(
                                provider = conn.provider.ifBlank { conn.friendlyName },
                                size = 42.dp
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conn.friendlyName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = conn.modelName.ifBlank { conn.baseUrl },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (conn.active) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AccentGreen.copy(alpha = 0.15f))
                                        .border(1.dp, AccentGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = AccentGreen
                                    )
                                }
                            }

                            Box {
                                IconButton(
                                    onClick = { profileMenuExpanded = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = profileMenuExpanded,
                                    onDismissRequest = { profileMenuExpanded = false }
                                ) {
                                    if (!conn.active) {
                                        DropdownMenuItem(
                                            text = { Text("Set as Active") },
                                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen) },
                                            onClick = {
                                                profileMenuExpanded = false
                                                coroutineScope.launch {
                                                    settingsRepository.setActiveConnection(conn.id)
                                                    allConnections = settingsRepository.getAllConnections()
                                                }
                                            }
                                        )
                                    }
                                    if (allConnections.size > 1) {
                                        DropdownMenuItem(
                                            text = { Text("Delete Profile", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                profileMenuExpanded = false
                                                coroutineScope.launch {
                                                    settingsRepository.deleteConnection(conn.id)
                                                    allConnections = settingsRepository.getAllConnections()
                                                    val next = allConnections.firstOrNull()
                                                    if (next != null) loadConnectionIntoForm(next)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Provider Presets (Matching Image 3)
            Text(
                text = "Provider Presets",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    "Google AI Studio" to ("gemini" to Triple(
                        "https://generativelanguage.googleapis.com/v1beta/openai/",
                        "gemini-1.5-flash",
                        listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-2.0-flash", "gemini-2.5-flash")
                    )),
                    "OpenAI" to ("openai" to Triple(
                        "https://api.openai.com/v1/",
                        "gpt-4o-mini",
                        listOf("gpt-4o-mini", "gpt-4o", "o1-mini", "o3-mini")
                    )),
                    "Claude" to ("claude" to Triple(
                        "https://api.anthropic.com/v1/",
                        "claude-3-5-sonnet-20241022",
                        listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022")
                    )),
                    "OpenRouter" to ("openrouter" to Triple(
                        "https://openrouter.ai/api/v1/",
                        "google/gemini-2.5-flash",
                        listOf("google/gemini-2.5-flash", "deepseek/deepseek-r1", "anthropic/claude-3.5-sonnet")
                    )),
                    "Ollama" to ("ollama" to Triple(
                        "http://10.0.2.2:11434/v1/",
                        "llama3:8b",
                        listOf("llama3:8b", "mistral:7b", "qwen2.5:7b")
                    ))
                )

                presets.forEach { (label, data) ->
                    val (pKey, details) = data
                    val isSelected = provider == pKey

                    Surface(
                        onClick = {
                            provider = pKey
                            friendlyName = label
                            baseUrl = details.first
                            modelName = details.second
                            availableModels = details.third
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section 3: Profile Configuration Details Form (Matching Image 3)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = friendlyName,
                        onValueChange = { friendlyName = it },
                        label = { Text("Profile Name") },
                        placeholder = { Text("e.g. Google Gemini Flash") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL / API Endpoint *") },
                        placeholder = { Text("https://generativelanguage.googleapis.com/v1beta/openai/") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("Enter API key") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
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

                    // Model Selector Header with "Fetch Models" Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Model Selector",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Button(
                            onClick = {
                                isFetchingModels = true
                                modelFetchStatus = null
                                coroutineScope.launch {
                                    val endpointToUse = modelEndpoint.ifBlank {
                                        when (provider) {
                                            "openrouter" -> "https://openrouter.ai/api/v1/models"
                                            "gemini" -> "https://generativelanguage.googleapis.com/v1beta/openai/models"
                                            "openai" -> "https://api.openai.com/v1/models"
                                            "ollama" -> "${baseUrl.trimEnd('/')}/tags"
                                            else -> "${baseUrl.trimEnd('/')}/models"
                                        }
                                    }

                                    val tempConfig = ConnectionConfig(
                                        provider = provider,
                                        apiKey = apiKey,
                                        baseUrl = baseUrl,
                                        modelEndpoint = endpointToUse,
                                        customHeaders = customHeaders
                                    )
                                    val result = llmClient.fetchAvailableModels(tempConfig)
                                    isFetchingModels = false
                                    result.fold(
                                        onSuccess = { fetched ->
                                            if (fetched.isNotEmpty()) {
                                                availableModels = fetched
                                                modelFetchStatus = "Found ${fetched.size} models"
                                                if (modelName.isBlank() || modelName !in fetched) {
                                                    modelName = fetched.first()
                                                }
                                            } else {
                                                modelFetchStatus = "No models returned. Enter model name manually."
                                            }
                                        },
                                        onFailure = { err ->
                                            modelFetchStatus = "Could not fetch models: ${err.message ?: "Unknown error"}"
                                        }
                                    )
                                }
                            },
                            enabled = !isFetchingModels,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Fetching...", fontSize = 12.sp)
                            } else {
                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Fetch Models", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (modelFetchStatus != null) {
                        Text(
                            text = modelFetchStatus!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = if (modelFetchStatus!!.startsWith("Found"))
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }

                    // Model Name Input with Dropdown Anchor
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = modelName,
                            onValueChange = {
                                modelName = it
                                modelSearchQuery = it
                            },
                            label = { Text("Model Name *") },
                            placeholder = { Text("Choose or type model name") },
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                IconButton(onClick = { isModelDropdownExpanded = !isModelDropdownExpanded }) {
                                    Icon(
                                        imageVector = if (isModelDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = "Show models"
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("model_input")
                        )

                        DropdownMenu(
                            expanded = isModelDropdownExpanded,
                            onDismissRequest = { isModelDropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .heightIn(max = 280.dp)
                        ) {
                            val filtered = if (modelSearchQuery.isBlank()) {
                                availableModels
                            } else {
                                availableModels.filter { it.contains(modelSearchQuery, ignoreCase = true) }
                            }

                            if (filtered.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Enter model name manually") },
                                    onClick = { isModelDropdownExpanded = false }
                                )
                            } else {
                                filtered.forEach { mName ->
                                    DropdownMenuItem(
                                        text = { Text(mName) },
                                        leadingIcon = if (mName == modelName) {
                                            { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                        } else null,
                                        onClick = {
                                            modelName = mName
                                            isModelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Prominent Test Connection Button (Matching Image 3)
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing Connection...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Connection", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (testIsSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (testIsSuccess) AccentGreen else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = testResult!!,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = if (testIsSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // Save Profile Button
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val currentActive = allConnections.firstOrNull { it.id == selectedConnectionId }?.active ?: true
                                val updatedConfig = ConnectionConfig(
                                    id = selectedConnectionId.ifBlank { java.util.UUID.randomUUID().toString() },
                                    friendlyName = friendlyName.ifBlank { "API Connection" },
                                    provider = provider,
                                    apiKey = apiKey,
                                    baseUrl = baseUrl,
                                    modelName = modelName,
                                    modelEndpoint = modelEndpoint,
                                    availableModels = availableModels,
                                    customHeaders = customHeaders,
                                    active = currentActive
                                )
                                settingsRepository.saveConnectionConfig(updatedConfig)
                                settingsRepository.saveGenerationSettings(generationSettings)
                                allConnections = settingsRepository.getAllConnections()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Profile Changes", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
