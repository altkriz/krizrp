package com.example.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessageWithSwipes
import com.example.data.model.ChatSessionEntity
import com.example.ui.components.AvatarView
import com.example.ui.components.RoleplayMarkdownText
import com.example.ui.components.SwipeControls

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onEditCharacter: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var showChatsSheet by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatMessageWithSwipes?>(null) }
    var editMessageText by remember { mutableStateOf("") }
    var showRenameChatDialog by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var newChatTitleInput by remember { mutableStateOf("") }

    // Auto-scroll when messages change
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.currentContent?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            state.character?.let { onEditCharacter(it.id) }
                        }
                    ) {
                        AvatarView(
                            name = state.character?.name ?: "Character",
                            avatarUri = state.character?.avatarUri?.takeIf { it.isNotBlank() },
                            avatarColor = state.character?.avatarColor ?: 0xFF7B1FA2,
                            size = 36.dp,
                            shapeRadius = 10.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = state.character?.name ?: "Roleplay",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Text(
                                text = state.currentChat?.title ?: "Chat",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showChatsSheet = true },
                        modifier = Modifier.testTag("chats_drawer_button")
                    ) {
                        Icon(imageVector = Icons.Default.Forum, contentDescription = "Chats List")
                    }
                    IconButton(
                        onClick = { state.character?.let { onEditCharacter(it.id) } }
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Character")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Quick prompt action chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickActions = listOf(
                        "*smiles*", "*looks around*", "*sighs*", "*leans closer*", "*continues*", "*nods*"
                    )
                    quickActions.forEach { action ->
                        SuggestionChip(
                            onClick = {
                                val current = state.inputText
                                val separator = if (current.isNotBlank() && !current.endsWith(" ")) " " else ""
                                viewModel.onInputTextChanged("$current$separator$action ")
                            },
                            label = { Text(action, fontSize = 12.sp) }
                        )
                    }
                }

                // Text Input Field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = { viewModel.onInputTextChanged(it) },
                        placeholder = {
                            Text(
                                "Send a message as ${state.userPersona?.name ?: "User"}...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = {
                            if (state.isGenerating) {
                                viewModel.stopGeneration()
                            } else {
                                viewModel.sendMessage()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (state.isGenerating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (state.isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (state.isGenerating) "Stop" else "Send"
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
        ) {
            items(state.messages, key = { it.message.id }) { item ->
                if (item.message.isUser) {
                    UserMessageBubble(
                        item = item,
                        userPersona = state.userPersona,
                        onEdit = {
                            editingMessage = item
                            editMessageText = item.currentContent
                        },
                        onCopy = {
                            copyToClipboard(context, item.currentContent)
                        },
                        onDelete = {
                            viewModel.deleteMessage(item.message.id)
                        }
                    )
                } else {
                    val isCurrentStreaming = state.isGenerating && (item.message.id == state.streamingMessageId || item == state.messages.lastOrNull())
                    val displayContent = if (state.isGenerating && item.message.id == state.streamingMessageId) {
                        state.streamingText
                    } else {
                        item.currentContent
                    }
                    CharacterMessageBubble(
                        item = item,
                        displayContent = displayContent,
                        character = state.character,
                        isStreaming = isCurrentStreaming,
                        onPreviousSwipe = {
                            viewModel.switchSwipe(item, item.message.activeSwipeIndex - 1)
                        },
                        onNextSwipe = {
                            viewModel.switchSwipe(item, item.message.activeSwipeIndex + 1)
                        },
                        onRegenerate = {
                            viewModel.regenerateLastMessage(item)
                        },
                        onEdit = {
                            editingMessage = item
                            editMessageText = item.currentContent
                        },
                        onCopy = {
                            copyToClipboard(context, item.currentContent)
                        },
                        onDelete = {
                            viewModel.deleteMessage(item.message.id)
                        }
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet: Multiple Chats for this character
    if (showChatsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChatsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chats with ${state.character?.name ?: ""}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Button(
                        onClick = {
                            viewModel.createNewChat("Chat ${state.allChatsForCharacter.size + 1}")
                            showChatsSheet = false
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Chat", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                state.allChatsForCharacter.forEach { chat ->
                    val isSelected = chat.id == state.currentChat?.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                viewModel.selectChat(chat.id)
                                showChatsSheet = false
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = chat.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        showRenameChatDialog = chat
                                        newChatTitleInput = chat.title
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(16.dp))
                                }
                                if (state.allChatsForCharacter.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.deleteChat(chat.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Edit message dialog
    if (editingMessage != null) {
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(
                    value = editMessageText,
                    onValueChange = { editMessageText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val msg = editingMessage ?: return@Button
                        val activeSwipe = msg.swipes.getOrNull(msg.message.activeSwipeIndex)
                        if (activeSwipe != null) {
                            viewModel.editSwipeContent(activeSwipe.id, editMessageText)
                        }
                        editingMessage = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Chat Dialog
    if (showRenameChatDialog != null) {
        val targetChat = showRenameChatDialog!!
        AlertDialog(
            onDismissRequest = { showRenameChatDialog = null },
            title = { Text("Rename Chat") },
            text = {
                OutlinedTextField(
                    value = newChatTitleInput,
                    onValueChange = { newChatTitleInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newChatTitleInput.isNotBlank()) {
                            viewModel.renameChat(targetChat.id, newChatTitleInput.trim())
                        }
                        showRenameChatDialog = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameChatDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CharacterMessageBubble(
    item: ChatMessageWithSwipes,
    displayContent: String = item.currentContent,
    character: com.example.data.model.CharacterEntity?,
    isStreaming: Boolean,
    onPreviousSwipe: () -> Unit,
    onNextSwipe: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        AvatarView(
            name = character?.name ?: item.message.senderName,
            avatarUri = character?.avatarUri?.takeIf { it.isNotBlank() },
            avatarColor = character?.avatarColor ?: 0xFF7B1FA2,
            size = 40.dp,
            shapeRadius = 10.dp
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = character?.name ?: item.message.senderName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val content = displayContent
                    if (content.isBlank() && isStreaming) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Thinking...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        RoleplayMarkdownText(rawText = content)
                    }

                    if (!isStreaming && item.swipes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SwipeControls(
                            currentSwipeIndex = item.message.activeSwipeIndex.coerceIn(0, item.swipeCount - 1),
                            totalSwipes = item.swipeCount,
                            onPreviousSwipe = onPreviousSwipe,
                            onNextSwipe = onNextSwipe,
                            onRegenerate = onRegenerate,
                            onEdit = onEdit,
                            onCopy = onCopy,
                            onDelete = onDelete
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserMessageBubble(
    item: ChatMessageWithSwipes,
    userPersona: com.example.data.model.CharacterEntity?,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = userPersona?.name ?: item.message.senderName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clickable { showMenu = true }
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = item.currentContent,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onCopy()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        AvatarView(
            name = userPersona?.name ?: item.message.senderName,
            avatarUri = userPersona?.avatarUri?.takeIf { it.isNotBlank() },
            avatarColor = userPersona?.avatarColor ?: 0xFF3F51B5,
            size = 40.dp,
            shapeRadius = 10.dp
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("KrizRP Message", text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
