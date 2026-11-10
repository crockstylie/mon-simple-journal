package fr.antoinehory.monsimplejournal.ui.archive

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.antoinehory.monsimplejournal.data.preferences.UserPreferencesRepository
import fr.antoinehory.monsimplejournal.data.repository.JournalRepository
import fr.antoinehory.monsimplejournal.domain.DiacriticsMatcher
import fr.antoinehory.monsimplejournal.domain.JournalDateFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Cible de navigation lorsqu'un saut `▲` / `▼` en mode recherche bascule vers le journal du jour (`EditorScreen`).
 */
data class CrossJournalSearchTarget(
    val isoDate: String,
    val query: String,
    val occurrenceIndex: Int,
)

/**
 * État UI de l'écran de consultation en lecture seule d'un ancien journal (`ReaderScreen`).
 */
data class ReaderUiState(
    val isoDate: String = "",
    val formattedTitle: String = "",
    val content: String = "",
    val isLoading: Boolean = true,
    val highlightQuery: String = "",
    val matchRanges: List<IntRange> = emptyList(),
    val activeOccurrenceIndex: Int = 0,
    val globalOccurrenceIndex: Int = 0,
    val globalTotalOccurrences: Int = 0,
    val archiveIndex: Int = 0,
    val archiveTotalCount: Int = 0,
    val exportMessageResId: Int? = null,
    val pendingEditorNavigation: CrossJournalSearchTarget? = null,
) {
    /** Offset exact du caractère sur lequel scroller automatiquement lors d'un saut depuis la recherche. */
    val targetCharOffset: Int?
        get() = matchRanges.getOrNull(activeOccurrenceIndex)?.first

    /** Contenu Markdown complet formaté avec le titre en en-tête `# Titre`. */
    val markdownDocument: String
        get() = "# $formattedTitle\n\n$content\n"
}

/**
 * ViewModel dédié à la consultation **strictement en lecture seule** d'un journal passé (ADR-002),
 * gérant également :
 * - la navigation d'un journal d'archive au suivant/précédent (`▲` / `▼`) en mode lecture simple,
 * - la navigation inter-occurrences et inter-journaux (`▲` / `▼`) en mode recherche active.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(
    isoDate: String,
    initialHighlightQuery: String = "",
    initialOccurrenceIndex: Int = 0,
    private val activeSessionDate: LocalDate = LocalDate.now(),
    private val journalRepository: JournalRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val currentDateFlow = MutableStateFlow(
        runCatching { LocalDate.parse(isoDate) }.getOrElse { LocalDate.now().minusDays(1) },
    )
    private val highlightQueryFlow = MutableStateFlow(initialHighlightQuery.trim())
    private var requestedOccurrenceIndex: Int = initialOccurrenceIndex.coerceAtLeast(0)

    private var cachedArchiveDates: List<LocalDate> = emptyList()
    private var cachedGlobalOccurrences: List<Pair<String, Int>> = emptyList()

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            isoDate = currentDateFlow.value.toString(),
            formattedTitle = JournalDateFormatter.formatTitle(currentDateFlow.value),
            highlightQuery = initialHighlightQuery.trim(),
            activeOccurrenceIndex = initialOccurrenceIndex.coerceAtLeast(0),
        ),
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        observeEntryArchivesAndGlobalSearch()
    }

    private fun observeEntryArchivesAndGlobalSearch() {
        val entryFlow = currentDateFlow.flatMapLatest { date ->
            journalRepository.observeEntryByDate(date)
        }
        val archiveDatesFlow = journalRepository.observePastEntries(activeSessionDate)
            .map { entries -> entries.map { it.localDate } }
            .distinctUntilChanged()
        val globalSearchFlow = highlightQueryFlow.flatMapLatest { query ->
            if (query.isEmpty()) {
                flowOf(emptyList())
            } else {
                journalRepository.searchOccurrences(query)
            }
        }

        viewModelScope.launch {
            combine(
                currentDateFlow,
                entryFlow,
                userPreferencesRepository.userPreferencesFlow,
                archiveDatesFlow,
                globalSearchFlow,
            ) { currentDate, entry, prefs, archiveDates, globalOccurrences ->
                cachedArchiveDates = archiveDates
                cachedGlobalOccurrences = globalOccurrences.map { it.isoDate to it.occurrenceIndex }

                val text = entry?.content.orEmpty()
                val query = highlightQueryFlow.value
                val ranges = if (query.isNotEmpty()) {
                    DiacriticsMatcher.findMatchRanges(text, query)
                } else {
                    emptyList()
                }

                val currentState = _uiState.value
                val switchingJournal = currentState.isoDate != currentDate.toString()
                val targetLocalIndex = if (switchingJournal) {
                    if (requestedOccurrenceIndex < 0 && ranges.isNotEmpty()) {
                        ranges.lastIndex
                    } else {
                        requestedOccurrenceIndex
                    }
                } else {
                    currentState.activeOccurrenceIndex
                }
                val safeIndex = if (ranges.isEmpty()) {
                    0
                } else {
                    targetLocalIndex.coerceIn(0, ranges.lastIndex)
                }

                val currentIso = currentDate.toString()
                val archiveIdx = archiveDates.indexOf(currentDate).coerceAtLeast(0)
                val globalIdx = cachedGlobalOccurrences
                    .indexOfFirst { (iso, occIdx) -> iso == currentIso && occIdx == safeIndex }
                    .coerceAtLeast(0)

                currentState.copy(
                    isoDate = currentIso,
                    formattedTitle = JournalDateFormatter.formatTitle(
                        date = currentDate,
                        style = prefs.dateFormatStyle,
                    ),
                    content = text,
                    isLoading = false,
                    highlightQuery = query,
                    matchRanges = ranges,
                    activeOccurrenceIndex = safeIndex,
                    globalOccurrenceIndex = globalIdx,
                    globalTotalOccurrences = cachedGlobalOccurrences.size,
                    archiveIndex = archiveIdx,
                    archiveTotalCount = archiveDates.size,
                )
            }.collect { updatedState ->
                _uiState.value = updatedState
            }
        }
    }

    /**
     * Action du bouton `▼` (suivant) :
     * - En mode recherche : passe à l'occurrence suivante (dans ce journal ou dans le journal suivant ayant un résultat).
     * - En mode archive simple : passe au journal d'archive suivant dans la liste.
     */
    fun onNextClicked() {
        val state = _uiState.value
        if (state.highlightQuery.isNotEmpty() && state.matchRanges.isNotEmpty()) {
            stepSearchOccurrence(delta = 1)
        } else {
            stepArchiveJournal(delta = 1)
        }
    }

    /**
     * Action du bouton `▲` (précédent) :
     * - En mode recherche : passe à l'occurrence précédente (dans ce journal ou dans le journal précédent ayant un résultat).
     * - En mode archive simple : passe au journal d'archive précédent dans la liste.
     */
    fun onPreviousClicked() {
        val state = _uiState.value
        if (state.highlightQuery.isNotEmpty() && state.matchRanges.isNotEmpty()) {
            stepSearchOccurrence(delta = -1)
        } else {
            stepArchiveJournal(delta = -1)
        }
    }

    private fun stepArchiveJournal(delta: Int) {
        val dates = cachedArchiveDates
        if (dates.size <= 1) return
        val currentIdx = dates.indexOf(currentDateFlow.value).takeIf { it >= 0 } ?: 0
        val nextIdx = (currentIdx + delta).mod(dates.size)
        requestedOccurrenceIndex = 0
        currentDateFlow.value = dates[nextIdx]
    }

    private fun stepSearchOccurrence(delta: Int) {
        val state = _uiState.value
        val globalList = cachedGlobalOccurrences
        if (globalList.size > 1) {
            val currentGlobalIdx = globalList
                .indexOfFirst { (iso, occIdx) -> iso == state.isoDate && occIdx == state.activeOccurrenceIndex }
                .takeIf { it >= 0 } ?: 0
            val nextGlobalIdx = (currentGlobalIdx + delta).mod(globalList.size)
            val (targetIso, targetOccIdx) = globalList[nextGlobalIdx]

            if (targetIso == state.isoDate) {
                val safeIdx = targetOccIdx.coerceIn(0, (state.matchRanges.size - 1).coerceAtLeast(0))
                val newGlobalIdx = globalList
                    .indexOfFirst { (iso, occIdx) -> iso == state.isoDate && occIdx == safeIdx }
                    .coerceAtLeast(0)
                _uiState.update {
                    it.copy(
                        activeOccurrenceIndex = safeIdx,
                        globalOccurrenceIndex = newGlobalIdx,
                    )
                }
            } else if (targetIso == activeSessionDate.toString()) {
                _uiState.update {
                    it.copy(
                        pendingEditorNavigation = CrossJournalSearchTarget(
                            isoDate = targetIso,
                            query = state.highlightQuery,
                            occurrenceIndex = targetOccIdx,
                        ),
                    )
                }
            } else {
                requestedOccurrenceIndex = targetOccIdx
                runCatching { LocalDate.parse(targetIso) }.onSuccess { targetDate ->
                    currentDateFlow.value = targetDate
                }
            }
        } else {
            val total = state.matchRanges.size
            if (total <= 1) return
            _uiState.update {
                it.copy(activeOccurrenceIndex = (state.activeOccurrenceIndex + delta).mod(total))
            }
        }
    }

    fun onEditorNavigationConsumed() {
        _uiState.update { it.copy(pendingEditorNavigation = null) }
    }

    fun onClearHighlight() {
        highlightQueryFlow.value = ""
        _uiState.update { state ->
            state.copy(
                highlightQuery = "",
                matchRanges = emptyList(),
                activeOccurrenceIndex = 0,
                globalOccurrenceIndex = 0,
                globalTotalOccurrences = 0,
            )
        }
    }

    /**
     * Exporte ce journal individuel au format universel `.md` vers l'[uri] choisie via SAF.
     */
    fun exportSingleMarkdown(
        contentResolver: ContentResolver,
        uri: Uri,
        successMsgRes: Int,
        errorMsgRes: Int,
    ) {
        val markdown = _uiState.value.markdownDocument
        viewModelScope.launch {
            val result = runCatching {
                withContext(ioDispatcher) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.writer(Charsets.UTF_8).use { writer ->
                            writer.write(markdown)
                        }
                    } ?: error("Impossible d'ouvrir le flux de sortie SAF")
                }
            }
            _uiState.update { state ->
                state.copy(
                    exportMessageResId = if (result.isSuccess) successMsgRes else errorMsgRes,
                )
            }
        }
    }

    fun onExportMessageShown() {
        _uiState.update { it.copy(exportMessageResId = null) }
    }

    companion object {
        fun provideFactory(
            isoDate: String,
            initialHighlightQuery: String = "",
            initialOccurrenceIndex: Int = 0,
            activeSessionDate: LocalDate = LocalDate.now(),
            journalRepository: JournalRepository,
            userPreferencesRepository: UserPreferencesRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(
                    isoDate = isoDate,
                    initialHighlightQuery = initialHighlightQuery,
                    initialOccurrenceIndex = initialOccurrenceIndex,
                    activeSessionDate = activeSessionDate,
                    journalRepository = journalRepository,
                    userPreferencesRepository = userPreferencesRepository,
                ) as T
            }
        }
    }
}
