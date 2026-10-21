package com.michal.fiszki.ui.themes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.michal.fiszki.model.CycleConfig
import com.michal.fiszki.model.Theme
import com.michal.fiszki.model.ThemeStatus
import com.michal.fiszki.model.isAvailableNow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemeItem(theme: Theme, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val isBasket = theme.id < 0
    val isLocked = theme.nextAvailableDate?.isAvailableNow() == false && !theme.isInProgress
    val isReadyForReview = !isLocked && theme.status != ThemeStatus.NOT_STARTED && theme.status != ThemeStatus.COMPLETED
    val nextStatus = CycleConfig.getNextStage(theme.status).first
    
    val containerColor = when {
        isLocked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        theme.isInProgress -> Color(0xFFFFCC80) // Stronger Orange (Orange 200)
        isReadyForReview -> Color(0xFFC8E6C9) // Slightly deeper Light Green (Green 100)
        isBasket -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    
    val contentColor = when {
        isLocked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        theme.isInProgress -> Color(0xFF5D4037) // Dark Brown-Orange for contrast
        isReadyForReview -> Color(0xFF1B5E20) // Deep Green (Green 900)
        isBasket -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                
                val statusText = when {
                    theme.isInProgress -> "In Progress: ${nextStatus.displayName}"
                    isLocked -> "Waiting for ${nextStatus.displayName}"
                    isBasket -> {
                        when (theme.status) {
                            ThemeStatus.NOT_STARTED -> ""
                            ThemeStatus.COMPLETED -> "All words learned"
                            else -> "Stage: ${theme.status.displayName}"
                        }
                    }
                    theme.status == ThemeStatus.COMPLETED -> "Mastered"
                    theme.status == ThemeStatus.NOT_STARTED -> ""
                    else -> "Ready: ${nextStatus.displayName}"
                }
                
                if (statusText.isNotEmpty()) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val totalWordsInTheme = theme.wordCount
                val activeWordsInTheme = theme.wordCount - theme.basketCount

                Text(
                    text = "$totalWordsInTheme words",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )

                if (!isBasket && theme.basketCount > 0) {
                    Text(
                        text = "${theme.basketCount} in basket",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                    )
                }

                if (isLocked) {
                    Text(
                        text = "Available: ${theme.nextAvailableDate?.format(DateTimeFormatter.ofPattern("MMM dd"))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
