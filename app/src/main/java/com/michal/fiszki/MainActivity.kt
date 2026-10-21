package com.michal.fiszki

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import com.michal.fiszki.data.AppDatabase
import com.michal.fiszki.data.repository.LanguageRepository
import com.michal.fiszki.data.repository.ThemeRepository
import com.michal.fiszki.data.repository.WordRepository
import com.michal.fiszki.model.*
import com.michal.fiszki.ui.MainViewModel
import com.michal.fiszki.ui.MainViewModelFactory
import com.michal.fiszki.ui.flashcards.FlashcardScreen
import com.michal.fiszki.ui.language.LanguageScreen
import com.michal.fiszki.ui.stats.StatsScreen
import com.michal.fiszki.ui.theme.FiszkiTheme
import com.michal.fiszki.ui.themes.ThemeListScreen
import com.michal.fiszki.ui.words.WordListScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        val database = AppDatabase.getDatabase(this)
        val themeRepository = ThemeRepository.getInstance(database.themeDao())
        val wordRepository = WordRepository.getInstance(database.wordDao())
        val languageRepository = LanguageRepository.getInstance(database.languageDao())
        
        val factory = MainViewModelFactory(themeRepository, wordRepository, languageRepository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setContent {
            FiszkiTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val isFlashcardActive = viewModel.flashcardWords.value != null
        if (!isFlashcardActive) return super.onKeyDown(keyCode, event)

        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.onVolumeDownReleased()
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                viewModel.onVolumeUpReleased()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val languages by viewModel.languages.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val themes by viewModel.themes.collectAsState()
    val wordsMap by viewModel.words.collectAsState()
    val progressMap by viewModel.progress.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val basketWords by viewModel.basketWords.collectAsState()
    val flashcardWords by viewModel.flashcardWords.collectAsState()

    var selectedCategoryId by remember { mutableStateOf<Int?>(null) }
    var selectedSubThemePartIndex by remember { mutableStateOf<Int?>(null) }
    var isTestMode by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var isBasketMode by remember { mutableStateOf(false) }

    val mainThemeListState = rememberLazyListState()
    val subThemeListState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val jsonString = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { it.readText() }
            jsonString?.let { viewModel.importFromJson(it) }
        }
    }

    val progressExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val jsonString = viewModel.exportProgress()
                context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { it.write(jsonString) }
            }
        }
    }

    val progressImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val jsonString = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { it.readText() }
            jsonString?.let { viewModel.importProgress(it) }
        }
    }

    val onLanguageSelected = { language: Language? ->
        viewModel.selectLanguage(language)
        selectedCategoryId = null
        selectedSubThemePartIndex = null
        viewModel.setFlashcardWords(null)
    }

    val basketThemes = remember(basketWords, selectedLanguage, progressMap) {
        if (basketWords.isEmpty()) emptyList()
        else {
            val progressList = progressMap[-1] ?: emptyList()
            getBatches("Basket List", basketWords).mapIndexed { index, theme ->
                val p = progressList.find { it.partIndex == index }
                theme.copy(
                    id = -1,
                    status = p?.status ?: ThemeStatus.NOT_STARTED,
                    nextAvailableDate = p?.nextAvailableDate,
                    isInProgress = p?.isInProgress ?: false,
                    categoryId = index
                )
            }
        }
    }

    val savedUnlearnedWords = remember(basketWords) {
        basketWords
    }

    val currentSubThemes = remember(selectedCategoryId, themes, wordsMap, progressMap) {
        selectedCategoryId?.let { catId ->
            val selectedCategory = themes.find { it.id == catId }
            val subthemes = themes.filter { it.parentId == catId }
            val mainWordsRaw = wordsMap[catId] ?: emptyList()
            val allParts = mutableListOf<Theme>()

            if (mainWordsRaw.isNotEmpty()) {
                val progressList = progressMap[catId] ?: emptyList()
                val themeName = selectedCategory?.name ?: "General"
                allParts.addAll(getBatches(themeName, mainWordsRaw).mapIndexed { index, theme ->
                    val p = progressList.find { it.partIndex == index }
                    theme.copy(
                        id = catId,
                        status = p?.status ?: ThemeStatus.NOT_STARTED,
                        nextAvailableDate = p?.nextAvailableDate,
                        isInProgress = p?.isInProgress ?: false,
                        categoryId = index
                    )
                })
            }

            subthemes.forEach { subtheme ->
                val subthemeWordsRaw = wordsMap[subtheme.id] ?: emptyList()
                val progressList = progressMap[subtheme.id] ?: emptyList()
                allParts.addAll(getBatches(subtheme.name, subthemeWordsRaw).mapIndexed { index, part ->
                    val p = progressList.find { it.partIndex == index }
                    part.copy(
                        id = subtheme.id,
                        status = p?.status ?: ThemeStatus.NOT_STARTED,
                        nextAvailableDate = p?.nextAvailableDate,
                        isInProgress = p?.isInProgress ?: false,
                        categoryId = index
                    )
                }.ifEmpty {
                    listOf(Theme(
                        id = subtheme.id,
                        name = subtheme.name,
                        status = ThemeStatus.NOT_STARTED,
                        categoryId = 0
                    ))
                })
            }
            
            if (allParts.isEmpty() && subthemes.isEmpty()) {
                allParts.add(Theme(
                    id = catId,
                    name = selectedCategory?.name ?: "",
                    status = ThemeStatus.NOT_STARTED,
                    categoryId = 0
                ))
            }
            allParts
        } ?: emptyList()
    }

    val navigateBack = {
        when {
            showStats -> showStats = false
            flashcardWords != null -> viewModel.setFlashcardWords(null)
            selectedSubThemePartIndex != null -> {
                if (isBasketMode) {
                    selectedSubThemePartIndex = null
                    isBasketMode = false
                } else {
                    selectedSubThemePartIndex = null
                    if (currentSubThemes.size <= 1) {
                        selectedCategoryId = null
                    }
                }
            }
            selectedCategoryId != null -> selectedCategoryId = null
            isBasketMode -> isBasketMode = false
            else -> onLanguageSelected(null)
        }
    }

    BackHandler(enabled = selectedLanguage != null) {
        navigateBack()
    }

    val categories by viewModel.calculatedCategories.collectAsState()

    if (isLoading && selectedLanguage != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentLang = selectedLanguage
    when {
        currentLang == null -> {
            LanguageScreen(
                languages = languages,
                onLanguageSelected = { lang -> onLanguageSelected(lang) },
                onImportRequested = { importLauncher.launch("application/json") },
                onExportProgress = { progressExportLauncher.launch("fiszki_progress.json") },
                onImportProgress = { progressImportLauncher.launch("application/json") }
            )
        }

        showStats -> {
            StatsScreen(
                selectedLanguage = currentLang,
                themes = categories,
                onBack = { showStats = false }
            )
        }
        
        selectedCategoryId == null && !isBasketMode -> {
            ThemeListScreen(
                selectedLanguage = currentLang,
                themes = categories + basketThemes,
                state = mainThemeListState,
                onThemeSelected = { theme -> 
                    val basketIdx = basketThemes.indexOf(theme)
                    if (basketIdx != -1) {
                        isBasketMode = true
                        selectedCategoryId = null // Ensure category is cleared when entering basket
                        selectedSubThemePartIndex = basketIdx
                    } else {
                        selectedCategoryId = theme.id
                        val subthemes = themes.filter { it.parentId == theme.id }
                        val mainWords = wordsMap[theme.id] ?: emptyList()
                        val totalPartsCount = getBatches(theme.name, mainWords).size +
                                            subthemes.sumOf { getBatches(it.name, wordsMap[it.id] ?: emptyList()).size }

                        if (totalPartsCount <= 1) {
                            selectedSubThemePartIndex = 0
                        }
                    }
                },
                onThemeOverride = { theme, status, date ->
                    if (theme.id == -1) {
                        // Override for Basket
                        viewModel.advanceStage(
                            -1,
                            theme.categoryId,
                            theme.copy(status = status, nextAvailableDate = date, isInProgress = false),
                            emptyList() // No words to reset for just an override
                        )
                    } else {
                        val subThemesForThis = themes.filter { it.parentId == theme.id }
                        val mainWords = wordsMap[theme.id] ?: emptyList()
                        val mainPartsCount = getBatches(theme.name, mainWords).size
                        repeat(mainPartsCount) { i ->
                            val partWords = getWordsForBatch(i + 1, mainWords)
                            viewModel.advanceStage(theme.id, i, theme.copy(status = status, nextAvailableDate = date, isInProgress = false), partWords)
                        }

                        subThemesForThis.forEach { subtheme ->
                            val words = wordsMap[subtheme.id] ?: emptyList()
                            val partsCount = getBatches(subtheme.name, words).size.coerceAtLeast(1)
                            repeat(partsCount) { i ->
                                val partWords = getWordsForBatch(i + 1, words)
                                viewModel.advanceStage(subtheme.id, i, subtheme.copy(status = status, nextAvailableDate = date, isInProgress = false), partWords)
                            }
                        }

                        if (mainPartsCount == 0 && subThemesForThis.isEmpty()) {
                            viewModel.advanceStage(theme.id, 0, theme.copy(status = status, nextAvailableDate = date, isInProgress = false), emptyList())
                        }
                    }
                },
                onBack = { navigateBack() },
                actions = {
                    IconButton(onClick = { showStats = true }) {
                        Icon(imageVector = Icons.Default.BarChart, contentDescription = "Stats")
                    }
                }
            )
        }

        selectedSubThemePartIndex == null && !isBasketMode -> {
            ThemeListScreen(
                selectedLanguage = currentLang,
                themes = currentSubThemes,
                state = subThemeListState,
                onThemeSelected = { subTheme ->
                    selectedSubThemePartIndex = currentSubThemes.indexOf(subTheme)
                },
                onThemeOverride = { theme, status, date ->
                    val words = wordsMap[theme.id] ?: emptyList()
                    val partWords = getWordsForBatch(theme.categoryId + 1, words)
                    viewModel.advanceStage(theme.id, theme.categoryId, theme.copy(status = status, nextAvailableDate = date, isInProgress = false), partWords)
                },
                onBack = { navigateBack() }
            )
        }

        flashcardWords != null -> {
            val subTheme = if (isBasketMode) {
                basketThemes.getOrNull(selectedSubThemePartIndex ?: 0)
            } else {
                currentSubThemes.getOrNull(selectedSubThemePartIndex ?: 0)
            }

            if (subTheme != null) {
                FlashcardScreen(
                    theme = subTheme,
                    words = flashcardWords!!,
                    isTestModeInitially = isTestMode,
                    isBasket = isBasketMode,
                    onBack = { navigateBack() },
                    onWordLearned = { word, learned ->
                        if (isBasketMode) {
                            viewModel.updateWordStatus(word.copy(basketStatus = if (learned) LearningStatus.LEARNED else LearningStatus.NOT_LEARNED))
                        } else {
                            viewModel.updateWordStatus(word.copy(status = if (learned) LearningStatus.LEARNED else LearningStatus.NOT_LEARNED))
                        }
                    },
                    onSessionFinished = { unlearnedInSession, forceAdvance ->
                        if (isBasketMode && subTheme.isInProgress) {
                            if (unlearnedInSession.isEmpty()) {
                                if (subTheme.status == ThemeStatus.LEARNING_STAGE_3) {
                                    viewModel.completeBasketPart(subTheme.categoryId, flashcardWords!!)
                                } else {
                                    val (nextStatus, interval) = CycleConfig.getNextStage(subTheme.status)
                                    viewModel.advanceStage(
                                        -1,
                                        subTheme.categoryId,
                                        subTheme.copy(
                                            status = nextStatus,
                                            lastCompletedDate = LocalDate.now(),
                                            nextAvailableDate = LocalDate.now().plus(interval),
                                            isInProgress = false
                                        ),
                                        wordsToReset = flashcardWords!!
                                    )
                                }
                            } else {
                                viewModel.updateThemeProgress(-1, subTheme.categoryId, subTheme.copy(isInProgress = false))
                            }
                        } else if (!isBasketMode && subTheme.isInProgress) {
                            val isMaintenance = subTheme.status.ordinal >= ThemeStatus.LEARNING_STAGE_4.ordinal
                            val shouldAdvance = isMaintenance || unlearnedInSession.isEmpty() || forceAdvance

                            if (shouldAdvance) {
                                val (nextStatus, interval) = CycleConfig.getNextStage(subTheme.status)
                                val allWords = wordsMap[subTheme.id] ?: emptyList()
                                val partWords = getWordsForBatch(subTheme.categoryId + 1, allWords)

                                viewModel.advanceStage(
                                    subTheme.id,
                                    subTheme.categoryId,
                                    subTheme.copy(
                                        status = nextStatus,
                                        lastCompletedDate = LocalDate.now(),
                                        nextAvailableDate = LocalDate.now().plus(interval),
                                        isInProgress = false
                                    ),
                                    wordsToReset = partWords,
                                    wordsToBasket = if (isMaintenance && !forceAdvance) unlearnedInSession else emptyList()
                                )
                            } else {
                                // Learning mode but missed words: Reset "In Progress" so user can retry
                                viewModel.updateThemeProgress(subTheme.id, subTheme.categoryId, subTheme.copy(isInProgress = false))
                            }
                        }
                        viewModel.setFlashcardWords(null)
                        selectedSubThemePartIndex = null
                        if (isBasketMode) {
                            isBasketMode = false
                        } else {
                            if (currentSubThemes.size <= 1) {
                                selectedCategoryId = null
                            }
                        }
                    },
                    volumeDownEvents = viewModel.volumeDownEvents,
                    volumeUpEvents = viewModel.volumeUpEvents,
                    skipEvents = viewModel.skipEvents
                )
            } else {
                SideEffect { navigateBack() }
            }
        }

        else -> {
            val partIdx = selectedSubThemePartIndex
            if (partIdx == null) {
                // If no part is selected, we shouldn't be in the WordList view
                return@MainScreen
            }
            
            val subTheme = if (isBasketMode) {
                basketThemes.getOrNull(partIdx)
            } else {
                currentSubThemes.getOrNull(partIdx)
            }

            if (subTheme != null) {
                val themeWords = if (isBasketMode) {
                    getWordsForBatch(partIdx + 1, savedUnlearnedWords)
                } else {
                    val allWords = wordsMap[subTheme.id] ?: emptyList()
                    getWordsForBatch(subTheme.categoryId + 1, allWords).filter { !it.isInBasket }
                }

                WordListScreen(
                    theme = subTheme,
                    words = themeWords,
                    isBasket = isBasketMode,
                    onBack = { navigateBack() },
                    onPlayFlashcards = { selectedWords, testMode ->
                        val isDue = subTheme.nextAvailableDate == null || subTheme.nextAvailableDate.isAvailableNow()
                        if (isDue) {
                            if (isBasketMode) {
                                viewModel.updateThemeProgress(-1, subTheme.categoryId, subTheme.copy(isInProgress = true))
                            } else {
                                viewModel.updateThemeProgress(subTheme.id, subTheme.categoryId, subTheme.copy(isInProgress = true))
                            }
                        }
                        isTestMode = testMode
                        viewModel.setFlashcardWords(selectedWords)
                    },
                    onSaveToBasket = { unlearned: List<Word> ->
                        if (!isBasketMode) {
                            val (nextStatus, interval) = CycleConfig.getNextStage(subTheme.status)
                            viewModel.advanceStage(
                                subTheme.id,
                                subTheme.categoryId,
                                subTheme.copy(
                                    status = nextStatus,
                                    lastCompletedDate = LocalDate.now(),
                                    nextAvailableDate = LocalDate.now().plus(interval),
                                    isInProgress = false
                                ),
                                wordsToReset = themeWords,
                                wordsToBasket = unlearned
                            )
                            selectedSubThemePartIndex = null
                            if (currentSubThemes.size <= 1) {
                                selectedCategoryId = null
                            }
                        }
                    },
                    onRemoveFromBasket = { word ->
                        viewModel.updateWordStatus(word.copy(
                            isInBasket = false, 
                            basketStatus = LearningStatus.UNCHECKED,
                            basketAddedDate = null
                        ))
                    },
                    onClearBasket = {
                        if (subTheme.status == ThemeStatus.LEARNING_STAGE_3) {
                            viewModel.completeBasketPart(subTheme.categoryId, themeWords)
                        } else {
                            val (nextStatus, interval) = CycleConfig.getNextStage(subTheme.status)
                            viewModel.advanceStage(
                                -1,
                                subTheme.categoryId,
                                subTheme.copy(
                                    status = nextStatus,
                                    lastCompletedDate = LocalDate.now(),
                                    nextAvailableDate = LocalDate.now().plus(interval),
                                    isInProgress = false
                                ),
                                wordsToReset = themeWords
                            )
                        }
                        selectedSubThemePartIndex = null
                        isBasketMode = false
                        navigateBack()
                    }
                )
            }
        }
    }
}

fun getBatches(themeName: String, words: List<Word>): List<Theme> {
    val chunks = getChunkedWords(words)
    if (chunks.isEmpty()) return emptyList()
    return chunks.mapIndexed { index, batch ->
        Theme(
            id = 0,
            name = if (chunks.size == 1) themeName else "$themeName (Part ${index + 1})",
            wordCount = batch.size,
            basketCount = batch.count { it.isInBasket }
        )
    }
}

fun getWordsForBatch(batchIndex: Int, words: List<Word>): List<Word> {
    val chunks = getChunkedWords(words)
    return if (batchIndex > 0 && batchIndex <= chunks.size) chunks[batchIndex - 1] else emptyList()
}

private fun getChunkedWords(words: List<Word>): List<List<Word>> {
    if (words.isEmpty()) return emptyList()
    val batchSize = 50
    val chunks = words.chunked(batchSize).toMutableList()
    if (chunks.size > 1 && chunks.last().size < 20) {
        val last = chunks.removeAt(chunks.size - 1)
        val prev = chunks.removeAt(chunks.size - 1)
        chunks.add(prev + last)
    }
    return chunks
}
