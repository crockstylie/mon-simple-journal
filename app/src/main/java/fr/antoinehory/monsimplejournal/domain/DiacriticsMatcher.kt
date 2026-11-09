package fr.antoinehory.monsimplejournal.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Moteur de recherche d'occurrences insensible à la casse et aux accents (diacritiques),
 * tout en garantissant des positions (`charOffset` et `matchLength`) 100 % exactes dans le texte original.
 */
object DiacriticsMatcher {

    /**
     * Trouve toutes les plages de caractères ([IntRange] inclusives) correspondant à [query]
     * dans [content], sans tenir compte de la casse ni des accents.
     */
    fun findMatchRanges(content: String, query: String): List<IntRange> {
        val normalizedQuery = normalizeSimple(query.trim())
        if (normalizedQuery.isEmpty() || content.isEmpty()) return emptyList()

        // Construction de la chaîne normalisée du contenu + table de correspondance vers les index d'origine
        val normalizedBuilder = StringBuilder(content.length)
        val normToOrigStart = IntArray(content.length * 2)
        val normToOrigEndExclusive = IntArray(content.length * 2)
        var normLen = 0

        for (origIndex in content.indices) {
            val origCharStr = content.substring(origIndex, origIndex + 1)
            val stripped = stripDiacriticsAndLower(origCharStr)
            for (i in stripped.indices) {
                if (normLen >= normToOrigStart.size) break
                normalizedBuilder.append(stripped[i])
                normToOrigStart[normLen] = origIndex
                normToOrigEndExclusive[normLen] = origIndex + 1
                normLen++
            }
        }

        if (normLen < normalizedQuery.length) return emptyList()
        val normalizedContent = normalizedBuilder.toString()

        val ranges = mutableListOf<IntRange>()
        var searchIndex = 0
        while (searchIndex <= normalizedContent.length - normalizedQuery.length) {
            val foundNormIndex = normalizedContent.indexOf(normalizedQuery, startIndex = searchIndex)
            if (foundNormIndex < 0) break

            val origStart = normToOrigStart[foundNormIndex]
            val origEndExclusive = normToOrigEndExclusive[foundNormIndex + normalizedQuery.length - 1]
            ranges.add(origStart until origEndExclusive)

            searchIndex = foundNormIndex + normalizedQuery.length
        }

        return ranges
    }

    /**
     * Extrait la liste complète des [SearchOccurrence] avec leurs extraits contextuels pour une entrée donnée.
     */
    fun findOccurrencesInEntry(
        isoDate: String,
        epochDay: Long,
        content: String,
        query: String,
        snippetRadius: Int = 42,
    ): List<SearchOccurrence> {
        val ranges = findMatchRanges(content, query)
        if (ranges.isEmpty()) return emptyList()

        val total = ranges.size
        return ranges.mapIndexed { index, range ->
            val start = range.first
            val endExclusive = range.last + 1
            val matchLength = endExclusive - start

            val rawBeforeStart = (start - snippetRadius).coerceAtLeast(0)
            val rawAfterEnd = (endExclusive + snippetRadius).coerceAtMost(content.length)

            val prefixEllipsis = if (rawBeforeStart > 0) "…" else ""
            val suffixEllipsis = if (rawAfterEnd < content.length) "…" else ""

            val beforeText = content.substring(rawBeforeStart, start)
                .replace('\n', ' ')
                .replace('\r', ' ')
            val matchText = content.substring(start, endExclusive)
                .replace('\n', ' ')
            val afterText = content.substring(endExclusive, rawAfterEnd)
                .replace('\n', ' ')
                .replace('\r', ' ')

            SearchOccurrence(
                isoDate = isoDate,
                epochDay = epochDay,
                occurrenceIndex = index,
                totalOccurrencesInEntry = total,
                charOffset = start,
                matchLength = matchLength,
                snippetBefore = prefixEllipsis + beforeText,
                matchText = matchText,
                snippetAfter = afterText + suffixEllipsis,
            )
        }
    }

    /**
     * Normalise une chaîne pour une comparaison insensible aux accents et à la casse.
     */
    fun normalizeSimple(input: String): String = stripDiacriticsAndLower(input)

    private fun stripDiacriticsAndLower(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        val withoutMarks = buildString(decomposed.length) {
            for (ch in decomposed) {
                if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) {
                    append(ch)
                }
            }
        }
        return withoutMarks.lowercase(Locale.ROOT)
    }
}
