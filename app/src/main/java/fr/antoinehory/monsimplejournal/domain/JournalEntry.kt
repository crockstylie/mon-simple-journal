package fr.antoinehory.monsimplejournal.domain

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/**
 * Modèle métier immuable représentant le journal d'une journée.
 *
 * @property isoDate Date au format ISO-8601 (`YYYY-MM-DD`), clé unique de la journée.
 * @property epochDay Nombre de jours depuis l'époque Unix (`LocalDate.toEpochDay()`), utilisé pour le tri chronologique.
 * @property content Texte brut / Markdown écrit par l'utilisateur.
 * @property createdAt Timestamp (ms) de création initiale.
 * @property updatedAt Timestamp (ms) de dernière sauvegarde.
 */
@Immutable
data class JournalEntry(
    val isoDate: String,
    val epochDay: Long,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Conversion directe vers [LocalDate]. */
    val localDate: LocalDate
        get() = LocalDate.ofEpochDay(epochDay)

    /** Aperçu de la première ligne non vide pour la liste des archives. */
    val firstLinePreview: String
        get() = content
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: ""

    /** Nombre approximatif de mots dans l'entrée. */
    val wordCount: Int
        get() {
            val trimmed = content.trim()
            if (trimmed.isEmpty()) return 0
            return trimmed.split(WHITESPACE_REGEX).size
        }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
