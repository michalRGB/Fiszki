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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.michal.fiszki.model.LearningStatus
import com.michal.fiszki.model.CycleConfig
import com.michal.fiszki.model.Theme
import com.michal.fiszki.model.ThemeStatus
import com.michal.fiszki.model.Word
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

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
    skipEvents: kotlinx.coroutines.flow.SharedFlow<Unit>? = null,
    onVolumeControlsChanged: (Boolean) -> Unit = {}
) {
    val wordsById = remember(words, allWords) { (words + allWords).associateBy { it.id } }
    val wordListSaver = listSaver(
        save = { list -> list.map { it.id } },
        restore = { ids -> ids.mapNotNull { wordsById[it] } }
    )
    val missedWordsSaver = listSaver(
        save = { missed -> missed.map { it.id } },
        restore = { ids -> ids.mapNotNull { wordsById[it] }.toSet() }
    )
    var currentWords by rememberSaveable(stateSaver = wordListSaver) { mutableStateOf(words) }
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    var isTestMode by rememberSaveable { mutableStateOf(isTestModeInitially) }
    var unlearnedWordsInSession by rememberSaveable(stateSaver = missedWordsSaver) { mutableStateOf(setOf()) }
    var isUnlearnedSession by rememberSaveable { mutableStateOf(isUnlearnedSessionInitially) }
    var showTranslation by rememberSaveable { mutableStateOf(false) }
    var isFinished by rememberSaveable { mutableStateOf(false) }
    var isFlipped by rememberSaveable(isTestMode) { mutableStateOf(isTestMode) }
    var showBothGlobal by rememberSaveable(isTestMode) { mutableStateOf(false) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val updateVolumeControls by rememberUpdatedState(onVolumeControlsChanged)
    val hasActiveCard = !isFinished && currentWords.isNotEmpty()
    DisposableEffect(lifecycle, hasActiveCard) {
        fun update() = updateVolumeControls(hasActiveCard && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycle.addObserver(observer)
        update()
        onDispose {
            lifecycle.removeObserver(observer)
            updateVolumeControls(false)
        }
    }

    // Animation trigger states
    var poppedCardId by remember { mutableStateOf<Int?>(null) }
    var exitingCards by remember { mutableStateOf(emptyList<ExitingFlashcard>()) }
    var nextExitId by remember { mutableIntStateOf(0) }

    val currentWord = if (currentIndex < currentWords.size) currentWords[currentIndex] else null
    val unlearnedWords = allWords.filter { word ->
        (if (isBasket) word.basketStatus else word.status) != LearningStatus.LEARNED
    }

    fun clearTransitionState() {
        poppedCardId = null
        exitingCards = emptyList()
    }

    fun goToPreviousCard() {
        if (currentIndex > 0 && !isFinished) {
            clearTransitionState()
            showTranslation = false
            currentIndex--
        }
    }
    
    val isMaintenanceOrCompleted = remember(theme.status) { 
        theme.status.ordinal >= ThemeStatus.LEARNING_STAGE_4.ordinal
    }

    val advanceToNextCard = {
        if (currentWords.getOrNull(currentIndex) != null && !isFinished) {
            showTranslation = false // Reset here atomically
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

    fun shuffleRemaining() {
        val seen = currentWords.take(currentIndex)
        val remaining = currentWords.drop(currentIndex).shuffled()
        currentWords = seen + remaining
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
        if (isFinished) return

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
        // Commit progress at release. Animation callbacks only remove visual copies
        // of outgoing cards, so shuffle and recreation cannot cancel an answer.
        if (isSkip) {
            processSkip()
        } else {
            if (isTestMode) {
                onWordLearned(word, swipedRight)
                unlearnedWordsInSession = if (swipedRight) {
                    unlearnedWordsInSession - word
                } else {
                    unlearnedWordsInSession + word
                }
            }
            advanceToNextCard()
        }
    }

    fun startButtonSwipe(swipedRight: Boolean, isSkip: Boolean = false) {
        startSwipe(
            swipedRight,
            targetY = if (isSkip) 2500f else -2500f,
            isSkip = isSkip,
            exitDurationMillis = 1000
        )
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
                else goToPreviousCard()
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
                            shuffleRemaining()
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
                if (isFinished && exitingCards.isEmpty()) {
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
                        if (!isFinished && nextIndex < currentWords.size) {
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

                        if (!isFinished) {
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
                                    triggerPop = poppedCardId == currentWord.id,
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
                                    modifier = Modifier.zIndex(2f),
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
                                onClick = { goToPreviousCard() },
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
    val scale = remember(word, key) { Animatable(if (isBackground || triggerPop) 0.92f else 1f) }
    val promotion = remember(word, key) { Animatable(if (isBackground || triggerPop) 0f else 1f) }
    val rotation = offsetX.value / 10f
    val scope = rememberCoroutineScope()

    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val currentOnSwipeReleased by rememberUpdatedState(onSwipeReleased)
    val currentOnPopComplete by rememberUpdatedState(onPopComplete)
    val currentOnSkip by rememberUpdatedState(onSkip)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnAnimationComplete by rememberUpdatedState(onAnimationComplete)

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val dimmedSurfaceColor = surfaceColor.copy(alpha = 0.58f).compositeOver(MaterialTheme.colorScheme.background)
    val swipeRightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f).compositeOver(surfaceColor)
    val swipeLeftColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f).compositeOver(surfaceColor)
    val targetSurfaceColor = when {
        !isBackground && isTestMode && offsetX.value > 100f -> swipeRightColor
        !isBackground && isTestMode && offsetX.value < -100f -> swipeLeftColor
        else -> surfaceColor
    }
    val cardSurfaceColor = lerp(dimmedSurfaceColor, targetSurfaceColor, promotion.value)
    val textOpacity = 0.58f + 0.42f * promotion.value
    // Explicit, opaque text colors avoid both Material's default color switching
    // at the final surface color and a text layer changing compositing at alpha 1.
    val frontColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textOpacity)
        .compositeOver(cardSurfaceColor)
    val backColor = MaterialTheme.colorScheme.primary.copy(alpha = textOpacity)
        .compositeOver(cardSurfaceColor)
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
        if (triggerPop) {
            coroutineScope {
                launch { scale.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing)) }
                launch { promotion.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing)) }
            }
            currentOnPopComplete()
        } else if (isBackground) {
            launch { scale.snapTo(0.92f) }
            launch { promotion.snapTo(0f) }
        } else {
            launch { scale.snapTo(1f) }
            launch { promotion.snapTo(1f) }
        }
    }

    // Listen for external triggers (volume buttons or on-screen buttons)
    LaunchedEffect(triggerSwipeRight, triggerSwipeLeft, triggerSkip) {
        if (triggerSwipeRight || triggerSwipeLeft || triggerSkip) {
            val isRight = triggerSwipeRight || triggerSkip

            // triggerSwipeRight (Know/Vol Down) flies to the top right corner.
            // triggerSwipeLeft (Don't Know/Vol Up) flies to the top left corner.
            // triggerSkip (Skip Button) flies to the bottom right corner.
            val targetX = exitTargetX ?: if (isRight) 2500f else -2500f
            val targetY = exitTargetY ?: if (triggerSkip) 2500f else -2500f

            launch { offsetX.animateTo(targetX, animationSpec = tween(animationDurationMillis, easing = FastOutSlowInEasing)) }
            launch { offsetY.animateTo(targetY, animationSpec = tween(animationDurationMillis, easing = FastOutSlowInEasing)) }
            if (!triggerSkip || !isTestMode) {
                launch { alpha.animateTo(0f, animationSpec = tween(animationDurationMillis)) }
            }

            kotlinx.coroutines.delay(animationDurationMillis.toLong().milliseconds)
            currentOnAnimationComplete()
            if (triggerSkip) {
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
            // Animate an opaque tint rather than the card layer's transparency,
            // so the next card's text stays hidden throughout the promotion.
            containerColor = cardSurfaceColor,
            contentColor = frontColor
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
                color = backColor,
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
    var showForceAdvanceDialog by rememberSaveable { mutableStateOf(false) }
    val completesBasket = isBasket && CycleConfig.isFinalBasketStage(theme.status)

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
                    val buttonText = if (completesBasket) "Complete the basket" else "Complete stage"
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
            title = { Text(if (completesBasket) "Complete the basket?" else "Force Advance Stage?") },
            text = {
                Text(if (completesBasket) {
                    "Completing this basket will return all its words, including missed words, to their original themes."
                } else {
                    "You haven't mastered all words yet. If you proceed, the $unlearnedCount missed words will remain in this ${if (isBasket) "basket" else "theme"} but the stage will be advanced."
                })
            },
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
