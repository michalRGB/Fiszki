package com.michal.fiszki.ui.themes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.michal.fiszki.model.CycleConfig
import com.michal.fiszki.model.Language
import com.michal.fiszki.model.Theme
import com.michal.fiszki.model.ThemeStatus
import com.michal.fiszki.model.isAvailableNow
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ThemeListScreen(
    selectedLanguage: Language,
    themes: List<Theme>,
    onThemeSelected: (Theme) -> Unit,
    onThemeOverride: (Theme, ThemeStatus, LocalDate?) -> Unit,
    onBack: () -> Unit,
    state: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {}
) {
    var themeToOverride by remember { mutableStateOf<Theme?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val totalWordCount = remember(themes) { 
        themes.filterNot { it.name.contains("Basket List", ignoreCase = true) }.sumOf { it.wordCount }
    }

    val groupedThemes = remember(themes) {
        themes.groupBy { theme ->
            val isLocked = theme.nextAvailableDate?.isAvailableNow() == false && !theme.isInProgress
            val isBasket = theme.name.contains("Basket List", ignoreCase = true)
            
            when {
                theme.isInProgress -> "In Progress"
                isLocked -> "Waiting"
                isBasket -> "Basket Lists"
                theme.status == ThemeStatus.NOT_STARTED -> "Not Learned"
                else -> "Ready for Review"
            }
        }
    }

    val groupOrder = listOf("In Progress", "Ready for Review", "Basket Lists", "Not Learned", "Waiting")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(text = "Learning ${selectedLanguage.displayName}")
                        Text(
                            text = "Total Words: $totalWordCount",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Normal
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    actions()
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "Info")
                    }
                }
            )
        }
    ) { padding ->
        if (themes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No themes available for this language.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = state,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupOrder.forEach { groupName ->
                    val themesInGroup = groupedThemes[groupName]
                    if (!themesInGroup.isNullOrEmpty()) {
                        stickyHeader {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                Text(
                                    text = groupName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                        items(themesInGroup, key = { "${it.id}_${it.categoryId}_${it.name}" }) { theme ->
                            val isLocked = theme.nextAvailableDate?.isAvailableNow() == false && !theme.isInProgress
                            ThemeItem(
                                theme = theme,
                                onClick = { if (!isLocked) onThemeSelected(theme) },
                                onLongClick = { 
                                    if (!isLocked) {
                                        themeToOverride = theme 
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showInfoDialog) {
            InfoDialog(onDismiss = { showInfoDialog = false })
        }

        themeToOverride?.let { theme ->
            OverrideDialog(
                theme = theme,
                onDismiss = { themeToOverride = null },
                onConfirm = { status, date ->
                    onThemeOverride(theme, status, date)
                    themeToOverride = null
                }
            )
        }
    }
}

@Composable
fun InfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Learning Stages") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("The app uses a spaced repetition system to help you memorize words efficiently.")
                
                StageInfoItem("Learning Stages (1-5)", "Shorter intervals to build initial memory.")
                StageInfoItem("Maintenance Stages (1-3)", "Longer intervals to move words to long-term memory.")
                
                HorizontalDivider()
                
                Text("Cooldown Periods:", style = MaterialTheme.typography.labelLarge)
                CooldownItem("Stage 1", "1 day")
                CooldownItem("Stage 2", "3 days")
                CooldownItem("Stage 3", "7 days")
                CooldownItem("Stage 4", "21 days")
                CooldownItem("Stage 5", "21 days")
                CooldownItem("Maintenance 1", "1 month")
                CooldownItem("Maintenance 2", "3 months")
                CooldownItem("Maintenance 3", "3 months")
                CooldownItem("Completed", "Mastered! (3 months)")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}

@Composable
fun StageInfoItem(title: String, description: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CooldownItem(stage: String, duration: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = stage, style = MaterialTheme.typography.bodyMedium)
        Text(text = duration, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun OverrideDialog(
    theme: Theme,
    onDismiss: () -> Unit,
    onConfirm: (ThemeStatus, LocalDate?) -> Unit
) {
    val isBasket = theme.name.contains("Basket List", ignoreCase = true)
    val targetStages = remember(isBasket) {
        if (isBasket) {
            listOf(
                "Learning Stage 1",
                "Learning Stage 2",
                "Learning Stage 3",
                "Learning Stage 4"
            )
        } else {
            listOf(
                "Learning Stage 1",
                "Learning Stage 2",
                "Learning Stage 3",
                "Learning Stage 4",
                "Learning Stage 5",
                "Maintenance Stage 1",
                "Maintenance Stage 2",
                "Maintenance Stage 3",
                "Completed"
            )
        }
    }

    val currentTarget = remember(theme.status) {
        if (theme.status == ThemeStatus.COMPLETED) "Completed"
        else CycleConfig.getNextStage(theme.status).first.displayName
    }

    var selectedStage by remember { mutableStateOf(currentTarget) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Override Stage: ${theme.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Select Target Stage", style = MaterialTheme.typography.labelMedium)
                targetStages.forEach { stageName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedStage == stageName,
                                onClick = { selectedStage = stageName }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedStage == stageName,
                            onClick = { selectedStage = stageName }
                        )
                        Text(
                            text = stageName,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val targetStatus = when (selectedStage) {
                    "Learning Stage 1" -> ThemeStatus.NOT_STARTED
                    "Completed" -> ThemeStatus.COMPLETED
                    else -> {
                        ThemeStatus.entries.find { 
                            CycleConfig.getNextStage(it).first.displayName == selectedStage 
                        } ?: ThemeStatus.NOT_STARTED
                    }
                }
                // Set to yesterday to ensure it's available even if current time is before 5:30 AM
                onConfirm(targetStatus, LocalDate.now().minusDays(1))
            }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
