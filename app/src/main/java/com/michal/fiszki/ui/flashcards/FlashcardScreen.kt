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
import androidx.compose.ui.zIndex
import com.michal.fiszki.model.LearningStatus
import com.michal.fiszki.model.Theme
import com.michal.fiszki.model.ThemeStatus
import com.michal.fiszki.model.Word
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private data class ExitingFlashcard(
    val id: Int,
    val word: Word,
    val startX: Float,
    val startY: Float,
    val targetX: Float,
    val targetY: Float,
    val exitDurationMillis: Int,
    val swipedRight: Boolean,
    val isSkip: Boolean,
    val isFlipped: Boolean,
    val showBoth: Boolean,
    val showTranslation: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(
    theme: Theme,
    words: List<Word>,
    allWords: List<Word> = words,
    isTestModeInitially: Boolean,
    isBasket: Boolean = false,
    isUnlearnedSessionInitially: Boolean = false,
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
    var isUnlearnedSession by remember { mutableStateOf(isUnlearnedSessionInitially) }
    var showTranslation by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var isFlipped by remember(isTestMode) { mutableStateOf(isTestMode) }
    var showBothGlobal by remember(isTestMode) { mutableStateOf(false) }

    // Animation trigger states
    var poppedCardId by remember { mutableStateOf<Int?>(null) }
    var currentCardExiting by remember { mutableStateOf(false) }
    var interactionLocked by remember { mutableStateOf(false) }
    var exitingCards by remember { mutableStateOf(emptyList<ExitingFlashcard>()) }
    var nextExitId by remember { mutableIntStateOf(0) }
    var pendingDragResult by remember { mutableStateOf<Boolean?>(null) }
    var pendingSkipTransition by remember { mutableStateOf(false) }
    val screenScope = rememberCoroutineScope()

    val currentWord = if (currentIndex < currentWords.size) currentWords[currentIndex] else null
    val unlearnedWords = allWords.filter { word ->
        (if (isBasket) word.basketStatus else word.status) != LearningStatus.LEARNED
    }

    fun clearTransitionState() {
        poppedCardId = null
        currentCardExiting = false
        interactionLocked = false
        exitingCards = emptyList()
        pendingDragResult = null
        pendingSkipTransition = false
    }
    
    val isMaintenanceOrCompleted = remember(theme.status) { 
        theme.status.ordinal >= ThemeStatus.LEARNING_STAGE_4.ordinal
    }

    // Centralized reset for triggers and state when card changes
    LaunchedEffect(currentIndex, currentWords, isFinished) {
        if (isFinished) {
            clearTransitionState()
            return@LaunchedEffect
        }
        // We now reset showTranslation in processResult/processSkip for better atomicity
        currentCardExiting = false
        pendingDragResult = null
        pendingSkipTransition = false
        if (interactionLocked) {
            withFrameNanos { }
            interactionLocked = false
        }
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

    fun startSwipe(
        swipedRight: Boolean,
        startX: Float = 0f,
        startY: Float = 0f,
        targetX: Float = if (swipedRight) 2500f else -2500f,
        targetY: Float = -2500f,
        isSkip: Boolean = false,
        exitDurationMillis: Int = 600
    ) {
        val word = currentWords.getOrNull(currentIndex) ?: return
        if (isFinished || currentCardExiting || interactionLocked) return

        val hasNextCard = currentIndex < currentWords.lastIndex
        if (isSkip && !hasNextCard) return
        poppedCardId = currentWords.getOrNull(currentIndex + 1)?.id
        val exitId = nextExitId++
        exitingCards = exitingCards + ExitingFlashcard(
            id = exitId,
            word = word,
            startX = startX,
            startY = startY,
            targetX = targetX,
            targetY = targetY,
            exitDurationMillis = exitDurationMillis,
            swipedRight = swipedRight,
            isSkip = isSkip,
            isFlipped = isFlipped,
            showBoth = showBothGlobal,
            showTranslation = showTranslation
        )
        currentCardExiting = true
        interactionLocked = true

        if (isSkip) {
            pendingSkipTransition = true
        } else if (hasNextCard) {
            pendingDragResult = swipedRight
        } else {
            screenScope.launch {
                kotlinx.coroutines.delay(exitDurationMillis.toLong())
                processResult(swipedRight)
                currentCardExiting = false
            }
        }
    }

    fun startButtonSwipe(swipedRight: Boolean, isSkip: Boolean = false) {
        if (currentCardExiting || interactionLocked) return
        startSwipe(swipedRight, targetY = if (isSkip) 2500f else -2500f, isSkip = isSkip)
    }

    LaunchedEffect(volumeDownEvents) {
        volumeDownEvents?.collect {
            if (!isFinished) startButtonSwipe(true)
        }
    }

    LaunchedEffect(volumeUpEvents) {
        volumeUpEvents?.collect {
            if (!isFinished) {
                if (isTestMode) startButtonSwipe(false)
                else if (!currentCardExiting && !interactionLocked && currentIndex > 0) {
                    currentIndex--
                }
            }
        }
    }

    LaunchedEffect(skipEvents) {
        skipEvents?.collect {
            if (!isFinished && isTestMode && currentIndex < currentWords.size - 1) {
                startButtonSwipe(true, isSkip = true)
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
                        isUnlearnedSession = isUnlearnedSession,
                        onLearnAll = {
                            clearTransitionState()
                            currentWords = allWords
                            currentIndex = 0
                            isTestMode = false
                            showBothGlobal = false
                            isFlipped = false
                            isFinished = false
                            unlearnedWordsInSession = emptySet()
                            isUnlearnedSession = false
                        },
                        onTestAll = {
                            clearTransitionState()
                            currentWords = allWords
                            currentIndex = 0
                            isTestMode = true
                            showBothGlobal = false
                            isFlipped = true
                            isFinished = false
                            unlearnedWordsInSession = emptySet()
                            isUnlearnedSession = false
                        },
                        onLearnUnlearned = {
                            clearTransitionState()
                            currentWords = unlearnedWords
                            currentIndex = 0
                            isTestMode = false
                            showBothGlobal = false
                            isFlipped = false
                            isFinished = false
                            unlearnedWordsInSession = emptySet()
                            isUnlearnedSession = true
                        },
                        onTestUnlearned = {
                            clearTransitionState()
                            currentWords = unlearnedWords
                            currentIndex = 0
                            isTestMode = true
                            showBothGlobal = false
                            isFlipped = true
                            isFinished = false
                            unlearnedWordsInSession = emptySet()
                            isUnlearnedSession = true
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
                                    isBackground = true,
                                    triggerPop = poppedCardId == currentWords[nextIndex].id,
                                    onPopComplete = {
                                        when {
                                            pendingDragResult != null -> {
                                                val result = pendingDragResult!!
                                                pendingDragResult = null
                                                processResult(result)
                                            }
                                            pendingSkipTransition -> {
                                                pendingSkipTransition = false
                                                processSkip()
                                            }
                                        }
                                        if (pendingDragResult == null && !pendingSkipTransition) {
                                            currentCardExiting = false
                                        }
                                    }
                                )
                            }
                        }

                        if (!currentCardExiting) {
                            key(currentWord.id) {
                                Flashcard(
                                    key = currentIndex,
                                    word = currentWord,
                                    modifier = Modifier.zIndex(1f),
                                    showTranslation = showTranslation,
                                    isTestMode = isTestMode,
                                    isFlipped = isFlipped,
                                    showBoth = showBothGlobal,
                                    onClick = { showTranslation = !showTranslation },
                                    onSwipe = {},
                                    isInputEnabled = !interactionLocked,
                                    onSwipeReleased = { swipedRight, startX, startY, targetX, targetY ->
                                        startSwipe(swipedRight, startX, startY, targetX, targetY)
                                    },
                                    onSkip = {}
                                )
                            }
                        }

                        exitingCards.forEach { exitingCard ->
                            key(exitingCard.id) {
                                Flashcard(
                                    key = exitingCard.id,
                                    word = exitingCard.word,
                                    showTranslation = exitingCard.showTranslation,
                                    isTestMode = isTestMode,
                                    isFlipped = exitingCard.isFlipped,
                                    showBoth = exitingCard.showBoth,
                                    onClick = {},
                                    onSwipe = {},
                                    isInputEnabled = false,
                                    initialOffsetX = exitingCard.startX,
                                    initialOffsetY = exitingCard.startY,
                                    exitTargetX = exitingCard.targetX,
                                    exitTargetY = exitingCard.targetY,
                                    animationDurationMillis = exitingCard.exitDurationMillis,
                                    triggerSwipeRight = !exitingCard.isSkip && exitingCard.swipedRight,
                                    triggerSwipeLeft = !exitingCard.isSkip && !exitingCard.swipedRight,
                                    triggerSkip = exitingCard.isSkip,
                                    onAnimationComplete = {
                                        exitingCards = exitingCards.filterNot { it.id == exitingCard.id }
                                    }
                                )
                            }
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
                                    if (!currentCardExiting && !interactionLocked) {
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
                                onClick = { startButtonSwipe(false) },
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
                                onClick = { startButtonSwipe(true) },
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
                                    onClick = { startButtonSwipe(true, isSkip = true) },
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
                                    startButtonSwipe(true)
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
    triggerPop: Boolean = false,
    onPopComplete: () -> Unit = {},
    onSwipeReleased: (Boolean, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    isInputEnabled: Boolean = true,
    initialOffsetX: Float = 0f,
    initialOffsetY: Float = 0f,
    exitTargetX: Float? = null,
    exitTargetY: Float? = null,
    animationDurationMillis: Int = 500,
    onAnimationComplete: () -> Unit = {}
) {
    val offsetX = remember(word, key) { Animatable(initialOffsetX) }
    val offsetY = remember(word, key) { Animatable(initialOffsetY) }
    val alpha = remember(word, key) { Animatable(1f) }
    val scale = remember(word, key) { Animatable(if (isBackground && !triggerPop) 0.92f else 1f) }
    val rotation = offsetX.value / 10f
    val scope = rememberCoroutineScope()

    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val currentOnSwipeReleased by rememberUpdatedState(onSwipeReleased)
    val currentOnPopComplete by rememberUpdatedState(onPopComplete)
    val currentOnSkip by rememberUpdatedState(onSkip)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnAnimationComplete by rememberUpdatedState(onAnimationComplete)

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val swipeRightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f).compositeOver(surfaceColor)
    val swipeLeftColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f).compositeOver(surfaceColor)
    val cardShape = MaterialTheme.shapes.medium

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

    LaunchedEffect(isBackground, triggerPop) {
        if (isBackground && triggerPop) {
            scale.animateTo(1f, animationSpec = tween(250, easing = FastOutSlowInEasing))
            currentOnPopComplete()
        } else if (isBackground) {
            launch { scale.snapTo(0.92f) }
            launch { alpha.snapTo(1f) }
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
            val targetX = exitTargetX ?: if (isRight) 2500f else -2500f
            val targetY = exitTargetY ?: if (isActualSkip) 2500f else -2500f
            val animDuration = animationDurationMillis

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
        shape = cardShape,
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
            .graphicsLayer(
                translationX = offsetX.value,
                translationY = offsetY.value,
                rotationZ = rotation,
                scaleX = scale.value,
                scaleY = scale.value,
                alpha = alpha.value,
                shape = cardShape,
                clip = true
            )
            .then(
                if (isBackground || !isInputEnabled) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(word, key) {
                            detectTapGestures(onTap = { currentOnClick() })
                        }
                        .pointerInput(word, key) {
                            detectDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        val totalDrag = sqrt((offsetX.value * offsetX.value + offsetY.value * offsetY.value).toDouble()).toFloat()
                                        if (totalDrag > 400f) {
                                            val angle = atan2(offsetY.value.toDouble(), offsetX.value.toDouble())
                                            val targetX = (cos(angle) * 1500).toFloat()
                                            val targetY = (sin(angle) * 1500).toFloat()
                                            val swipeRight = offsetX.value > 0
                                            currentOnSwipeReleased(swipeRight, offsetX.value, offsetY.value, targetX, targetY)
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
                        }
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
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

            val backAlpha = if (isBackground) {
                if (showBoth) 1f else 0f
            }
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
    isUnlearnedSession: Boolean = false,
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

        if (isUnlearnedSession && !isTestMode) {
            Button(onClick = onTestAll, modifier = Modifier.fillMaxWidth()) { Text("Test All") }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onLearnAll, modifier = Modifier.fillMaxWidth()) { Text("Learn All") }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onTestUnlearned, modifier = Modifier.fillMaxWidth()) { Text("Test Unlearned") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onLearnUnlearned, modifier = Modifier.fillMaxWidth()) { Text("Learn Unlearned") }
        }
        // Scenario 1: Maintenance or Mastered (Regular Theme)
        else if (isMaintenanceOrCompleted && !isBasket) {
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
