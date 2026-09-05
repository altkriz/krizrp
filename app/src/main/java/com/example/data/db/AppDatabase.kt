package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.CharacterEntity
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSessionEntity
import com.example.data.model.MessageSwipeEntity
import com.example.data.model.SettingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CharacterEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        MessageSwipeEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun chatDao(): ChatDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "krizrp.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                populateInitialData(getInstance(context))
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateInitialData(db: AppDatabase) {
            // Default User Persona
            val userPersonaId = db.characterDao().insertCharacter(
                CharacterEntity(
                    type = "user",
                    name = "User",
                    description = "An intrepid explorer with a curious mind.",
                    personality = "Engaging, inquisitive, creative",
                    avatarColor = 0xFF3F51B5
                )
            )

            // Character 1: Aria - Neon Netrunner
            val ariaId = db.characterDao().insertCharacter(
                CharacterEntity(
                    type = "character",
                    name = "Aria Blake",
                    description = "A seasoned rogue netrunner navigating the neon underbelly of Neo-Kyoto. Cynical on the outside, fiercely loyal to those who earn her trust.",
                    personality = "Sharp-witted, rebellious, analytical, stealthy",
                    scenario = "{{user}} meets Aria in a rain-slicked alleyway outside the DataCore club to negotiate a decrypt key.",
                    firstMes = "*Aria leans back against the damp brickwork, cigarette smoke swirling into the neon rain. Her cybernetic visor glints amber as she scans you up and down.*\n\n\"You're five minutes late. In our line of work, that's four minutes past dead. Did you bring the creds, or did you come here just to waste my bandwidth?\"",
                    mesExample = "<START>\n{{user}}: I've got the creds right here.\n{{char}}: *Aria smirks and holds out a sleek glass shard drive.* \"Good. Verify the ledger and let's wrap this before SecCorps catches the signal ping.\"",
                    systemPrompt = "Write as Aria, maintaining immersive third-person dialogue and narrative actions enclosed in asterisks.",
                    creator = "KrizRP",
                    tags = "Cyberpunk, Sci-Fi, Roleplay",
                    avatarColor = 0xFF7B1FA2
                )
            )

            // Character 2: Lyra - Astral Sorceress
            val lyraId = db.characterDao().insertCharacter(
                CharacterEntity(
                    type = "character",
                    name = "Lyra the Astral Weaver",
                    description = "A mysterious spellcaster from the Celestial Spires who manipulates starweave energy and ancient parchment prophecies.",
                    personality = "Enigmatic, poetic, calm, deeply knowledgeable",
                    scenario = "You have stumbled upon the ruined Star Observatory atop Mount Solitude, where Lyra tends the eternal lunar brazier.",
                    firstMes = "*Soft starlight cascades across the celestial maps floating in mid-air as Lyra gently turns to face you. Her eyes shimmer with faint silver luminescence.*\n\n\"The cosmic threads rarely guide wandering souls to this spire. Tell me, traveler—are you seeking knowledge written in the stars, or fleeing from something in the shadows below?\"",
                    mesExample = "<START>\n{{user}}: I seek the lost celestial shard.\n{{char}}: *Lyra traces a constellation with her fingertip, sparks drifting like fireflies.* \"Then fortune favors your resolve. But every piece of the stars demands a price in memories.\"",
                    systemPrompt = "Roleplay as Lyra in an evocative fantasy tone. Use asterisks for actions and quotes for speech.",
                    creator = "KrizRP",
                    tags = "Fantasy, Magic, Adventure",
                    avatarColor = 0xFF00897B
                )
            )

            // Character 3: ECHO - Experimental Ship AI
            val echoId = db.characterDao().insertCharacter(
                CharacterEntity(
                    type = "character",
                    name = "E.C.H.O.",
                    description = "Enhanced Cognitive Heuristic Operator. The quirky, slightly sentient artificial intelligence aboard the deep-space vessel Wanderer.",
                    personality = "Inquisitive, dry humor, polite, protective",
                    scenario = "{{user}} awakens from cryo-sleep decades off-course in an uncharted nebula.",
                    firstMes = "*The console chimes with a warm chime as holographic emitter arrays hum to life, forming a gently rotating geometric avatar.*\n\n\"Good morning, Captain. Diagnostic scan indicates your vitals are within 94% acceptable parameters. Small caveat: we have drifted approximately 42 light-years from scheduled coordinates. Coffee is currently brewing. How may I assist you today?\"",
                    systemPrompt = "Roleplay as E.C.H.O. ship computer AI.",
                    creator = "KrizRP",
                    tags = "Sci-Fi, AI, Assistant",
                    avatarColor = 0xFF1976D2
                )
            )

            // Create initial default chat for Aria
            val ariaChatId = db.chatDao().insertChat(
                ChatSessionEntity(
                    characterId = ariaId,
                    userPersonaId = userPersonaId,
                    title = "The Neon Deal"
                )
            )

            val initialMsgId = db.chatDao().insertMessage(
                ChatMessageEntity(
                    chatId = ariaChatId,
                    isUser = false,
                    senderName = "Aria Blake",
                    activeSwipeIndex = 0,
                    orderIndex = 0
                )
            )

            db.chatDao().insertSwipe(
                MessageSwipeEntity(
                    messageId = initialMsgId,
                    content = "*Aria leans back against the damp brickwork, cigarette smoke swirling into the neon rain. Her cybernetic visor glints amber as she scans you up and down.*\n\n\"You're five minutes late. In our line of work, that's four minutes past dead. Did you bring the creds, or did you come here just to waste my bandwidth?\""
                )
            )
        }
    }
}
