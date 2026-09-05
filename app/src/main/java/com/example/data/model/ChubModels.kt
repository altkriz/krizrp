package com.example.data.model

data class ChubCharacterNode(
    val id: Long = 0,
    val name: String = "",
    val fullPath: String = "",
    val description: String = "",
    val tagline: String = "",
    val starCount: Int = 0,
    val forksCount: Int = 0,
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val nChats: Int = 0,
    val nMessages: Int = 0,
    val nTokens: Int = 0,
    val avatarUrl: String = "",
    val maxResUrl: String = "",
    val topics: List<String> = emptyList(),
    val createdAt: String = "",
    val lastActivityAt: String = "",
    val verified: Boolean = false,
    val projectSpace: String = "characters",
    val nsfwImage: Boolean = false
) {
    val author: String
        get() = fullPath.substringBefore("/", missingDelimiterValue = "Unknown")

    val resolvedAvatarUrl: String
        get() {
            if (avatarUrl.isNotBlank() && avatarUrl.startsWith("http")) return avatarUrl
            if (fullPath.isNotBlank()) {
                return "https://avatars.charhub.io/avatars/$fullPath/avatar.webp"
            }
            return ""
        }

    val resolvedCardUrl: String
        get() {
            if (maxResUrl.isNotBlank() && maxResUrl.startsWith("http")) return maxResUrl
            if (fullPath.isNotBlank()) {
                return "https://avatars.charhub.io/avatars/$fullPath/chara_card_v2.png"
            }
            return ""
        }
}

data class ChubSearchResult(
    val characters: List<ChubCharacterNode>,
    val page: Int,
    val totalCount: Int,
    val cursor: String? = null
)

data class FavoriteAuthor(
    val username: String,
    val addedAt: Long = System.currentTimeMillis()
)
