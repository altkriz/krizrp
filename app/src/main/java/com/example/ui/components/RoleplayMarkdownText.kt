package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RoleplayMarkdownText(
    rawText: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    actionColor: Color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f)
) {
    // Check for <think>...</think>
    val thinkRegex = Regex("<think>([\\s\\S]*?)(?:</think>|$)", RegexOption.IGNORE_CASE)
    val match = thinkRegex.find(rawText)

    val thinkContent = match?.groups?.get(1)?.value?.trim()
    val mainContent = if (match != null) {
        rawText.replace(thinkRegex, "").trim()
    } else {
        rawText
    }

    var thinkExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Render Think Block if present
        if (!thinkContent.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { thinkExpanded = !thinkExpanded }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "Thought Process",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Thought Process",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = if (thinkExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (thinkExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = thinkExpanded) {
                        Text(
                            text = thinkContent,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = FontStyle.Italic,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        // Render main text with asterisks styled
        val annotated = parseRoleplayText(mainContent, textColor, actionColor)
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun parseRoleplayText(
    text: String,
    textColor: Color,
    actionColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        var inAction = false
        var lastIdx = 0
        var i = 0

        while (i < text.length) {
            if (text[i] == '*') {
                // Append previous slice
                val slice = text.substring(lastIdx, i)
                if (slice.isNotEmpty()) {
                    if (inAction) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = actionColor))
                        append(slice)
                        pop()
                    } else {
                        pushStyle(SpanStyle(color = textColor))
                        append(slice)
                        pop()
                    }
                }
                inAction = !inAction
                lastIdx = i + 1
            }
            i++
        }

        // Remainder
        if (lastIdx < text.length) {
            val slice = text.substring(lastIdx)
            if (inAction) {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = actionColor))
                append(slice)
                pop()
            } else {
                pushStyle(SpanStyle(color = textColor))
                append(slice)
                pop()
            }
        }
    }
}
