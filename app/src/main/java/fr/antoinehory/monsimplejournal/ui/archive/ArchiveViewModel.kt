package fr.antoinehory.monsimplejournal.ui.archive

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.antoinehory.monsimplejournal.data.preferences.UserPreferencesRepository
import fr.antoinehory.monsimplejournal.data.repository.JournalRepository
import fr.antoinehory.monsimplejournal.domain.JournalDateFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

/**
 * Élément d'affichage immuable pour un journal passé dans la liste des archives.
 */
@Immutable
data class ArchiveItemUiModel(
    val isoDate: String,
    val formattedDateTitle: String,
    val firstLinePreview: String,
    val wordCount: Int,
)

/**
 * État UI de l'écran des journaux précédents (`ArchiveScreen`).
 */
data class ArchiveUiState(
    val items: List<ArchiveItemUiModel> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * ViewModel observant les journaux des jours précédents (`isoDate < activeSessionDate`)
 * par ordre antéchronologique et les formatant selon le style de date choisi par l'utilisateur.
 */
class ArchiveViewModel(
    journalRepository: JournalRepository,
    userPreferencesRepository: UserPreferencesRepository,
    clock: Clock = Clock.systemDefaultZone(),
    activeSessionDate: LocalDate = LocalDate.now(clock),
) : ViewModel() {

    val uiState: StateFlow<ArchiveUiState> = combine(
        journalRepository.observePastEntries(activeSessionDate),
        userPreferencesRepository.userPreferencesFlow,
    ) { entries, prefs ->
        val models = entries.map { entry ->
            ArchiveItemUiModel(
                isoDate = entry.isoDate,
                formattedDateTitle = JournalDateFormatter.formatTitle(
                    date = entry.localDate,
                    style = prefs.dateFormatStyle,
                ),
                firstLinePreview = entry.firstLinePreview,
                wordCount = entry.wordCount,
            )
        }
        ArchiveUiState(
            items = models,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ArchiveUiState(isLoading = true),
    )

    companion object {
        fun provideFactory(
            journalRepository: JournalRepository,
            userPreferencesRepository: UserPreferencesRepository,
            clock: Clock = Clock.systemDefaultZone(),
            activeSessionDate: LocalDate = LocalDate.now(clock),
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ArchiveViewModel(
                    journalRepository = journalRepository,
                    userPreferencesRepository = userPreferencesRepository,
                    clock = clock,
                    activeSessionDate = activeSessionDate,
                ) as T
            }
        }
    }
}
