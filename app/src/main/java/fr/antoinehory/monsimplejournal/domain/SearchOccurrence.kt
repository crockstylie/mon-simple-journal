package fr.antoinehory.monsimplejournal.domain

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/**
 * Représente une occurrence précise d'un mot recherché au sein d'un journal.
 *
 * @property isoDate Date du journal (`YYYY-MM-DD`).
 * @property epochDay Jour epoch pour le tri et le formatage.
 * @property occurrenceIndex Index (0-based) de cette occurrence au sein de ce journal.
 * @property totalOccurrencesInEntry Nombre total d'occurrences trouvées dans ce même journal.
 * @property charOffset Position exacte du premier caractère de l'occurrence dans le texte original.
 * @property matchLength Longueur de l'occurrence dans le texte original.
 * @property snippetBefore Extrait de texte précédant immédiatement le mot trouvé.
 * @property matchText Le mot tel qu'il apparaît dans le journal (avec sa casse et ses accents d'origine).
 * @property snippetAfter Extrait de texte suivant immédiatement le mot trouvé.
 */
@Immutable
data class SearchOccurrence(
    val isoDate: String,
    val epochDay: Long,
    val occurrenceIndex: Int,
    val totalOccurrencesInEntry: Int,
    val charOffset: Int,
    val matchLength: Int,
    val snippetBefore: String,
    val matchText: String,
    val snippetAfter: String,
) {
    val localDate: LocalDate
        get() = LocalDate.ofEpochDay(epochDay)
}
