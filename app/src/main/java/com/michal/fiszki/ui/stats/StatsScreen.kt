package com.michal.fiszki.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.michal.fiszki.model.Language
import com.michal.fiszki.model.Theme
import com.michal.fiszki.model.ThemeStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    selectedLanguage: Language,
    themes: List<Theme>,
    onBack: () -> Unit
) {
    val notLearnedColor = MaterialTheme.colorScheme.secondary
    val learningColor = MaterialTheme.colorScheme.tertiary
    val maintenanceColor = MaterialTheme.colorScheme.primary
    val completedColor = MaterialTheme.colorScheme.primaryContainer
    val primaryColor = MaterialTheme.colorScheme.primary

    val totalThemes = themes.size
    val completedCount = themes.count { it.status == ThemeStatus.COMPLETED }
    val progress = if (totalThemes > 0) completedCount.toFloat() / totalThemes else 0f

    val stats = remember(themes) {
        val notLearned = themes.count { it.status == ThemeStatus.NOT_STARTED && !it.isInProgress }
        val learning = themes.count { 
            (it.status in listOf(
                ThemeStatus.LEARNING_STAGE_1,
                ThemeStatus.LEARNING_STAGE_2,
                ThemeStatus.LEARNING_STAGE_3,
                ThemeStatus.LEARNING_STAGE_4,
                ThemeStatus.LEARNING_STAGE_5
            )) || (it.status == ThemeStatus.NOT_STARTED && it.isInProgress)
        }
        val maintenance = themes.count { it.status in listOf(
            ThemeStatus.MAINTENANCE_STAGE_1,
            ThemeStatus.MAINTENANCE_STAGE_2,
            ThemeStatus.MAINTENANCE_STAGE_3
        ) }
        
        listOf(
            StatItemData("Not Learned", notLearned, notLearnedColor),
            StatItemData("Learning Stages", learning, learningColor),
            StatItemData("Maintenance Stages", maintenance, maintenanceColor),
            StatItemData("Completed", completedCount, completedColor)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${selectedLanguage.displayName} Statistics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Overall Progress Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.size(100.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                            strokeWidth = 10.dp,
                            strokeCap = StrokeCap.Round,
                        )
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(100.dp),
                            color = primaryColor,
                            strokeWidth = 10.dp,
                            strokeCap = StrokeCap.Round,
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Column {
                        Text(
                            text = "Total Themes",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = totalThemes.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "$completedCount mastered",
                            style = MaterialTheme.typography.bodyMedium,
                            color = primaryColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Breakdown by Stage",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            stats.forEach { stat ->
                StatCard(stat)
            }
        }
    }
}

data class StatItemData(
    val label: String,
    val count: Int,
    val color: Color
)

@Composable
fun StatCard(stat: StatItemData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = stat.color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stat.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stat.count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = stat.color,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
