package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-fidelity provider icons matching the API Manager design in the reference screenshot:
 * - Google (Google 'G' / Gemini 4-color emblem)
 * - OpenAI (Signature spiral rosette emblem)
 * - Claude / Anthropic (Signature terracotta "A\" emblem)
 * - OpenRouter (Interconnected router / arrows emblem)
 * - Ollama (Terminal / llama emblem)
 */
@Composable
fun ApiProviderIcon(
    provider: String,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp
) {
    val normalized = provider.lowercase()
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        when {
            normalized.contains("google") || normalized.contains("gemini") -> {
                GoogleIcon(size = size)
            }
            normalized.contains("openai") || normalized.contains("gpt") -> {
                OpenAiIcon(size = size)
            }
            normalized.contains("claude") || normalized.contains("anthropic") -> {
                ClaudeIcon(size = size)
            }
            normalized.contains("openrouter") -> {
                OpenRouterIcon(size = size)
            }
            normalized.contains("ollama") -> {
                OllamaIcon(size = size)
            }
            else -> {
                DefaultProviderIcon(provider = provider, size = size)
            }
        }
    }
}

@Composable
fun GoogleIcon(size: Dp) {
    // Official Google 4-color emblem
    Box(
        modifier = Modifier
            .size(size)
            .background(Color(0xFF1E1C2B), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.65f)) {
            val w = this.size.width
            val h = this.size.height
            val strokeWidth = w * 0.18f

            // Google Blue, Red, Yellow, Green arcs
            val blue = Color(0xFF4285F4)
            val red = Color(0xFFEA4335)
            val yellow = Color(0xFFFBBC05)
            val green = Color(0xFF34A853)

            val inset = strokeWidth / 2f
            val arcSize = Size(w - strokeWidth, h - strokeWidth)
            val topLeft = Offset(inset, inset)

            // Red arc (top)
            drawArc(
                color = red,
                startAngle = 180f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Yellow arc (left)
            drawArc(
                color = yellow,
                startAngle = 120f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Green arc (bottom)
            drawArc(
                color = green,
                startAngle = 40f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Blue horizontal crossbar and partial arc
            drawArc(
                color = blue,
                startAngle = -20f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawLine(
                color = blue,
                start = Offset(w * 0.45f, h * 0.5f),
                end = Offset(w * 0.95f, h * 0.5f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun OpenAiIcon(size: Dp) {
    // OpenAI Rosette spiral icon with dark badge background
    Box(
        modifier = Modifier
            .size(size)
            .background(Color(0xFF10A37F).copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.62f)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.width * 0.42f
            val strokeW = this.size.width * 0.11f
            val iconColor = Color(0xFF10A37F)

            // Draw 6-spoke rosette loops
            for (i in 0 until 6) {
                val angle = (i * 60f) * (Math.PI / 180f).toFloat()
                val spokeX = center.x + Math.cos(angle.toDouble()).toFloat() * (radius * 0.45f)
                val spokeY = center.y + Math.sin(angle.toDouble()).toFloat() * (radius * 0.45f)
                drawCircle(
                    color = iconColor,
                    radius = radius * 0.38f,
                    center = Offset(spokeX, spokeY),
                    style = Stroke(width = strokeW)
                )
            }
            drawCircle(
                color = Color.White,
                radius = radius * 0.2f,
                center = center
            )
        }
    }
}

@Composable
fun ClaudeIcon(size: Dp) {
    // Anthropic signature terracotta stylized "A\" emblem
    Box(
        modifier = Modifier
            .size(size)
            .background(Color(0xFFD97757).copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "AI",
            color = Color(0xFFD97757),
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp
        )
    }
}

@Composable
fun OpenRouterIcon(size: Dp) {
    // OpenRouter interconnected node icon
    Box(
        modifier = Modifier
            .size(size)
            .background(Color(0xFF6366F1).copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.6f)) {
            val w = this.size.width
            val h = this.size.height
            val color = Color(0xFF818CF8)
            val strokeW = w * 0.12f

            // Center chevron / nodes
            val path = Path().apply {
                moveTo(w * 0.2f, h * 0.35f)
                lineTo(w * 0.5f, h * 0.2f)
                lineTo(w * 0.8f, h * 0.35f)
                lineTo(w * 0.8f, h * 0.65f)
                lineTo(w * 0.5f, h * 0.8f)
                lineTo(w * 0.2f, h * 0.65f)
                close()
            }
            drawPath(path, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color = Color.White, radius = w * 0.14f, center = Offset(w * 0.5f, h * 0.5f))
        }
    }
}

@Composable
fun OllamaIcon(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(Color(0xFF8B5CF6).copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🦙",
            fontSize = (size.value * 0.5f).sp
        )
    }
}

@Composable
fun DefaultProviderIcon(provider: String, size: Dp) {
    val initial = provider.firstOrNull()?.uppercase() ?: "A"
    Box(
        modifier = Modifier
            .size(size)
            .background(Color(0xFF374151), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = (size.value * 0.45f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}
