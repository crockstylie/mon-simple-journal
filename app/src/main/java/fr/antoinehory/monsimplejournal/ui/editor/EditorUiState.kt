package fr.antoinehory.monsimplejournal.ui.editor

import androidx.compose.ui.text.input.TextFieldValue
import fr.antoinehory.monsimplejournal.domain.DateFormatStyle
import fr.antoinehory.monsimplejournal.domain.MarkdownFormatAction
import fr.antoinehory.monsimplejournal.ui.archive.CrossJournalSearchTarget
import java.time.LocalDate

/**
 * État UI de l'écran principal d'écriture du jour (`EditorScreen`).
 *
 * @property sessionDate Date d'ancrage de la session en cours (verrouillée au passage de minuit `23h55` ➔ `00h00`).
 * @property formattedTitle Titre généré automatiquement avec la date en toutes lettres et le nom du jour.
 * @property textFieldValue Texte en cours d'édition avec la position du curseur et de la sélection.
 * @property dateFormatStyle Format de date actif choisi dans les paramètres.
 * @property fontScale Facteur d'échelle de la police pour le rendu proportionnel des titres H1/H2/H3.
 * @property keepScreenOn Indique si l'écran doit rester allumé pendant la session d'écriture.
 * @property isLoading Indique si le chargement initial du journal du jour est en cours.
 * @property highlightQuery Mot-clé éventuellement recherché à surligner dans le journal du jour.
 * @property matchRanges Plages de caractères correspondant à [highlightQuery] dans le texte.
 * @property activeOccurrenceIndex Index (0-based) de l'occurrence actuellement ciblée par le défilement.
 * @property globalOccurrenceIndex Index (0-based) de l'occurrence active à l'échelle de tous les journaux.
 * @property globalTotalOccurrences Nombre total d'occurrences de [highlightQuery] à travers tous les journaux.
 * @property pendingArchiveNavigation Cible de navigation lorsqu'un saut `▲` / `▼` bascule vers une archive passée.
 */
data class EditorUiState(
    val sessionDate: LocalDate = LocalDate.now(),
    val formattedTitle: String = "",
    val textFieldValue: TextFieldValue = TextFieldValue(""),
    val dateFormatStyle: DateFormatStyle = DateFormatStyle.FULL_DAY_FIRST,
    val fontScale: Float = 1.0f,
    val keepScreenOn: Boolean = true,
    val isLoading: Boolean = true,
    val highlightQuery: String = "",
    val matchRanges: List<IntRange> = emptyList(),
    val activeOccurrenceIndex: Int = 0,
    val globalOccurrenceIndex: Int = 0,
    val globalTotalOccurrences: Int = 0,
    val pendingArchiveNavigation: CrossJournalSearchTarget? = null,
) {
    /** Offset exact du caractère sur lequel scroller lorsqu'une recherche cible le journal du jour. */
    val targetCharOffset: Int?
        get() = matchRanges.getOrNull(activeOccurrenceIndex)?.first

    /** Ensemble des styles Markdown actifs à la position actuelle du curseur ou de la sélection. */
    val activeFormats: Set<MarkdownFormatAction>
        get() = fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.detectActiveFormats(textFieldValue)
}

/**
 * Événements utilisateur émis depuis `EditorScreen` vers `EditorViewModel`.
 */
sealed interface EditorUiEvent {
    data class OnTextChanged(
        val newValue: TextFieldValue,
        val sessionDate: LocalDate? = null,
    ) : EditorUiEvent
    data class OnFormatActionClicked(val action: MarkdownFormatAction) : EditorUiEvent
    data object OnInsertTimestampClicked : EditorUiEvent
    data object OnLifecycleStopped : EditorUiEvent
    data object OnLifecycleResumed : EditorUiEvent
    data class OnHighlightOccurrenceRequested(val query: String, val occurrenceIndex: Int) : EditorUiEvent
    data object OnNextOccurrenceClicked : EditorUiEvent
    data object OnPreviousOccurrenceClicked : EditorUiEvent
    data object OnClearHighlightClicked : EditorUiEvent
    data object OnArchiveNavigationConsumed : EditorUiEvent
}
