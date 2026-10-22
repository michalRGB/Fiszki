package com.michal.fiszki.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.michal.fiszki.data.AppDatabase
import com.michal.fiszki.data.LanguageEntity
import com.michal.fiszki.data.ThemeEntity
import com.michal.fiszki.data.ThemeProgressEntity
import com.michal.fiszki.data.WordEntity
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
import kotlinx.serialization.json.Json
import java.time.LocalDate

class MainViewModel(
    private val database: AppDatabase,
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

    private val _operationMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val operationMessages = _operationMessages.asSharedFlow()

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


    fun removeWordFromBasket(word: Word) {
        viewModelScope.launch(Dispatchers.IO) {
            database.withTransaction {
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
            database.withTransaction {
                themeRepository.updateProgress(themeId, partIndex, theme)

                val updatedWords = if (themeId == -1) {
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
    }

    fun completeBasketPart(partIndex: Int, words: List<Word>) {
        viewModelScope.launch(Dispatchers.IO) {
            database.withTransaction {
                wordRepository.updateWords(words.map { word ->
                    word.copy(
                        isInBasket = false,
                        basketStatus = LearningStatus.UNCHECKED,
                        basketAddedDate = null
                    )
                })
                themeRepository.deleteBasketProgress(partIndex)
                themeRepository.shiftBasketProgress(partIndex)
            }
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
                val oldThemesById = oldThemes.associateBy { it.id }
                val usedThemeIds = themeRepository.getAllThemesSuspend().mapTo(mutableSetOf()) { it.id }
                var nextThemeId = 1
                fun allocateThemeId(): Int {
                    while (nextThemeId in usedThemeIds) {
                        check(nextThemeId < Int.MAX_VALUE) { "No theme IDs available" }
                        nextThemeId++
                    }
                    val allocatedId = nextThemeId
                    usedThemeIds.add(allocatedId)
                    if (nextThemeId < Int.MAX_VALUE) nextThemeId++
                    return allocatedId
                }

                val themeProgressMap = mutableMapOf<Pair<String, String>, List<ThemeProgressEntity>>()
                val basketWordsMap = mutableMapOf<Triple<String, String, String>, Word>()

                for ((id, name, _, _, parentId) in oldThemes) {
                    val parentThemeName = parentId
                        ?.let { oldThemesById[it]?.name }
                        ?: name
                    val progress = themeRepository.getProgressForThemeSuspend(id)
                    themeProgressMap[parentThemeName to name] = progress
                    
                    val words = wordRepository.getWordsForThemeSuspend(id)
                    words.filter { it.isInBasket }.forEach { word ->
                        basketWordsMap[Triple(parentThemeName, name, word.original)] = word
                    }
                }

                // 2. Prepare new objects
                val newThemes = mutableListOf<Theme>()
                val newWordsMap = mutableMapOf<Int, List<Word>>()
                val allRestoredProgress = mutableListOf<ThemeProgressEntity>()

                imported.themes.forEachIndexed { tIndex, iTheme ->
                    val parentId = allocateThemeId()
                    
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
                            val subthemeId = allocateThemeId()
                            newThemes.add(Theme(
                                id = subthemeId,
                                name = subthemeName,
                                position = sIndex,
                                parentId = parentId
                            ))
                            subthemeId
                        }
                        
                        val words = subthemeWords.map { iWord ->
                            val basketKey = Triple(iTheme.name, subthemeName, iWord.original)
                            val oldBasketWord = basketWordsMap[basketKey]
                            Word(
                                themeId = targetId,
                                original = iWord.original,
                                translation = iWord.translation,
                                isInBasket = oldBasketWord?.isInBasket ?: false,
                                basketStatus = oldBasketWord?.basketStatus ?: LearningStatus.UNCHECKED,
                                basketAddedDate = oldBasketWord?.basketAddedDate
                            )
                        }
                        
                        val existingWords = newWordsMap[targetId] ?: emptyList()
                        newWordsMap[targetId] = existingWords + words

                        // Restore progress if matching by name
                        themeProgressMap[iTheme.name to subthemeName]?.let { oldPList ->
                            allRestoredProgress.addAll(oldPList.map { it.copy(themeId = targetId) })
                        }
                    }
                }

                // 3. Perform atomic batch updates
                val language = Language(langName, langDisplayName, langFlag)
                database.withTransaction {
                    val currentLanguages = languageRepository.getAllLanguages().first()
                    if (!currentLanguages.any { it.name == language.name }) {
                        languageRepository.insertLanguages(currentLanguages + language)
                    } else {
                        languageRepository.insertLanguages(currentLanguages.map { if (it.name == language.name) language else it })
                    }

                    wordRepository.deleteWordsForLanguage(langName)
                    themeRepository.deleteProgressForLanguage(langName)
                    themeRepository.deleteThemesForLanguage(langName)

                    themeRepository.insertThemes(newThemes, langName)
                    newWordsMap.forEach { (tid, wList) ->
                        wordRepository.insertWords(wList, tid)
                    }
                    themeRepository.insertProgressList(allRestoredProgress)
                }

                _operationMessages.emit("Language imported successfully")
                
                if (_selectedLanguage.value?.name == language.name) {
                    loadDataForLanguage(language)
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                _operationMessages.emit("Could not import language: ${e.message ?: "Invalid file"}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        val archive = database.withTransaction {
            BackupArchive(
                formatVersion = 1,
                languages = database.languageDao().getAllLanguagesSuspend().map {
                    BackupLanguage(it.name, it.displayName, it.flag)
                },
                themes = database.themeDao().getAllThemesSuspend().map {
                    BackupTheme(it.id, it.name, it.language, it.position, it.parentId)
                },
                words = database.wordDao().getAllWordsSuspend().map {
                    BackupWord(
                        id = it.id,
                        themeId = it.themeId,
                        original = it.original,
                        translation = it.translation,
                        status = it.status.name,
                        isInBasket = it.isInBasket,
                        basketStatus = it.basketStatus.name,
                        basketAddedDate = it.basketAddedDate,
                        subtheme = it.subtheme
                    )
                },
                progress = database.themeDao().getAllProgressSuspend().map {
                    BackupProgress(
                        themeId = it.themeId,
                        partIndex = it.partIndex,
                        status = it.status.name,
                        lastCompletedDate = it.lastCompletedDate?.toString(),
                        nextAvailableDate = it.nextAvailableDate?.toString(),
                        isInProgress = it.isInProgress
                    )
                }
            )
        }
        json.encodeToString(archive)
    }

    fun restoreBackup(jsonString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val archive = json.decodeFromString<BackupArchive>(jsonString)
                require(archive.formatVersion == 1) { "Unsupported backup version: ${archive.formatVersion}" }

                val languageNames = archive.languages.map { it.name }
                val themeIds = archive.themes.map { it.id }
                val wordIds = archive.words.map { it.id }
                val progressKeys = archive.progress.map { it.themeId to it.partIndex }
                require(languageNames.distinct().size == languageNames.size) { "Duplicate language names" }
                require(themeIds.distinct().size == themeIds.size) { "Duplicate theme IDs" }
                require(wordIds.distinct().size == wordIds.size) { "Duplicate word IDs" }
                require(progressKeys.distinct().size == progressKeys.size) { "Duplicate progress entries" }

                val languageNameSet = languageNames.toSet()
                val themeIdSet = themeIds.toSet()
                require(archive.themes.all { it.language in languageNameSet }) { "Theme references a missing language" }
                require(archive.themes.all { it.parentId == null || it.parentId in themeIdSet }) { "Theme references a missing parent" }
                require(archive.words.all { it.themeId in themeIdSet }) { "Word references a missing theme" }
                require(archive.progress.all { it.themeId == -1 || it.themeId in themeIdSet }) { "Progress references a missing theme" }
                require(archive.progress.all { it.partIndex >= 0 }) { "Progress has an invalid part index" }

                val languages = archive.languages.map { LanguageEntity(it.name, it.displayName, it.flag) }
                val themes = archive.themes.map { ThemeEntity(it.id, it.name, it.language, it.position, it.parentId) }
                val words = archive.words.map {
                    WordEntity(
                        id = it.id,
                        themeId = it.themeId,
                        original = it.original,
                        translation = it.translation,
                        status = LearningStatus.valueOf(it.status),
                        isInBasket = it.isInBasket,
                        basketStatus = LearningStatus.valueOf(it.basketStatus),
                        basketAddedDate = it.basketAddedDate,
                        subtheme = it.subtheme
                    )
                }
                val progress = archive.progress.map {
                    ThemeProgressEntity(
                        themeId = it.themeId,
                        partIndex = it.partIndex,
                        status = ThemeStatus.valueOf(it.status),
                        lastCompletedDate = it.lastCompletedDate?.let(LocalDate::parse),
                        nextAvailableDate = it.nextAvailableDate?.let(LocalDate::parse),
                        isInProgress = it.isInProgress
                    )
                }

                database.withTransaction {
                    database.themeDao().deleteAllProgress()
                    database.themeDao().deleteAllWords()
                    database.themeDao().deleteAllThemes()
                    database.languageDao().deleteAllLanguages()
                    database.languageDao().insertLanguages(languages)
                    database.themeDao().insertThemes(themes)
                    database.wordDao().insertWords(words)
                    database.themeDao().insertProgressList(progress)
                }

                val selectedLanguage = _selectedLanguage.value
                if (selectedLanguage != null) {
                    val restoredLanguage = languages.find { it.name == selectedLanguage.name }
                    if (restoredLanguage == null) selectLanguage(null)
                    else selectLanguage(Language(restoredLanguage.name, restoredLanguage.displayName, restoredLanguage.flag))
                }
                _operationMessages.emit("Backup restored successfully")
            } catch (e: Exception) {
                e.printStackTrace()
                _operationMessages.emit("Could not restore backup: ${e.message ?: "Invalid file"}")
            } finally {
                _isLoading.value = false
            }
        }
    }

}

class MainViewModelFactory(
    private val database: AppDatabase,
    private val themeRepository: ThemeRepository,
    private val wordRepository: WordRepository,
    private val languageRepository: LanguageRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(database, themeRepository, wordRepository, languageRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
