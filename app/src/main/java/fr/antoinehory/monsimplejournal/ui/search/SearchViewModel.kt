package fr.antoinehory.monsimplejournal.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.antoinehory.monsimplejournal.data.preferences.UserPreferencesRepository
import fr.antoinehory.monsimplejournal.data.repository.JournalRepository
import fr.antoinehory.monsimplejournal.domain.JournalDateFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Modèle UI immuable représentant une occurrence cliquable dans la liste des résultats de recherche.
 */
@Immutable
data class SearchOccurrenceUiModel(
    val id: String,
    val isoDate: String,
    val formattedDateTitle: String,
    val occurrenceIndex: Int,
    val totalOccurrencesInEntry: Int,
    val charOffset: Int,
    val snippetBefore: String,
    val matchText: String,
    val snippetAfter: String,
)

/**
 * État UI de l'écran de recherche (`SearchScreen`).
 */
data class SearchUiState(
    val query: String = "",
    val results: List<SearchOccurrenceUiModel> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isSearching: Boolean = false,
)

/**
 * ViewModel pilotant la recherche d'occurrences plein texte insensible aux accents et à la casse,
 * ainsi que l'historique des recherches récentes.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val journalRepository: JournalRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    private val rawOccurrencesFlow = queryFlow
        .debounce(150L)
        .distinctUntilChanged()
        .flatMapLatest { currentQuery ->
            val trimmed = currentQuery.trim()
            if (trimmed.isEmpty()) {
                flowOf(emptyList())
            } else {
                journalRepository.searchOccurrences(trimmed)
            }
        }

    val uiState: StateFlow<SearchUiState> = combine(
        queryFlow,
        rawOccurrencesFlow,
        userPreferencesRepository.userPreferencesFlow,
    ) { query, occurrences, prefs ->
        val uiModels = occurrences.map { occ ->
            SearchOccurrenceUiModel(
                id = "${occ.isoDate}_${occ.occurrenceIndex}",
                isoDate = occ.isoDate,
                formattedDateTitle = JournalDateFormatter.formatTitle(
                    date = occ.localDate,
                    style = prefs.dateFormatStyle,
                ),
                occurrenceIndex = occ.occurrenceIndex,
                totalOccurrencesInEntry = occ.totalOccurrencesInEntry,
                charOffset = occ.charOffset,
                snippetBefore = occ.snippetBefore,
                matchText = occ.matchText,
                snippetAfter = occ.snippetAfter,
            )
        }
        SearchUiState(
            query = query,
            results = uiModels,
            recentSearches = prefs.recentSearchQueries,
            isSearching = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SearchUiState(),
    )

    fun onQueryChanged(newQuery: String) {
        queryFlow.value = newQuery
    }

    fun onClearQuery() {
        queryFlow.value = ""
    }

    fun onCommitCurrentSearch() {
        val current = queryFlow.value.trim()
        if (current.isEmpty()) return
        viewModelScope.launch {
            userPreferencesRepository.addRecentSearchQuery(current)
        }
    }

    fun onRecentSearchClicked(recentQuery: String) {
        queryFlow.value = recentQuery
        viewModelScope.launch {
            userPreferencesRepository.addRecentSearchQuery(recentQuery)
        }
    }

    fun onRemoveRecentSearch(recentQuery: String) {
        viewModelScope.launch {
            userPreferencesRepository.removeRecentSearchQuery(recentQuery)
        }
    }

    fun onClearAllRecentSearches() {
        viewModelScope.launch {
            userPreferencesRepository.clearRecentSearchQueries()
        }
    }

    companion object {
        fun provideFactory(
            journalRepository: JournalRepository,
            userPreferencesRepository: UserPreferencesRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(
                    journalRepository = journalRepository,
                    userPreferencesRepository = userPreferencesRepository,
                ) as T
            }
        }
    }
}
