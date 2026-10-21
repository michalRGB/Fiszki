package com.michal.fiszki.ui.flashcards

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.michal.fiszki.model.Theme
import com.michal.fiszki.model.ThemeStatus
import com.michal.fiszki.model.Word
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(
    theme: Theme,
    words: List<Word>,
    isTestModeInitially: Boolean,
    isBasket: Boolean = false,
    onBack: () -> Unit,
    onWordLearned: (Word, Boolean) -> Unit,
    onSessionFinished: (List<Word>, Boolean) -> Unit,
    volumeDownEvents: kotlinx.coroutines.flow.SharedFlow<Unit>? = null,
    volumeUpEvents: kotlinx.coroutines.flow.SharedFlow<Unit>? = null,
    skipEvents: kotlinx.coroutines.flow.SharedFlow<Unit>? = null
) {
    var currentWords by remember { mutableStateOf(words) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isTestMode by remember { mutableStateOf(isTestModeInitially) }
    var unlearnedWordsInSession by remember { mutableStateOf(setOf<Word>()) }
    var showTranslation by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var isFlipped by remember(isTestMode) { mutableStateOf(isTestMode) }
    var showBothGlobal by remember(isTestMode) { mutableStateOf(false) }

    // Animation trigger states
    var triggerSwipeRight by remember { mutableStateOf(false) }
    var triggerSwipeLeft by remember { mutableStateOf(false) }
    var triggerSkip by remember { mutableStateOf(false) }
    
    // Result pending state to avoid "jumping"
    var pendingSwipeResult by remember { mutableStateOf<Boolean?>(null) }
    var pendingSkip by remember { mutableStateOf(false) }

    val currentWord = if (currentIndex < currentWords.size) currentWords[currentIndex] else null
    
    val isMaintenanceOrCompleted = remember(theme.status) { 
        theme.status.ordinal >= ThemeStatus.LEARNING_STAGE_4.ordinal
    }

    // Centralized reset for triggers and state when card changes
    LaunchedEffect(currentIndex, currentWords) {
        // We now reset showTranslation in processResult/processSkip for better atomicity
        triggerSwipeRight = false
        triggerSwipeLeft = false
        triggerSkip = false
        pendingSwipeResult = null
        pendingSkip = false
    }

    val processResult = { known: Boolean ->
        val word = currentWords.getOrNull(currentIndex)
        if (word != null && !isFinished) {
            showTranslation = false // Reset here atomically
            if (isTestMode) {
                onWordLearned(word, known)
                if (!known) {
                    unlearnedWordsInSession = unlearnedWordsInSession + word
                } else {
                    unlearnedWordsInSession = unlearnedWordsInSession - word
                }
            }
            
            if (currentIndex < currentWords.size - 1) {
                currentIndex++
            } else {
                isFinished = true
            }
        }
    }

    val processSkip = {
        if (!isFinished && currentIndex < currentWords.size - 1) {
            showTranslation = false // Reset here atomically
            val wordToSkip = currentWords[currentIndex]
            val updatedList = currentWords.toMutableList()
            updatedList.removeAt(currentIndex)
            updatedList.add(wordToSkip)
            currentWords = updatedList
        }
    }

    LaunchedEffect(volumeDownEvents) {
        volumeDownEvents?.collect {
            if (!isFinished && !triggerSwipeRight && !triggerSwipeLeft && !triggerSkip) {
                triggerSwipeRight = true
            }
        }
    }

    LaunchedEffect(volumeUpEvents) {
        volumeUpEvents?.collect {
            if (!isFinished && !triggerSwipeRight && !triggerSwipeLeft && !triggerSkip) {
                if (isTestMode) {
                    triggerSwipeLeft = true
                } else {
                    if (currentIndex > 0) {
                        currentIndex--
                    }
                }
            }
        }
    }

    LaunchedEffect(skipEvents) {
        skipEvents?.collect {
            if (!isFinished && isTestMode && currentIndex < currentWords.size - 1 && 
                !triggerSwipeRight && !triggerSwipeLeft && !triggerSkip) {
                triggerSkip = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "${theme.name} - ${if (isTestMode) "Test" else "Review"}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isFinished) {
                        IconButton(onClick = { showBothGlobal = !showBothGlobal }) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Show Both",
                                tint = if (showBothGlobal) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = { isFlipped = !isFlipped }) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Flip Cards",
                                tint = if (isFlipped) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = {
                            val seen = currentWords.take(currentIndex)
                            val remaining = currentWords.drop(currentIndex).shuffled()
                            currentWords = seen + remaining
                        }) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle remaining")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isFinished) {
                    FinishedScreen(
                        theme = theme,
                        isTestMode = isTestMode,
                        unlearnedCount = unlearnedWordsInSession.size,
                        totalCount = currentWords.size,
                        isMaintenanceOrCompleted = isMaintenanceOrCompleted,
                        isBasket = isBasket,
                        onLearnAll = {
                            currentWords = words
                            currentIndex = 0
                            isTestMode = false
                            showBothGlobal = false
                            isFlipped = false
                            isFinished = false
                            unlearnedWordsInSession = emptySet()
                        },
                        onTestAll = {
                            currentWords = words
                            currentIndex = 0
                            isTestMode = true
                            showBothGlobal = false
                            isFlipped = true
                            isFinished = false
                            unlearnedWordsInSession = emptySet()
                        },
                        onLearnUnlearned = {
                            currentWords = unlearnedWordsInSession.toList()
                            currentIndex = 0
                            isTestMode = false
                            showBothGlobal = false
                            isFlipped = false
                            isFinished = false
                            unlearnedWordsInSession = emptySet()
                        },
                        onTestUnlearned = {
                            currentWords = unlearnedWordsInSession.toList()
                            currentIndex = 0
                            isTestMode = true
                            showBothGlobal = false
                            isFlipped = true
                            isFinished = false
                            unlearnedWordsInSession = emptySet()
                        },
                        onFinishSession = { onSessionFinished(unlearnedWordsInSession.toList(), false) },
                        onForceAdvance = { onSessionFinished(unlearnedWordsInSession.toList(), true) },
                        onBack = onBack
                    )
                } else if (currentWord != null) {
                    Text(
                        text = "Word ${currentIndex + 1} of ${currentWords.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val nextIndex = currentIndex + 1
                        if (nextIndex < currentWords.size) {
                            key(currentWords[nextIndex].id) {
                                Flashcard(
                                    key = nextIndex,
                                    word = currentWords[nextIndex],
                                    showTranslation = false,
                                    isTestMode = isTestMode,
                                    isFlipped = isFlipped,
                                    showBoth = showBothGlobal,
                                    onClick = {},
                                    onSwipe = {},
                                    isBackground = true
                                )
                            }
                        }

                        key(currentWord.id) {
                            Flashcard(
                                key = currentIndex,
                                word = currentWord,
                                showTranslation = showTranslation,
                                isTestMode = isTestMode,
                                isFlipped = isFlipped,
                                showBoth = showBothGlobal,
                                onClick = { showTranslation = !showTranslation },
                                onSwipe = { swipedRight -> pendingSwipeResult = swipedRight },
                                onSkip = { pendingSkip = true },
                                triggerSwipeRight = triggerSwipeRight,
                                triggerSwipeLeft = triggerSwipeLeft,
                                triggerSkip = triggerSkip,
                                onAnimationComplete = {
                                    when {
                                        pendingSkip -> processSkip()
                                        pendingSwipeResult != null -> processResult(pendingSwipeResult!!)
                                        triggerSkip -> processSkip()
                                        triggerSwipeRight -> processResult(true)
                                        triggerSwipeLeft -> processResult(false)
                                    }
                                    triggerSwipeRight = false
                                    triggerSwipeLeft = false
                                    triggerSkip = false
                                    pendingSwipeResult = null
                                    pendingSkip = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    if (isTestMode) {
                        Text(
                            "Swipe Right/Vol Down if you know it, Left/Vol Up if you don't",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Text(
                            "Swipe or use Volume Down/Up to see next/previous card",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            if (!isFinished) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Slot 1: Previous Arrow
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (currentIndex > 0) {
                            SmallFloatingActionButton(
                                onClick = {
                                    if (!triggerSwipeRight && !triggerSwipeLeft && !triggerSkip) {
                                        currentIndex--
                                    }
                                },
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous word"
                                )
                            }
                        }
                    }

                    // Slot 2: Test "Don't Know" Button
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (isTestMode && currentWord != null) {
                            SmallFloatingActionButton(
                                onClick = { if (!triggerSwipeRight && !triggerSwipeLeft && !triggerSkip) triggerSwipeLeft = true },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = CircleShape
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Don't know")
                            }
                        }
                    }

                    // Slot 3: Test "Know" Button
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (isTestMode && currentWord != null) {
                            SmallFloatingActionButton(
                                onClick = { if (!triggerSwipeRight && !triggerSwipeLeft && !triggerSkip) triggerSwipeRight = true },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = CircleShape
                            ) {
                                Icon(Icons.Default.Done, contentDescription = "Know")
                            }
                        }
                    }

                    // Slot 4: Next Arrow / Skip
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (isTestMode) {
                            if (currentIndex < currentWords.size - 1) {
                                SmallFloatingActionButton(
                                    onClick = { if (!triggerSwipeRight && !triggerSwipeLeft && !triggerSkip) triggerSkip = true },
                                    shape = CircleShape,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Skip word"
                                    )
                                }
                            }
                        } else if (currentIndex < currentWords.size) {
                            SmallFloatingActionButton(
                                onClick = {
                                    if (!triggerSwipeRight && !triggerSwipeLeft && !triggerSkip) {
                                        triggerSwipeRight = true
                                    }
                                },
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next word"
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
fun Flashcard(
    word: Word,
    showTranslation: Boolean,
    isTestMode: Boolean,
    isFlipped: Boolean = false,
    showBoth: Boolean = false,
    onClick: () -> Unit,
    onSwipe: (Boolean) -> Unit,
    onSkip: () -> Unit = {},
    modifier: Modifier = Modifier,
    isBackground: Boolean = false,
    key: Any? = null,
    triggerSwipeRight: Boolean = false,
    triggerSwipeLeft: Boolean = false,
    triggerSkip: Boolean = false,
    onAnimationComplete: () -> Unit = {}
) {
    val offsetX = remember(word, key) { Animatable(0f) }
    val offsetY = remember(word, key) { Animatable(0f) }
    val alpha = remember(word, key) { Animatable(if (isBackground) 0.6f else 1f) }
    val scale = remember(word, key) { Animatable(if (isBackground) 0.92f else 1f) }
    val rotation = offsetX.value / 10f
    val scope = rememberCoroutineScope()

    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val currentOnSkip by rememberUpdatedState(onSkip)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnAnimationComplete by rememberUpdatedState(onAnimationComplete)

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val swipeRightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f).compositeOver(surfaceColor)
    val swipeLeftColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f).compositeOver(surfaceColor)

    val frontText = if (isFlipped) word.translation else word.original
    val backText = if (isFlipped) word.original else word.translation

    // Combined revealed state: either user tapped, or global toggle is on
    val isRevealed = showBoth || showTranslation

    val wordOffset by animateDpAsState(
        targetValue = if (isRevealed) (-24).dp else 0.dp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "wordOffset"
    )
    val translationAlphaAnimated by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "translationAlpha"
    )

    // Derived progress for visual transitions (0 to 1 based on swipe distance)
    val currentProgress = remember(word, key) {
        derivedStateOf {
            val totalDrag = sqrt((offsetX.value * offsetX.value + offsetY.value * offsetY.value).toDouble()).toFloat()
            (totalDrag / 400f).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(isBackground) {
        if (!isBackground) {
            launch { scale.animateTo(1f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 3000f)) }
            launch { alpha.animateTo(1f, animationSpec = tween(150)) }
        } else {
            launch { scale.snapTo(0.92f) }
            launch { alpha.snapTo(0.6f) }
        }
    }

    // Listen for external triggers (volume buttons or on-screen buttons)
    LaunchedEffect(triggerSwipeRight, triggerSwipeLeft, triggerSkip) {
        if (triggerSwipeRight || triggerSwipeLeft || triggerSkip) {
            val isRight = triggerSwipeRight || triggerSkip
            val isActualSkip = triggerSkip

            // triggerSwipeRight (Know/Vol Down) flies to the top right corner.
            // triggerSwipeLeft (Don't Know/Vol Up) flies to the top left corner.
            // triggerSkip (Skip Button) flies to the bottom right corner.
            val targetX = if (isRight) 2500f else -2500f
            val targetY = if (isActualSkip) 2500f else -2500f
            val animDuration = 500

            launch { offsetX.animateTo(targetX, animationSpec = tween(animDuration, easing = FastOutSlowInEasing)) }
            launch { offsetY.animateTo(targetY, animationSpec = tween(animDuration, easing = FastOutSlowInEasing)) }
            if (!isActualSkip || !isTestMode) {
                launch { alpha.animateTo(0f, animationSpec = tween(animDuration)) }
            }

            kotlinx.coroutines.delay(animDuration.toLong())
            currentOnAnimationComplete()
            if (isActualSkip) {
                currentOnSkip()
            } else {
                currentOnSwipe(triggerSwipeRight)
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
            .graphicsLayer(
                translationX = offsetX.value,
                translationY = offsetY.value,
                rotationZ = rotation,
                scaleX = scale.value,
                scaleY = scale.value,
                alpha = alpha.value
            )
            .pointerInput(word, key) {
                if (isBackground) return@pointerInput
                detectTapGestures(onTap = { currentOnClick() })
            }
            .pointerInput(word, key) {
                if (isBackground) return@pointerInput
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val totalDrag = sqrt((offsetX.value * offsetX.value + offsetY.value * offsetY.value).toDouble()).toFloat()
                            if (totalDrag > 400f) {
                                val angle = atan2(offsetY.value.toDouble(), offsetX.value.toDouble())
                                val targetX = (cos(angle) * 1500).toFloat()
                                val targetY = (sin(angle) * 1500).toFloat()
                                val swipeRight = offsetX.value > 0
                                val animDuration = 400
                                launch { offsetX.animateTo(targetX, animationSpec = tween(animDuration)) }
                                launch { offsetY.animateTo(targetY, animationSpec = tween(animDuration)) }
                                launch { alpha.animateTo(0f, animationSpec = tween(animDuration)) }
                                kotlinx.coroutines.delay(animDuration.toLong())
                                currentOnSwipe(swipeRight)
                                currentOnAnimationComplete()
                            } else {
                                launch { offsetX.animateTo(0f) }
                                launch { offsetY.animateTo(0f) }
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                        }
                    }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBackground) 2.dp else 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !isBackground && isTestMode && offsetX.value > 100f -> swipeRightColor
                !isBackground && isTestMode && offsetX.value < -100f -> swipeLeftColor
                else -> surfaceColor
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val frontAlpha = if (isBackground) 1f
                            else if (isRevealed) 1f
                            else if (triggerSkip && isTestMode) 1f
                            else (1f - currentProgress.value * 3f).coerceIn(0f, 1f)

            val backAlpha = if (isBackground) 0f
                           else if (isRevealed) translationAlphaAnimated
                           else if (triggerSkip && isTestMode) 0f
                           else (currentProgress.value * 3f - 0.5f).coerceIn(0f, 1f)

            Text(
                text = frontText,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(16.dp)
                    .offset(y = if (triggerSkip && isTestMode) 0.dp else wordOffset)
                    .alpha(frontAlpha)
            )
            
            Text(
                text = backText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(16.dp)
                    .offset(y = if (isRevealed) wordOffset + 48.dp else 0.dp)
                    .alpha(backAlpha)
            )
        }
    }
}

@Composable
fun FinishedScreen(
    theme: Theme,
    isTestMode: Boolean,
    unlearnedCount: Int,
    totalCount: Int,
    isMaintenanceOrCompleted: Boolean,
    isBasket: Boolean = false,
    onLearnAll: () -> Unit,
    onTestAll: () -> Unit,
    onLearnUnlearned: () -> Unit,
    onTestUnlearned: () -> Unit,
    onFinishSession: () -> Unit,
    onForceAdvance: () -> Unit,
    onBack: () -> Unit
) {
    var showForceAdvanceDialog by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
    ) {
        val title = when {
            isMaintenanceOrCompleted && !isBasket -> "Review finished!"
            unlearnedCount == 0 && isTestMode -> "All words learned!"
            isTestMode -> "You still have $unlearnedCount words to learn"
            else -> "Review finished!"
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        if (isTestMode) {
            val correctCount = totalCount - unlearnedCount
            Text(
                text = "Correct: $correctCount / $totalCount",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Scenario 1: Maintenance or Mastered (Regular Theme)
        if (isMaintenanceOrCompleted && !isBasket) {
            if (isTestMode) {
                val buttonText = if (unlearnedCount == 0) "Complete session" else "Add unlearned to the basket and proceed"
                Button(
                    onClick = onFinishSession,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(buttonText)
                }
            } else {
                // Review Mode
                Button(onClick = onLearnAll, modifier = Modifier.fillMaxWidth()) { Text("Learn All") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onTestAll, modifier = Modifier.fillMaxWidth()) { Text("Test All") }
            }
        } 
        // Scenario 2: Learning Stage (Stages 1-4) OR Basket
        else {
            if (isTestMode) {
                if (unlearnedCount == 0) {
                    // Success: Mastery reached
                    val buttonText = if (isBasket && theme.status == ThemeStatus.LEARNING_STAGE_3) "Complete the basket" else "Complete stage"
                    Button(
                        onClick = onFinishSession,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(buttonText)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onLearnAll, modifier = Modifier.fillMaxWidth()) { Text("Learn All") }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onTestAll, modifier = Modifier.fillMaxWidth()) { Text("Test All") }
                } else {
                    // Failure: Mastery not reached
                    Button(onClick = onLearnAll, modifier = Modifier.fillMaxWidth()) { Text("Learn All") }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onTestAll, modifier = Modifier.fillMaxWidth()) { Text("Test All") }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onLearnUnlearned, modifier = Modifier.fillMaxWidth()) { Text("Learn Unlearned") }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onTestUnlearned, modifier = Modifier.fillMaxWidth()) { Text("Test Unlearned") }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { showForceAdvanceDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Proceed anyway", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            } else {
                // Review Mode
                Button(onClick = onLearnAll, modifier = Modifier.fillMaxWidth()) { Text("Learn All") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onTestAll, modifier = Modifier.fillMaxWidth()) { Text("Test All") }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onBack) {
            Text("Go back to list")
        }
    }

    if (showForceAdvanceDialog) {
        AlertDialog(
            onDismissRequest = { showForceAdvanceDialog = false },
            title = { Text("Force Advance Stage?") },
            text = { Text("You haven't mastered all words yet. If you proceed, the $unlearnedCount missed words will remain in this theme but the stage will be advanced.") },
            confirmButton = {
                Button(
                    onClick = {
                        showForceAdvanceDialog = false
                        onForceAdvance()
                    }
                ) {
                    Text("Proceed Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForceAdvanceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
