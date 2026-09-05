package com.example.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.data.repository.CharacterRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.screens.charactereditor.CharacterEditorScreen
import com.example.ui.screens.characterlist.CharacterListScreen
import com.example.ui.screens.characterlist.CharacterListViewModel
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.chat.ChatViewModel
import com.example.ui.screens.connections.ConnectionsScreen
import com.example.ui.screens.personas.UserPersonasScreen
import com.example.ui.screens.settings.SamplerSettingsScreen
import com.example.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object CharacterList : Screen("character_list")
    object Chat : Screen("chat/{characterId}") {
        fun createRoute(characterId: Long) = "chat/$characterId"
    }
    object CharacterEditor : Screen("character_editor/{characterId}") {
        fun createRoute(characterId: Long = 0L) = "character_editor/$characterId"
    }
    object Personas : Screen("personas")
    object Connections : Screen("connections")
    object Sampler : Screen("sampler")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    characterRepository: CharacterRepository,
    chatRepository: ChatRepository,
    settingsRepository: SettingsRepository,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        text = "KrizRP",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "AI Roleplay & Character Chat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                NavigationDrawerItem(
                    label = { Text("Characters") },
                    icon = { Icon(Icons.Default.Group, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate(Screen.CharacterList.route) {
                            popUpTo(Screen.CharacterList.route) { inclusive = true }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("User Personas") },
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate(Screen.Personas.route)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("API Connections") },
                    icon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate(Screen.Connections.route)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Sampler Parameters") },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate(Screen.Sampler.route)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate(Screen.Settings.route)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.CharacterList.route
        ) {
            composable(Screen.CharacterList.route) {
                val viewModel = remember {
                    CharacterListViewModel(characterRepository, chatRepository)
                }
                CharacterListScreen(
                    viewModel = viewModel,
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                    onSelectCharacter = { charId ->
                        navController.navigate(Screen.Chat.createRoute(charId))
                    },
                    onEditCharacter = { charId ->
                        navController.navigate(Screen.CharacterEditor.createRoute(charId))
                    },
                    onCreateCharacter = {
                        navController.navigate(Screen.CharacterEditor.createRoute(0L))
                    },
                    onOpenSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("characterId") { type = NavType.LongType })
            ) { backStackEntry ->
                val charId = backStackEntry.arguments?.getLong("characterId") ?: 0L
                val viewModel = remember(charId) {
                    ChatViewModel(
                        characterId = charId,
                        chatRepository = chatRepository,
                        characterRepository = characterRepository,
                        settingsRepository = settingsRepository
                    )
                }
                ChatScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onEditCharacter = { id ->
                        navController.navigate(Screen.CharacterEditor.createRoute(id))
                    }
                )
            }

            composable(
                route = Screen.CharacterEditor.route,
                arguments = listOf(navArgument("characterId") { type = NavType.LongType })
            ) { backStackEntry ->
                val charId = backStackEntry.arguments?.getLong("characterId") ?: 0L
                CharacterEditorScreen(
                    characterId = charId,
                    characterRepository = characterRepository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Personas.route) {
                UserPersonasScreen(
                    characterRepository = characterRepository,
                    settingsRepository = settingsRepository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Connections.route) {
                ConnectionsScreen(
                    settingsRepository = settingsRepository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Sampler.route) {
                SamplerSettingsScreen(
                    settingsRepository = settingsRepository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    settingsRepository = settingsRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateConnections = { navController.navigate(Screen.Connections.route) },
                    onNavigateSampler = { navController.navigate(Screen.Sampler.route) },
                    onNavigatePersonas = { navController.navigate(Screen.Personas.route) },
                    isDarkTheme = isDarkTheme,
                    onToggleDarkTheme = onToggleDarkTheme
                )
            }
        }
    }
}
