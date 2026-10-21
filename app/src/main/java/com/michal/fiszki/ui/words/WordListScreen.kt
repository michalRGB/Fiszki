package com.michal.fiszki.ui.words

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.michal.fiszki.model.LearningStatus
import com.michal.fiszki.model.Theme
import com.michal.fiszki.model.ThemeStatus
import com.michal.fiszki.model.Word

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListScreen(
    theme: Theme,
    words: List<Word>,
    isBasket: Boolean = false,
    onBack: () -> Unit,
    onPlayFlashcards: (List<Word>, Boolean) -> Unit,
    onSaveToBasket: (List<Word>) -> Unit = {},
    onRemoveFromBasket: (Word) -> Unit = {},
    onClearBasket: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    
    val unlearnedWords = if (isBasket) {
        words.filter { it.basketStatus != LearningStatus.LEARNED }
    } else {
        words.filter { it.status != LearningStatus.LEARNED }
    }
    
    val notLearnedWords = words.filter { it.status != LearningStatus.LEARNED }
    
    val hasLearnedWords = if (isBasket) {
        words.any { it.basketStatus == LearningStatus.LEARNED }
    } else {
        words.any { it.status == LearningStatus.LEARNED }
    }

    val hasNotLearnedWords = if (isBasket) {
        words.any { it.basketStatus == LearningStatus.NOT_LEARNED }
    } else {
        words.any { it.status == LearningStatus.NOT_LEARNED }
    }
    
    val firstUncheckedIndex = if (isBasket) {
        words.indexOfFirst { it.basketStatus == LearningStatus.UNCHECKED }
    } else {
        words.indexOfFirst { it.status == LearningStatus.UNCHECKED }
    }

    val canContinueTesting = (hasLearnedWords || hasNotLearnedWords) && firstUncheckedIndex != -1
    
    val allLearned = if (isBasket) {
        words.all { it.basketStatus == LearningStatus.LEARNED }
    } else {
        words.all { it.status == LearningStatus.LEARNED }
    }

    val isMaintenanceOrCompleted = theme.status.ordinal >= ThemeStatus.LEARNING_STAGE_4.ordinal

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (words.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No words in this theme.", color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text("Go Back")
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Text(
                                text = theme.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                onClick = { showMenu = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Flashcards",
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            val testCompleted = if (isBasket) {
                                words.all { it.basketStatus != LearningStatus.UNCHECKED }
                            } else {
                                words.all { it.status != LearningStatus.UNCHECKED }
                            }

                            if (isBasket && allLearned) {
                                Spacer(Modifier.height(12.dp))
                                val buttonText = if (theme.status == ThemeStatus.LEARNING_STAGE_4) "Complete the basket" else "Complete Stage"
                                Button(
                                    onClick = onClearBasket
                                ) {
                                    Icon(Icons.Default.DoneAll, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(buttonText)
                                }
                            } else if (!isBasket) {
                                if (isMaintenanceOrCompleted) {
                                    if (testCompleted) {
                                        Spacer(Modifier.height(12.dp))
                                        if (notLearnedWords.isNotEmpty()) {
                                            Button(
                                                onClick = { onSaveToBasket(notLearnedWords) }
                                            ) {
                                                Icon(Icons.Default.Save, contentDescription = null)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Add unlearned to basket & Complete Stage")
                                            }
                                        } else {
                                            Button(
                                                onClick = { onSaveToBasket(emptyList()) }
                                            ) {
                                                Icon(Icons.Default.DoneAll, contentDescription = null)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Complete Session")
                                            }
                                        }
                                    }
                                } else {
                                    // Learning Stage
                                    if (allLearned) {
                                        Spacer(Modifier.height(12.dp))
                                        Button(
                                            onClick = { onSaveToBasket(emptyList()) }
                                        ) {
                                            Icon(Icons.Default.DoneAll, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Complete Stage")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(words) { word ->
                        WordItem(
                            word = word,
                            isBasket = isBasket,
                            onRemoveFromBasket = onRemoveFromBasket
                        )
                    }
                }

                if (showMenu) {
                    ModalBottomSheet(
                        onDismissRequest = { showMenu = false }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = "Select Mode",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(16.dp)
                            )
                            
                            if (canContinueTesting) {
                                DropdownMenuItem(
                                    text = { Text("Continue Testing") },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                                    onClick = {
                                        showMenu = false
                                        onPlayFlashcards(words.drop(firstUncheckedIndex), true)
                                    }
                                )
                                HorizontalDivider()
                            }

                            DropdownMenuItem(
                                text = { Text("Learn All") },
                                leadingIcon = { Icon(Icons.Default.School, null) },
                                onClick = {
                                    showMenu = false
                                    onPlayFlashcards(words, false)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Test All") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Assignment, null) },
                                onClick = {
                                    showMenu = false
                                    onPlayFlashcards(words, true)
                                }
                            )

                            if (hasLearnedWords && unlearnedWords.isNotEmpty()) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Learn Unlearned") },
                                    leadingIcon = { Icon(Icons.Default.School, null) },
                                    onClick = {
                                        showMenu = false
                                        onPlayFlashcards(unlearnedWords, false)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Test Unlearned") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Assignment, null) },
                                    onClick = {
                                        showMenu = false
                                        onPlayFlashcards(unlearnedWords, true)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WordItem(
    word: Word,
    isBasket: Boolean,
    onRemoveFromBasket: (Word) -> Unit
) {
    val status = if (isBasket) word.basketStatus else word.status
    val surfaceColor = MaterialTheme.colorScheme.surface
    val statusColor = when (status) {
        LearningStatus.LEARNED -> Color(0xFF4CAF50)
        LearningStatus.NOT_LEARNED -> Color(0xFFF44336)
        LearningStatus.UNCHECKED -> Color.Transparent
    }
    
    val backgroundColor = when (status) {
        LearningStatus.LEARNED -> statusColor.copy(alpha = 0.1f).compositeOver(surfaceColor)
        LearningStatus.NOT_LEARNED -> statusColor.copy(alpha = 0.1f).compositeOver(surfaceColor)
        LearningStatus.UNCHECKED -> surfaceColor
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = if (status != LearningStatus.UNCHECKED) {
            androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
        } else null
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = word.original,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = word.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status != LearningStatus.UNCHECKED) {
                    when (status) {
                        LearningStatus.LEARNED -> Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Learned",
                            tint = statusColor
                        )
                        LearningStatus.NOT_LEARNED -> Icon(
                            Icons.Default.Error,
                            contentDescription = "Not Learned",
                            tint = statusColor
                        )
                        else -> {}
                    }
                }
                
                if (isBasket) {
                    IconButton(onClick = { onRemoveFromBasket(word) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove from basket",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
