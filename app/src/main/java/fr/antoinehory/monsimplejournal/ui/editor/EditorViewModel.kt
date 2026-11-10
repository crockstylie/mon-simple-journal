package fr.antoinehory.monsimplejournal.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.antoinehory.monsimplejournal.data.preferences.UserPreferencesRepository
import fr.antoinehory.monsimplejournal.data.repository.JournalRepository
import fr.antoinehory.monsimplejournal.domain.DiacriticsMatcher
import fr.antoinehory.monsimplejournal.domain.JournalDateFormatter
import fr.antoinehory.monsimplejournal.domain.MarkdownFormatAction
import fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter
import fr.antoinehory.monsimplejournal.ui.archive.CrossJournalSearchTarget
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/**
 * ViewModel de l'écran principal d'écriture quotidienne (`EditorScreen`).
 *
 * Responsabilités clés :
 * 1. **Ouverture directe sur le journal du jour** (`LocalDate.now(clock)`).
 * 2. **Continuité à minuit (`23h55` ➔ `00h00`)** : la `sessionDate` reste verrouillée tant que l'écran reste
 *    actif (aucune coupure en pleine rédaction à minuit). En revanche, si l'utilisateur quitte l'application
 *    (`ON_STOP`) et y revient (`ON_RESUME`) alors que la date calendaire a changé, une nouvelle entrée
 *    du nouveau jour est ouverte immédiatement.
 * 3. **Sauvegarde automatique temps réel** (`debounce(300ms)`) + sauvegarde immédiate (`flushImmediate()`) sur `ON_STOP`.
 * 4. **Outils de mise en forme Markdown** (Titres H1/H2/H3, Gras, Italique, Souligné, Listes UL/OL avec continuation auto)
 *    et **Horodatage intra-journée** (`— HH:mm — `) inséré directement à la position du curseur.
 */
@OptIn(FlowPreview::class)
class EditorViewModel(
    private val journalRepository: JournalRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private var activeSessionDate: LocalDate = LocalDate.now(clock)
    private var hasStoppedInBackground: Boolean = false
    private var lastSavedContent: String = ""
    private var cachedGlobalOccurrences: List<Pair<String, Int>> = emptyList()

    private val _uiState = MutableStateFlow(
        EditorUiState(
            sessionDate = activeSessionDate,
            formattedTitle = JournalDateFormatter.formatTitle(activeSessionDate),
        ),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val pendingSaveFlow = MutableSharedFlow<Pair<LocalDate, String>>(extraBufferCapacity = 64)
    private var loadSessionJob: Job? = null
    private var globalSearchJob: Job? = null

    init {
        observePreferences()
        observeDebouncedSaves()
        loadSessionEntry(activeSessionDate)
    }

    fun onEvent(event: EditorUiEvent) {
        when (event) {
            is EditorUiEvent.OnTextChanged -> handleTextChanged(
                newValue = event.newValue,
                eventSessionDate = event.sessionDate,
                allowListContinuation = true,
            )
            is EditorUiEvent.OnFormatActionClicked -> applyMarkdownFormat(event.action)
            EditorUiEvent.OnInsertTimestampClicked -> insertCurrentTimestamp()
            EditorUiEvent.OnLifecycleStopped -> handleLifecycleStopped()
            EditorUiEvent.OnLifecycleResumed -> handleLifecycleResumed()
            is EditorUiEvent.OnHighlightOccurrenceRequested -> applySearchHighlight(
                query = event.query,
                occurrenceIndex = event.occurrenceIndex,
            )
            EditorUiEvent.OnNextOccurrenceClicked -> stepOccurrence(delta = 1)
            EditorUiEvent.OnPreviousOccurrenceClicked -> stepOccurrence(delta = -1)
            EditorUiEvent.OnClearHighlightClicked -> clearHighlight()
            EditorUiEvent.OnArchiveNavigationConsumed -> {
                _uiState.update { it.copy(pendingArchiveNavigation = null) }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                _uiState.update { current ->
                    current.copy(
                        dateFormatStyle = prefs.dateFormatStyle,
                        formattedTitle = JournalDateFormatter.formatTitle(
                            date = current.sessionDate,
                            style = prefs.dateFormatStyle,
                        ),
                        fontScale = prefs.fontScaleOption.scale,
                        keepScreenOn = prefs.keepScreenOn,
                    )
                }
            }
        }
    }

    private fun observeDebouncedSaves() {
        viewModelScope.launch {
            pendingSaveFlow
                .debounce(AUTO_SAVE_DEBOUNCE_MS)
                .collect { (dateToSave, contentToSave) ->
                    persistIfNeeded(dateToSave, contentToSave)
                }
        }
    }

    private fun loadSessionEntry(
        date: LocalDate,
        previousDateToFlush: LocalDate? = null,
        previousContentToFlush: String? = null,
    ) {
        loadSessionJob?.cancel()
        activeSessionDate = date
        lastSavedContent = ""

        // Réinitialisation synchrone immédiate de l'état UI sur le thread principal
        // pour éviter qu'un événement clavier/IME de la session précédente ne fuite sur le nouveau jour.
        _uiState.update { current ->
            val switchingDay = current.sessionDate != date
            current.copy(
                sessionDate = date,
                formattedTitle = JournalDateFormatter.formatTitle(date, current.dateFormatStyle),
                textFieldValue = TextFieldValue(""),
                isLoading = true,
                highlightQuery = if (switchingDay) "" else current.highlightQuery,
                matchRanges = emptyList(),
                activeOccurrenceIndex = 0,
            )
        }

        loadSessionJob = viewModelScope.launch {
            if (previousDateToFlush != null && previousContentToFlush != null) {
                persistIfNeeded(previousDateToFlush, previousContentToFlush)
            }

            val existingEntry = journalRepository.loadActiveSessionEntry(date)
            if (activeSessionDate != date) return@launch

            val initialText = existingEntry?.content.orEmpty()
            lastSavedContent = initialText

            _uiState.update { current ->
                val ranges = if (current.highlightQuery.isNotBlank()) {
                    DiacriticsMatcher.findMatchRanges(initialText, current.highlightQuery)
                } else {
                    emptyList()
                }
                current.copy(
                    sessionDate = date,
                    formattedTitle = JournalDateFormatter.formatTitle(date, current.dateFormatStyle),
                    textFieldValue = TextFieldValue(
                        text = initialText,
                        selection = TextRange(initialText.length),
                    ),
                    isLoading = false,
                    matchRanges = ranges,
                )
            }
        }
    }

    private fun handleTextChanged(
        newValue: TextFieldValue,
        eventSessionDate: LocalDate? = null,
        allowListContinuation: Boolean = false,
    ) {
        if (eventSessionDate != null && eventSessionDate != activeSessionDate) return
        val currentState = _uiState.value
        if (currentState.sessionDate != activeSessionDate) return
        if (currentState.isLoading && eventSessionDate != null) return

        val effectiveValue = if (allowListContinuation) {
            MarkdownTextFormatter.handleTextChangeWithListContinuation(
                previous = currentState.textFieldValue,
                current = newValue,
            )
        } else {
            newValue
        }

        val textChanged = effectiveValue.text != currentState.textFieldValue.text

        val updatedRanges = if (textChanged && currentState.highlightQuery.isNotBlank()) {
            DiacriticsMatcher.findMatchRanges(effectiveValue.text, currentState.highlightQuery)
        } else {
            currentState.matchRanges
        }

        _uiState.update { state ->
            state.copy(
                textFieldValue = effectiveValue,
                matchRanges = updatedRanges,
                activeOccurrenceIndex = if (updatedRanges.isEmpty()) {
                    0
                } else {
                    state.activeOccurrenceIndex.coerceIn(0, updatedRanges.lastIndex)
                },
            )
        }

        if (textChanged) {
            pendingSaveFlow.tryEmit(activeSessionDate to effectiveValue.text)
        }
    }

    private fun applyMarkdownFormat(action: MarkdownFormatAction) {
        val formatted = MarkdownTextFormatter.applyFormat(
            value = _uiState.value.textFieldValue,
            action = action,
        )
        handleTextChanged(
            newValue = formatted,
            eventSessionDate = activeSessionDate,
            allowListContinuation = false,
        )
    }

    private fun insertCurrentTimestamp() {
        val marker = JournalDateFormatter.formatTimestampMarker(LocalTime.now(clock))
        val currentTfv = _uiState.value.textFieldValue
        val text = currentTfv.text
        val start = currentTfv.selection.min.coerceIn(0, text.length)
        val end = currentTfv.selection.max.coerceIn(0, text.length)

        val prefixNeedsSpace = start > 0 && !text[start - 1].isWhitespace()
        val insertion = if (prefixNeedsSpace) " $marker" else marker

        val newText = StringBuilder(text.length + insertion.length)
            .append(text, 0, start)
            .append(insertion)
            .append(text, end, text.length)
            .toString()

        val newCursorPos = start + insertion.length
        handleTextChanged(
            newValue = TextFieldValue(
                text = newText,
                selection = TextRange(newCursorPos),
            ),
            eventSessionDate = activeSessionDate,
        )
    }

    private fun handleLifecycleStopped() {
        hasStoppedInBackground = true
        flushImmediate()
    }

    private fun handleLifecycleResumed() {
        val wasStoppedInBackground = hasStoppedInBackground
        hasStoppedInBackground = false

        val today = LocalDate.now(clock)
        if (today == activeSessionDate) return

        // Tant que l'écran reste actif (sans passage par ON_STOP), minuit ne coupe pas l'écriture.
        // En revanche, dès que l'utilisateur a quitté l'application (ON_STOP) et revient (ON_RESUME)
        // alors que la date calendaire a changé, on bascule immédiatement sur la nouvelle entrée du jour.
        if (wasStoppedInBackground) {
            val previousDate = activeSessionDate
            val previousContent = _uiState.value.textFieldValue.text
            loadSessionEntry(
                date = today,
                previousDateToFlush = previousDate,
                previousContentToFlush = previousContent,
            )
        }
    }

    /**
     * Recharge l'entrée de la session active depuis Room (appelé après un import `.zip` / `.md`).
     */
    fun reloadActiveSessionFromStorage() {
        loadSessionEntry(activeSessionDate)
    }

    /**
     * Force la sauvegarde immédiate du texte courant dans Room (utilisé sur `ON_STOP` et avant changement de session).
     */
    fun flushImmediate() {
        val state = _uiState.value
        if (state.isLoading || state.sessionDate != activeSessionDate) return
        val dateToSave = activeSessionDate
        val contentToSave = state.textFieldValue.text
        viewModelScope.launch {
            persistIfNeeded(dateToSave, contentToSave)
        }
    }

    private suspend fun persistIfNeeded(date: LocalDate, content: String) {
        if (date == activeSessionDate && content == lastSavedContent) return
        journalRepository.saveActiveSessionContent(
            activeSessionDate = date,
            targetDate = date,
            content = content,
        )
        if (date == activeSessionDate) {
            lastSavedContent = content
        }
    }

    private fun applySearchHighlight(query: String, occurrenceIndex: Int) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            clearHighlight()
            return
        }
        val text = _uiState.value.textFieldValue.text
        val ranges = DiacriticsMatcher.findMatchRanges(text, trimmed)
        val safeIndex = if (ranges.isEmpty()) 0 else occurrenceIndex.coerceIn(0, ranges.lastIndex)
        _uiState.update { state ->
            state.copy(
                highlightQuery = trimmed,
                matchRanges = ranges,
                activeOccurrenceIndex = safeIndex,
            )
        }

        globalSearchJob?.cancel()
        globalSearchJob = viewModelScope.launch {
            journalRepository.searchOccurrences(trimmed).collect { occurrences ->
                cachedGlobalOccurrences = occurrences.map { it.isoDate to it.occurrenceIndex }
                val activeIso = activeSessionDate.toString()
                _uiState.update { state ->
                    val globalIdx = cachedGlobalOccurrences
                        .indexOfFirst { (iso, occIdx) -> iso == activeIso && occIdx == state.activeOccurrenceIndex }
                        .coerceAtLeast(0)
                    state.copy(
                        globalOccurrenceIndex = globalIdx,
                        globalTotalOccurrences = cachedGlobalOccurrences.size,
                    )
                }
            }
        }
    }

    private fun stepOccurrence(delta: Int) {
        val state = _uiState.value
        val globalList = cachedGlobalOccurrences
        val activeIso = activeSessionDate.toString()

        if (globalList.size > 1) {
            val currentGlobalIdx = globalList
                .indexOfFirst { (iso, occIdx) -> iso == activeIso && occIdx == state.activeOccurrenceIndex }
                .takeIf { it >= 0 } ?: 0
            val nextGlobalIdx = (currentGlobalIdx + delta).mod(globalList.size)
            val (targetIso, targetOccIdx) = globalList[nextGlobalIdx]

            if (targetIso == activeIso) {
                val safeIdx = targetOccIdx.coerceIn(0, (state.matchRanges.size - 1).coerceAtLeast(0))
                val newGlobalIdx = globalList
                    .indexOfFirst { (iso, occIdx) -> iso == activeIso && occIdx == safeIdx }
                    .coerceAtLeast(0)
                _uiState.update {
                    it.copy(
                        activeOccurrenceIndex = safeIdx,
                        globalOccurrenceIndex = newGlobalIdx,
                    )
                }
            } else {
                flushImmediate()
                _uiState.update {
                    it.copy(
                        pendingArchiveNavigation = CrossJournalSearchTarget(
                            isoDate = targetIso,
                            query = state.highlightQuery,
                            occurrenceIndex = targetOccIdx,
                        ),
                    )
                }
            }
        } else {
            _uiState.update { current ->
                val total = current.matchRanges.size
                if (total <= 1) return@update current
                val nextIndex = (current.activeOccurrenceIndex + delta).mod(total)
                current.copy(activeOccurrenceIndex = nextIndex)
            }
        }
    }

    private fun clearHighlight() {
        globalSearchJob?.cancel()
        cachedGlobalOccurrences = emptyList()
        _uiState.update { state ->
            state.copy(
                highlightQuery = "",
                matchRanges = emptyList(),
                activeOccurrenceIndex = 0,
                globalOccurrenceIndex = 0,
                globalTotalOccurrences = 0,
                pendingArchiveNavigation = null,
            )
        }
    }

    companion object {
        const val AUTO_SAVE_DEBOUNCE_MS = 300L

        fun provideFactory(
            journalRepository: JournalRepository,
            userPreferencesRepository: UserPreferencesRepository,
            clock: Clock = Clock.systemDefaultZone(),
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditorViewModel(
                    journalRepository = journalRepository,
                    userPreferencesRepository = userPreferencesRepository,
                    clock = clock,
                ) as T
            }
        }
    }
}
