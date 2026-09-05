package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * Modern character avatar view with rounded corner aesthetic, network loading with Coil,
 * and artistic animated/stylized fallback thumbnails for roleplay characters.
 */
@Composable
fun AvatarView(
    name: String,
    avatarUri: String? = null,
    avatarColor: Long = 0xFF7B1FA2,
    size: Dp = 56.dp,
    shapeRadius: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(shapeRadius)
    val context = LocalContext.current

    val resolvedUri = rememberResolvedAvatarUrl(name, avatarUri)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (!resolvedUri.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedUri)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    CharacterFallbackAvatar(name = name, avatarColor = avatarColor, size = size)
                },
                error = {
                    CharacterFallbackAvatar(name = name, avatarColor = avatarColor, size = size)
                }
            )
        } else {
            CharacterFallbackAvatar(name = name, avatarColor = avatarColor, size = size)
        }
    }
}

/**
 * Returns a high-res anime character artwork URL for default/seeded characters,
 * ensuring the main page displays rich character thumbnails as requested.
 */
private fun rememberResolvedAvatarUrl(name: String, currentUri: String?): String? {
    if (!currentUri.isNullOrBlank()) return currentUri

    return when {
        name.contains("E.C.H.O", ignoreCase = true) ->
            "https://avatars.charhub.io/avatars/creators/echo/avatar.webp"
        name.contains("Lyra", ignoreCase = true) ->
            "https://avatars.charhub.io/avatars/creators/lyra/avatar.webp"
        name.contains("Aria", ignoreCase = true) ->
            "https://avatars.charhub.io/avatars/reaper/aria/avatar.webp"
        name.contains("Haena", ignoreCase = true) ->
            "https://avatars.charhub.io/avatars/DcKaizen/haena-woo/avatar.webp"
        name.contains("Cricket", ignoreCase = true) ->
            "https://avatars.charhub.io/avatars/slaykyh/cricket/avatar.webp"
        name.contains("Mika", ignoreCase = true) ->
            "https://avatars.charhub.io/avatars/Lunari/mika/avatar.webp"
        else -> null
    }
}

@Composable
private fun CharacterFallbackAvatar(
    name: String,
    avatarColor: Long,
    size: Dp
) {
    val initial = name.firstOrNull()?.uppercase() ?: "?"

    // Palette gradient based on character persona
    val (gradientColors, iconVector) = when {
        name.contains("E.C.H.O", ignoreCase = true) -> {
            listOf(Color(0xFF0D47A1), Color(0xFF00E5FF), Color(0xFF1E88E5)) to Icons.Default.Memory
        }
        name.contains("Lyra", ignoreCase = true) -> {
            listOf(Color(0xFF004D40), Color(0xFF00BFA5), Color(0xFF80CBC4)) to Icons.Default.AutoAwesome
        }
        name.contains("Aria", ignoreCase = true) -> {
            listOf(Color(0xFF4A148C), Color(0xFFD500F9), Color(0xFF7C4DFF)) to Icons.Default.Terminal
        }
        else -> {
            val base = Color(avatarColor)
            listOf(base.copy(alpha = 0.8f), base, base.copy(alpha = 0.5f)) to null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(gradientColors, center = Offset.Unspecified, radius = 150f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            // Draw subtle decorative ambient circles
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = w * 0.45f,
                center = Offset(w * 0.2f, h * 0.2f)
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.15f),
                radius = w * 0.4f,
                center = Offset(w * 0.8f, h * 0.8f)
            )
        }

        if (iconVector != null && size >= 50.dp) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(size * 0.45f)
                )
            }
        } else {
            Text(
                text = initial,
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
