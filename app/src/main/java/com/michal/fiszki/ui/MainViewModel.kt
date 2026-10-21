package com.michal.fiszki.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.michal.fiszki.data.ThemeProgressEntity
import com.michal.fiszki.data.repository.LanguageRepository
import com.michal.fiszki.data.repository.ThemeRepository
import com.michal.fiszki.data.repository.WordRepository
import com.michal.fiszki.model.*
import com.michal.fiszki.getBatches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainViewModel(
    private val themeRepository: ThemeRepository,
    private val wordRepository: WordRepository,
    private val languageRepository: LanguageRepository
) : ViewModel() {

    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
        isLenient = true
    }

    private val _languages = MutableStateFlow<List<Language>>(emptyList())
    val languages: StateFlow<List<Language>> = _languages.asStateFlow()

    private val _selectedLanguage = MutableStateFlow<Language?>(null)
    val selectedLanguage: StateFlow<Language?> = _selectedLanguage.asStateFlow()

    private val _themes = MutableStateFlow<List<Theme>>(emptyList())
    val themes: StateFlow<List<Theme>> = _themes.asStateFlow()

    private val _words = MutableStateFlow<Map<Int, List<Word>>>(emptyMap())
    val words: StateFlow<Map<Int, List<Word>>> = _words.asStateFlow()

    private val _basketWords = MutableStateFlow<List<Word>>(emptyList())
    val basketWords: StateFlow<List<Word>> = _basketWords.asStateFlow()

    private val _progress = MutableStateFlow<Map<Int, List<ThemeProgressEntity>>>(emptyMap())
    val progress: StateFlow<Map<Int, List<ThemeProgressEntity>>> = _progress.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _flashcardWords = MutableStateFlow<List<Word>?>(null)
    val flashcardWords: StateFlow<List<Word>?> = _flashcardWords.asStateFlow()

    val calculatedCategories: StateFlow<List<Theme>> = combine(_themes, _words, _progress) { themes, wordsMap, progressMap ->
        val topLevelThemes = themes.filter { it.parentId == null }
        topLevelThemes.map { theme ->
            val subThemesForThis = themes.filter { it.parentId == theme.id }
            val allParts = mutableListOf<Theme>()

            val mainWordsRaw = wordsMap[theme.id] ?: emptyList()
            val mainProgressList = progressMap[theme.id] ?: emptyList()

            allParts.addAll(getBatches(theme.name, mainWordsRaw).mapIndexed { index, part ->
                val p = mainProgressList.find { it.partIndex == index }
                part.copy(
                    status = p?.status ?: ThemeStatus.NOT_STARTED,
                    nextAvailableDate = p?.nextAvailableDate,
                    isInProgress = p?.isInProgress ?: false
                )
            })

            subThemesForThis.forEach { subtheme ->
                val subthemeWordsRaw = wordsMap[subtheme.id] ?: emptyList()
                val progressList = progressMap[subtheme.id] ?: emptyList()
                allParts.addAll(getBatches(subtheme.name, subthemeWordsRaw).mapIndexed { index, part ->
                    val p = progressList.find { it.partIndex == index }
                    part.copy(
                        status = p?.status ?: ThemeStatus.NOT_STARTED,
                        nextAvailableDate = p?.nextAvailableDate,
                        isInProgress = p?.isInProgress ?: false
                    )
                })
            }

            if (allParts.isEmpty()) {
                theme.copy(status = ThemeStatus.NOT_STARTED, wordCount = 0)
            } else {
                val anyInProgress = allParts.any { it.isInProgress }
                val anyPartReady = allParts.any { part ->
                    val isPartLocked = part.nextAvailableDate?.isAvailableNow() == false && !part.isInProgress
                    !isPartLocked && part.status != ThemeStatus.NOT_STARTED
                }
                val anyPartNotStarted = allParts.any { it.status == ThemeStatus.NOT_STARTED }

                val activeParts = allParts.filter { it.status != ThemeStatus.NOT_STARTED && it.status != ThemeStatus.COMPLETED }
                val calculatedStatus = when {
                    anyInProgress -> allParts.filter { it.isInProgress }.minBy { it.status.ordinal }.status
                    anyPartReady -> allParts.filter { ! (it.nextAvailableDate?.isAvailableNow() == false && !it.isInProgress) && it.status != ThemeStatus.NOT_STARTED }.minBy { it.status.ordinal }.status
                    anyPartNotStarted -> ThemeStatus.NOT_STARTED
                    activeParts.isEmpty() -> ThemeStatus.COMPLETED
                    else -> activeParts.minBy { it.status.ordinal }.status
                }

                val nextAvailable = if (anyPartReady || anyInProgress || anyPartNotStarted) null
                else allParts.mapNotNull { it.nextAvailableDate }.minOrNull()

                theme.copy(
                    status = calculatedStatus,
                    isInProgress = anyInProgress,
                    nextAvailableDate = nextAvailable,
                    wordCount = allParts.sumOf { it.wordCount },
                    basketCount = allParts.sumOf { it.basketCount }
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _volumeDownEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val volumeDownEvents = _volumeDownEvents.asSharedFlow()

    private val _volumeUpEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val volumeUpEvents = _volumeUpEvents.asSharedFlow()

    private val _skipEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val skipEvents = _skipEvents.asSharedFlow()

    init {
        observeLanguages()
    }

    fun onVolumeDownReleased() {
        viewModelScope.launch {
            _volumeDownEvents.emit(Unit)
        }
    }

    fun onVolumeUpReleased() {
        viewModelScope.launch {
            _volumeUpEvents.emit(Unit)
        }
    }

    fun onSkipRequested() {
        viewModelScope.launch {
            _skipEvents.emit(Unit)
        }
    }

    fun setFlashcardWords(words: List<Word>?) {
        _flashcardWords.value = words
    }

    private fun observeLanguages() {
        viewModelScope.launch {
            languageRepository.getAllLanguages().collect { langList ->
                _languages.value = langList
            }
        }
    }

    fun selectLanguage(language: Language?) {
        _selectedLanguage.value = language
        if (language != null) {
            loadDataForLanguage(language)
        } else {
            clearData()
        }
    }

    fun clearData() {
        loadJob?.cancel()
        subJobs.forEach { it.cancel() }
        subJobs.clear()
        
        _themes.value = emptyList()
        _words.value = emptyMap()
        _basketWords.value = emptyList()
        _progress.value = emptyMap()
        _isLoading.value = false
    }

    fun clearAllDatabaseData() {
        viewModelScope.launch {
            _isLoading.value = true
            themeRepository.clearAllData()
            languageRepository.deleteAllLanguages()
            selectLanguage(null)
            _isLoading.value = false
        }
    }

    private var loadJob: Job? = null
    private val subJobs = mutableListOf<Job>()

    private fun loadDataForLanguage(language: Language) {
        clearData()
        _isLoading.value = true

        loadJob = viewModelScope.launch {
            val themeJob = launch {
                themeRepository.getThemesForLanguage(language.name).collect { themeList ->
                    _themes.value = themeList
                    _isLoading.value = false
                }
            }
            val wordJob = launch {
                wordRepository.getAllWordsForLanguage(language.name).collect { allWords ->
                    val groupedWords = allWords.groupBy { it.themeId }
                    _words.update { it + groupedWords }
                }
            }
            val progressJob = launch {
                themeRepository.getAllProgressForLanguage(language.name).collect { allProgress ->
                    val groupedProgress = allProgress.groupBy { it.themeId }
                    _progress.update { it + groupedProgress }
                }
            }
            val basketJob = launch {
                wordRepository.getAllBasketWords().collect { basketList ->
                    _basketWords.value = basketList
                }
            }
            subJobs.add(themeJob)
            subJobs.add(wordJob)
            subJobs.add(progressJob)
            subJobs.add(basketJob)
        }
    }

    fun updateWordStatus(word: Word) {
        viewModelScope.launch(Dispatchers.IO) {
            wordRepository.updateWord(word)
        }
    }

    fun clearBasket(words: List<Word>) {
        viewModelScope.launch {
            words.forEach { word ->
                wordRepository.updateWord(word.copy(
                    isInBasket = false, 
                    basketStatus = LearningStatus.UNCHECKED,
                    basketAddedDate = null
                ))
            }
        }
    }

    fun advanceStage(themeId: Int, partIndex: Int, theme: Theme, wordsToReset: List<Word>, wordsToBasket: List<Word> = emptyList()) {
        viewModelScope.launch(Dispatchers.IO) {
            themeRepository.updateProgress(themeId, partIndex, theme)
            
            val updatedWords = if (themeId == -1) {
                // Resetting basket words within their basket
                wordsToReset.map { word ->
                    word.copy(basketStatus = LearningStatus.UNCHECKED)
                }
            } else {
                val basketIds = wordsToBasket.map { it.id }.toSet()
                
                wordsToReset.map { word ->
                    val movingToBasket = basketIds.contains(word.id)
                    val inBasket = movingToBasket || word.isInBasket
                    val bStatus = if (movingToBasket) LearningStatus.UNCHECKED else word.basketStatus
                    val bDate = if (movingToBasket && !word.isInBasket) System.currentTimeMillis() else word.basketAddedDate
                    
                    word.copy(
                        status = LearningStatus.UNCHECKED,
                        isInBasket = inBasket,
                        basketStatus = bStatus,
                        basketAddedDate = bDate
                    )
                }
            }
            wordRepository.updateWords(updatedWords)
        }
    }

    fun completeBasketPart(partIndex: Int, words: List<Word>) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Return words to theme in batch
            val updatedWords = words.map { word ->
                word.copy(
                    isInBasket = false,
                    basketStatus = LearningStatus.UNCHECKED,
                    basketAddedDate = null
                )
            }
            wordRepository.updateWords(updatedWords)

            // 2. Shift progress entries for other baskets
            themeRepository.deleteBasketProgress(partIndex)
            themeRepository.shiftBasketProgress(partIndex)
        }
    }

    fun updateThemeProgress(themeId: Int, partIndex: Int, theme: Theme) {
        viewModelScope.launch(Dispatchers.IO) {
            themeRepository.updateProgress(themeId, partIndex, theme)
        }
    }

    fun importFromJson(jsonString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val imported = json.decodeFromString<ImportLanguage>(jsonString)
                
                val langName = imported.name ?: imported.language?.name ?: "UNKNOWN"
                val langDisplayName = imported.displayName ?: imported.language?.displayName ?: langName
                val langFlag = imported.flag ?: imported.language?.flag ?: "🌐"

                // 1. Pre-process and Backup
                val oldThemes = themeRepository.getThemesForLanguageSuspend(langName)
                val themeProgressMap = mutableMapOf<String, List<ThemeProgressEntity>>()
                val basketWordsMap = mutableMapOf<Pair<String, String>, Word>()

                for (oldThemeEntity in oldThemes) {
                    val progress = themeRepository.getProgressForThemeSuspend(oldThemeEntity.id)
                    themeProgressMap[oldThemeEntity.name] = progress
                    
                    val words = wordRepository.getWordsForThemeSuspend(oldThemeEntity.id)
                    words.filter { it.isInBasket }.forEach { word ->
                        basketWordsMap[oldThemeEntity.name to word.original] = word
                    }
                }

                // 2. Prepare new objects
                val newThemes = mutableListOf<Theme>()
                val newWordsMap = mutableMapOf<Int, List<Word>>()
                val allRestoredProgress = mutableListOf<ThemeProgressEntity>()

                imported.themes.forEachIndexed { tIndex, iTheme ->
                    val parentId = (langName.hashCode() + iTheme.name.hashCode()).let { if (it < 0) -it else it }
                    
                    // Create Parent Theme
                    newThemes.add(Theme(id = parentId, name = iTheme.name, position = tIndex, parentId = null))

                    // Group words by subtheme
                    val wordsBySubtheme = iTheme.words.groupBy { it.subtheme ?: iTheme.name }
                    
                    wordsBySubtheme.entries.forEachIndexed { sIndex, entry ->
                        val subthemeName = entry.key
                        val subthemeWords = entry.value
                        
                        val targetId = if (subthemeName == iTheme.name) {
                            parentId
                        } else {
                            val subthemeId = (parentId + subthemeName.hashCode()).let { if (it < 0) -it else it }
                            newThemes.add(Theme(
                                id = subthemeId,
                                name = subthemeName,
                                position = sIndex,
                                parentId = parentId
                            ))
                            subthemeId
                        }
                        
                        val words = subthemeWords.map { iWord ->
                            val oldBasketWord = basketWordsMap[iTheme.name to iWord.original]
                            Word(
                                themeId = targetId,
                                original = iWord.original,
                                translation = iWord.translation,
                                isInBasket = oldBasketWord?.isInBasket ?: false,
                                basketStatus = oldBasketWord?.basketStatus ?: LearningStatus.UNCHECKED
                            )
                        }
                        
                        val existingWords = newWordsMap[targetId] ?: emptyList()
                        newWordsMap[targetId] = existingWords + words

                        // Restore progress if matching by name
                        themeProgressMap[subthemeName]?.let { oldPList ->
                            allRestoredProgress.addAll(oldPList.map { it.copy(themeId = targetId) })
                        }
                    }
                }

                // 3. Perform atomic batch updates
                val language = Language(langName, langDisplayName, langFlag)
                val currentLanguages = languageRepository.getAllLanguages().first()
                if (!currentLanguages.any { it.name == language.name }) {
                    languageRepository.insertLanguages(currentLanguages + language)
                } else {
                    languageRepository.insertLanguages(currentLanguages.map { if (it.name == language.name) language else it })
                }

                wordRepository.deleteWordsForLanguage(langName)
                themeRepository.deleteThemesForLanguage(langName)

                themeRepository.insertThemes(newThemes, langName)
                newWordsMap.forEach { (tid, wList) ->
                    wordRepository.insertWords(wList, tid)
                }
                themeRepository.insertProgressList(allRestoredProgress)
                
                if (_selectedLanguage.value?.name == language.name) {
                    loadDataForLanguage(language)
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun exportProgress(): String = withContext(Dispatchers.IO) {
        val allThemes = themeRepository.getAllThemesSuspend()
        val themeMap = allThemes.associateBy { it.id }
        val allProgress = themeRepository.getAllProgressSuspend()
        
        val exportData = allProgress.mapNotNull { progress ->
            val theme = themeMap[progress.themeId] ?: return@mapNotNull null
            ProgressExport(
                languageName = theme.language,
                themeName = theme.name,
                partIndex = progress.partIndex,
                status = progress.status,
                lastCompletedDate = progress.lastCompletedDate,
                nextAvailableDate = progress.nextAvailableDate,
                isInProgress = progress.isInProgress
            )
        }
        
        json.encodeToString(exportData)
    }

    fun importProgress(jsonString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val importData = json.decodeFromString<List<ProgressExport>>(jsonString)
                val allThemes = themeRepository.getAllThemesSuspend()
                
                val progressToInsert = importData.mapNotNull { imported ->
                    val matchingTheme = allThemes.find { 
                        it.language == imported.languageName && it.name == imported.themeName 
                    } ?: return@mapNotNull null
                    
                    ThemeProgressEntity(
                        themeId = matchingTheme.id,
                        partIndex = imported.partIndex,
                        status = imported.status,
                        lastCompletedDate = imported.lastCompletedDate,
                        nextAvailableDate = imported.nextAvailableDate,
                        isInProgress = imported.isInProgress
                    )
                }
                
                if (progressToInsert.isNotEmpty()) {
                    themeRepository.insertProgressList(progressToInsert)
                    _selectedLanguage.value?.let { loadDataForLanguage(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

}

class MainViewModelFactory(
    private val themeRepository: ThemeRepository,
    private val wordRepository: WordRepository,
    private val languageRepository: LanguageRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(themeRepository, wordRepository, languageRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
