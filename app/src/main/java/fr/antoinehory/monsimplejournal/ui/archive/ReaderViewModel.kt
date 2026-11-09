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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

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
    val exportMessageResId: Int? = null,
) {
    /** Offset exact du caractère sur lequel scroller automatiquement lors d'un saut depuis la recherche. */
    val targetCharOffset: Int?
        get() = matchRanges.getOrNull(activeOccurrenceIndex)?.first

    /** Contenu Markdown complet formaté avec le titre en en-tête `# Titre`. */
    val markdownDocument: String
        get() = "# $formattedTitle\n\n$content\n"
}

/**
 * ViewModel dédié à la consultation **strictement en lecture seule** d'un journal passé (ADR-002).
 * Aucune méthode d'édition ou de modification n'est exposée.
 */
class ReaderViewModel(
    isoDate: String,
    initialHighlightQuery: String = "",
    initialOccurrenceIndex: Int = 0,
    private val journalRepository: JournalRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val entryDate: LocalDate = runCatching { LocalDate.parse(isoDate) }
        .getOrElse { LocalDate.now() }

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            isoDate = entryDate.toString(),
            formattedTitle = JournalDateFormatter.formatTitle(entryDate),
            highlightQuery = initialHighlightQuery.trim(),
            activeOccurrenceIndex = initialOccurrenceIndex.coerceAtLeast(0),
        ),
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        observeEntryAndPreferences()
    }

    private fun observeEntryAndPreferences() {
        viewModelScope.launch {
            combine(
                journalRepository.observeEntryByDate(entryDate),
                userPreferencesRepository.userPreferencesFlow,
            ) { entry, prefs ->
                entry to prefs
            }.collect { (entry, prefs) ->
                val text = entry?.content.orEmpty()
                _uiState.update { state ->
                    val ranges = if (state.highlightQuery.isNotEmpty()) {
                        DiacriticsMatcher.findMatchRanges(text, state.highlightQuery)
                    } else {
                        emptyList()
                    }
                    val safeIndex = if (ranges.isEmpty()) {
                        0
                    } else {
                        state.activeOccurrenceIndex.coerceIn(0, ranges.lastIndex)
                    }
                    state.copy(
                        formattedTitle = JournalDateFormatter.formatTitle(
                            date = entryDate,
                            style = prefs.dateFormatStyle,
                        ),
                        content = text,
                        isLoading = false,
                        matchRanges = ranges,
                        activeOccurrenceIndex = safeIndex,
                    )
                }
            }
        }
    }

    fun onNextOccurrence() {
        _uiState.update { state ->
            val total = state.matchRanges.size
            if (total <= 1) return@update state
            state.copy(activeOccurrenceIndex = (state.activeOccurrenceIndex + 1).mod(total))
        }
    }

    fun onPreviousOccurrence() {
        _uiState.update { state ->
            val total = state.matchRanges.size
            if (total <= 1) return@update state
            state.copy(activeOccurrenceIndex = (state.activeOccurrenceIndex - 1).mod(total))
        }
    }

    fun onClearHighlight() {
        _uiState.update { state ->
            state.copy(
                highlightQuery = "",
                matchRanges = emptyList(),
                activeOccurrenceIndex = 0,
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
            journalRepository: JournalRepository,
            userPreferencesRepository: UserPreferencesRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(
                    isoDate = isoDate,
                    initialHighlightQuery = initialHighlightQuery,
                    initialOccurrenceIndex = initialOccurrenceIndex,
                    journalRepository = journalRepository,
                    userPreferencesRepository = userPreferencesRepository,
                ) as T
            }
        }
    }
}
