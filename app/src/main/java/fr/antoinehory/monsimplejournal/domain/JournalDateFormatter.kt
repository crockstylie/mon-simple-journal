package fr.antoinehory.monsimplejournal.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats d'affichage disponibles pour le titre automatique du journal (date en toutes lettres + nom du jour).
 */
enum class DateFormatStyle {
    /** Exemple : "Lundi 9 novembre 2026" (Défaut) */
    FULL_DAY_FIRST,

    /** Exemple : "9 novembre 2026 — Lundi" */
    DATE_FIRST_DASH_DAY,

    /** Exemple : "Lundi 9 nov. 2026" */
    SHORT_DAY_MONTH,

    /** Exemple : "2026-11-09 · Lundi" */
    ISO_WITH_DAY,
}

/**
 * Utilitaire pur de formatage de la date du journal et des marqueurs d'horodatage intra-journée.
 */
object JournalDateFormatter {

    /**
     * Formate une [LocalDate] selon le [style] choisi par l'utilisateur et la [locale] courante,
     * en mettant une majuscule élégante au début du titre et au nom du jour.
     */
    fun formatTitle(
        date: LocalDate,
        style: DateFormatStyle = DateFormatStyle.FULL_DAY_FIRST,
        locale: Locale = Locale.getDefault(),
    ): String {
        return when (style) {
            DateFormatStyle.FULL_DAY_FIRST -> {
                val pattern = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale)
                date.format(pattern).capitalizeFirst(locale)
            }

            DateFormatStyle.DATE_FIRST_DASH_DAY -> {
                val datePart = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
                val dayPart = date.format(DateTimeFormatter.ofPattern("EEEE", locale)).capitalizeFirst(locale)
                "$datePart — $dayPart"
            }

            DateFormatStyle.SHORT_DAY_MONTH -> {
                val pattern = DateTimeFormatter.ofPattern("EEEE d MMM yyyy", locale)
                date.format(pattern).capitalizeFirst(locale)
            }

            DateFormatStyle.ISO_WITH_DAY -> {
                val dayPart = date.format(DateTimeFormatter.ofPattern("EEEE", locale)).capitalizeFirst(locale)
                "${date} · $dayPart"
            }
        }
    }

    /**
     * Génère le marqueur d'horodatage discret à insérer dans le texte (ex. "— 22:15 — ").
     */
    fun formatTimestampMarker(time: LocalTime): String {
        return String.format(Locale.ROOT, "— %02d:%02d — ", time.hour, time.minute)
    }

    private fun String.capitalizeFirst(locale: Locale): String {
        if (isEmpty()) return this
        return replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(locale) else char.toString()
        }
    }
}
