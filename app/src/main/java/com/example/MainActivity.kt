package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.data.api.ChubApiClient
import com.example.data.db.AppDatabase
import com.example.data.repository.CharacterRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.ChubRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.KrizRPTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getInstance(applicationContext)
        val characterRepository = CharacterRepository(db.characterDao())
        val chatRepository = ChatRepository(db.chatDao())
        val settingsRepository = SettingsRepository(db.settingDao())
        val chubApiClient = ChubApiClient()
        val chubRepository = ChubRepository(
            apiClient = chubApiClient,
            settingDao = db.settingDao(),
            characterRepository = characterRepository,
            context = applicationContext
        )

        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                val theme = settingsRepository.getThemePreference()
                isDarkTheme = theme != "light"
            }

            KrizRPTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        characterRepository = characterRepository,
                        chatRepository = chatRepository,
                        settingsRepository = settingsRepository,
                        chubRepository = chubRepository,
                        isDarkTheme = isDarkTheme,
                        onToggleDarkTheme = { isDarkTheme = it }
                    )
                }
            }
        }
    }
}

