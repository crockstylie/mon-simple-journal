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
 * 2. **Continuité à minuit (`23h55` ➔ `00h00`)** : la `sessionDate` est verrouillée tant que l'utilisateur
 *    écrit ou revient rapidement dans l'application. Elle ne bascule sur le nouveau jour que si l'application
 *    est restée en arrière-plan plus de [BACKGROUND_SESSION_EXPIRY_MS] (30 minutes) et que la date calendaire a changé.
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
    private var lastStoppedAtMillis: Long? = null
    private var lastSavedContent: String = ""

    private val _uiState = MutableStateFlow(
        EditorUiState(
            sessionDate = activeSessionDate,
            formattedTitle = JournalDateFormatter.formatTitle(activeSessionDate),
        ),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val pendingSaveFlow = MutableSharedFlow<Pair<LocalDate, String>>(extraBufferCapacity = 64)
    private var loadSessionJob: Job? = null

    init {
        observePreferences()
        observeDebouncedSaves()
        loadSessionEntry(activeSessionDate)
    }

    fun onEvent(event: EditorUiEvent) {
        when (event) {
            is EditorUiEvent.OnTextChanged -> handleTextChanged(
                newValue = event.newValue,
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

    private fun loadSessionEntry(date: LocalDate) {
        loadSessionJob?.cancel()
        loadSessionJob = viewModelScope.launch {
            val existingEntry = journalRepository.getEntryByDate(date)
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
        allowListContinuation: Boolean = false,
    ) {
        val currentState = _uiState.value
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
        handleTextChanged(newValue = formatted, allowListContinuation = false)
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
            TextFieldValue(
                text = newText,
                selection = TextRange(newCursorPos),
            ),
        )
    }

    private fun handleLifecycleStopped() {
        lastStoppedAtMillis = clock.millis()
        flushImmediate()
    }

    private fun handleLifecycleResumed() {
        val stoppedAt = lastStoppedAtMillis ?: return
        lastStoppedAtMillis = null

        val elapsedInBackground = clock.millis() - stoppedAt
        val today = LocalDate.now(clock)

        // Si l'application est restée fermée/en arrière-plan plus de 30 minutes et qu'on a changé de jour,
        // on bascule proprement sur la nouvelle journée. Sinon (ex: écriture à 23h55 -> 00h00), on conserve la session.
        if (today != activeSessionDate && elapsedInBackground >= BACKGROUND_SESSION_EXPIRY_MS) {
            flushImmediate()
            activeSessionDate = today
            loadSessionEntry(today)
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
        val dateToSave = activeSessionDate
        val contentToSave = _uiState.value.textFieldValue.text
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
    }

    private fun stepOccurrence(delta: Int) {
        _uiState.update { state ->
            val total = state.matchRanges.size
            if (total <= 1) return@update state
            val nextIndex = (state.activeOccurrenceIndex + delta).mod(total)
            state.copy(activeOccurrenceIndex = nextIndex)
        }
    }

    private fun clearHighlight() {
        _uiState.update { state ->
            state.copy(
                highlightQuery = "",
                matchRanges = emptyList(),
                activeOccurrenceIndex = 0,
            )
        }
    }

    companion object {
        const val AUTO_SAVE_DEBOUNCE_MS = 300L
        const val BACKGROUND_SESSION_EXPIRY_MS = 30 * 60 * 1000L // 30 minutes

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
