package com.michal.fiszki.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.michal.fiszki.model.CycleConfig
import com.michal.fiszki.model.Language
import com.michal.fiszki.model.Theme
import com.michal.fiszki.model.ThemeStatus
import com.michal.fiszki.ui.components.ThemeItem
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ThemeListScreen(
    selectedLanguage: Language,
    themes: List<Theme>,
    onThemeSelected: (Theme) -> Unit,
    onThemeOverride: (Theme, ThemeStatus, LocalDate?) -> Unit,
    onBack: () -> Unit
) {
    var themeToOverride by remember { mutableStateOf<Theme?>(null) }

    // Separate themes: In Progress first
    val inProgressThemes = remember(themes) { themes.filter { it.isInProgress } }
    val finishedThemes = remember(themes) { themes.filter { !it.isInProgress } }
    
    // Group remaining themes by their target stage
    val groupedFinished = remember(finishedThemes) {
        finishedThemes.groupBy { theme ->
            when (theme.status) {
                ThemeStatus.NOT_STARTED -> "Not Started"
                ThemeStatus.COMPLETED -> "Completed"
                else -> {
                    // Group based on the status itself (the stage they are in/waiting for)
                    theme.status.displayName
                }
            }
        }
    }

    // Define the display order of groups
    val statusOrder = remember {
        ThemeStatus.entries.map { it.displayName }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Learning ${selectedLanguage.displayName}") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. In Progress Section
            if (inProgressThemes.isNotEmpty()) {
                stickyHeader {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Text(
                            text = "In Progress",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                items(inProgressThemes) { theme ->
                    ThemeItem(
                        theme = theme,
                        onClick = { onThemeSelected(theme) },
                        onLongClick = { themeToOverride = theme }
                    )
                }
            }

            // 2. Stages Sections
            statusOrder.forEach { groupName ->
                val themesInGroup = groupedFinished[groupName]
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
                    items(themesInGroup) { theme ->
                        ThemeItem(
                            theme = theme,
                            onClick = { onThemeSelected(theme) },
                            onLongClick = { themeToOverride = theme }
                        )
                    }
                }
            }
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
fun OverrideDialog(
    theme: Theme,
    onDismiss: () -> Unit,
    onConfirm: (ThemeStatus, LocalDate?) -> Unit
) {
    var selectedStatus by remember { mutableStateOf(theme.status) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Override Progress: ${theme.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Select Stage to become Ready/Waiting:", style = MaterialTheme.typography.labelMedium)
                ThemeStatus.entries.forEach { status ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedStatus == status,
                                onClick = { selectedStatus = status }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status }
                        )
                        Text(
                            text = status.displayName,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                onConfirm(selectedStatus, LocalDate.now()) 
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
