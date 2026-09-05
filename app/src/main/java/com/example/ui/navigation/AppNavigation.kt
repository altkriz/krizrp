package com.example.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.data.repository.ChubRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.screens.charactereditor.CharacterEditorScreen
import com.example.ui.screens.characterlist.CharacterListScreen
import com.example.ui.screens.characterlist.CharacterListViewModel
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.chat.ChatViewModel
import com.example.ui.screens.connections.ConnectionsScreen
import com.example.ui.screens.gallery.AuthorProfileScreen
import com.example.ui.screens.gallery.AuthorProfileViewModel
import com.example.ui.screens.gallery.AuthorProfileViewModelFactory
import com.example.ui.screens.gallery.ChubGalleryScreen
import com.example.ui.screens.gallery.ChubGalleryViewModel
import com.example.ui.screens.gallery.ChubGalleryViewModelFactory
import com.example.ui.screens.personas.UserPersonasScreen
import com.example.ui.screens.logs.CrashLogsScreen
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
    object CrashLogs : Screen("crash_logs")
    object Gallery : Screen("gallery")
    object AuthorProfile : Screen("author/{authorName}") {
        fun createRoute(authorName: String) = "author/$authorName"
    }
}

@Composable
fun AppNavigation(
    characterRepository: CharacterRepository,
    chatRepository: ChatRepository,
    settingsRepository: SettingsRepository,
    chubRepository: ChubRepository,
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
                    label = { Text("Chub Gallery") },
                    icon = { Icon(Icons.Default.TravelExplore, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate(Screen.Gallery.route)
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

                NavigationDrawerItem(
                    label = { Text("Crash & Error Logs") },
                    icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate(Screen.CrashLogs.route)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                Spacer(modifier = Modifier.weight(1f))

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Text(
                    text = "PROJECT & COMMUNITY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )

                val context = LocalContext.current

                NavigationDrawerItem(
                    label = { Text("GitHub Repository") },
                    icon = { Icon(Icons.Default.Code, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/altkriz/krizrp"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Author: altkriz") },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/altkriz"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                Spacer(modifier = Modifier.height(12.dp))
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
                    chubRepository = chubRepository,
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
                    },
                    onOpenGallery = {
                        navController.navigate(Screen.Gallery.route)
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
                    onNavigateLogs = { navController.navigate(Screen.CrashLogs.route) },
                    isDarkTheme = isDarkTheme,
                    onToggleDarkTheme = onToggleDarkTheme
                )
            }

            composable(Screen.CrashLogs.route) {
                CrashLogsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Gallery.route) {
                val viewModel: ChubGalleryViewModel = viewModel(
                    factory = ChubGalleryViewModelFactory(chubRepository)
                )
                ChubGalleryScreen(
                    viewModel = viewModel,
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                    onNavigateToAuthor = { authorName ->
                        navController.navigate(Screen.AuthorProfile.createRoute(authorName))
                    }
                )
            }

            composable(
                route = Screen.AuthorProfile.route,
                arguments = listOf(navArgument("authorName") { type = NavType.StringType })
            ) { backStackEntry ->
                val authorName = backStackEntry.arguments?.getString("authorName") ?: ""
                val viewModel: AuthorProfileViewModel = viewModel(
                    key = authorName,
                    factory = AuthorProfileViewModelFactory(authorName, chubRepository)
                )
                AuthorProfileScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
