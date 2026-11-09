package fr.antoinehory.monsimplejournal.domain

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Actions de mise en forme disponibles dans la barre d'outils de l'éditeur.
 */
enum class MarkdownFormatAction {
    HEADING_1,
    HEADING_2,
    HEADING_3,
    BOLD,
    ITALIC,
    UNDERLINE,
    UNORDERED_LIST,
    ORDERED_LIST,
}

/**
 * Moteur métier pur de mise en forme Markdown légère (Titres H1/H2/H3, Gras, Italique, Souligné,
 * Listes à puces `UL` et Listes numérotées `OL`) et de construction d'[AnnotatedString]
 * préservant strictement les index de caractères (`OffsetMapping.Identity`).
 */
object MarkdownTextFormatter {

    private val UNORDERED_LIST_REGEX = Regex("""^-\s+""")
    private val ORDERED_LIST_REGEX = Regex("""^(\d+)\.\s+""")
    private val ANY_LINE_PREFIX_REGEX = Regex("""^(#{1,3}\s+|-\s+|\d+\.\s+)""")

    private val BOLD_REGEX = Regex("""\*\*(.+?)\*\*""")
    private val UNDERLINE_REGEX = Regex("""__(.+?)__""")
    private val ITALIC_REGEX = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""")

    /**
     * Applique une [action] de mise en forme sur la sélection ou la ligne courante d'un [TextFieldValue].
     */
    fun applyFormat(value: TextFieldValue, action: MarkdownFormatAction): TextFieldValue {
        return when (action) {
            MarkdownFormatAction.HEADING_1 -> toggleLineHeading(value, "# ")
            MarkdownFormatAction.HEADING_2 -> toggleLineHeading(value, "## ")
            MarkdownFormatAction.HEADING_3 -> toggleLineHeading(value, "### ")
            MarkdownFormatAction.BOLD -> toggleInlineWrap(value, "**")
            MarkdownFormatAction.ITALIC -> toggleInlineWrap(value, "*")
            MarkdownFormatAction.UNDERLINE -> toggleInlineWrap(value, "__")
            MarkdownFormatAction.UNORDERED_LIST -> toggleUnorderedList(value)
            MarkdownFormatAction.ORDERED_LIST -> toggleOrderedList(value)
        }
    }

    /**
     * Gère la continuation automatique des listes à puces (`- `) et des listes numérotées (`1. `, `2. `)
     * lorsque l'utilisateur appuie sur la touche `Entrée` (`\n`).
     * Si l'utilisateur appuie sur `Entrée` sur une puce vide, la puce est supprimée pour sortir de la liste.
     */
    fun handleTextChangeWithListContinuation(
        previous: TextFieldValue,
        current: TextFieldValue,
    ): TextFieldValue {
        val oldText = previous.text
        val newText = current.text
        val cursor = current.selection.min

        // Vérifie si l'utilisateur vient d'insérer exactement un saut de ligne au curseur
        if (!current.selection.collapsed || newText.length != oldText.length + 1 || cursor <= 0) {
            return current
        }
        if (newText[cursor - 1] != '\n') {
            return current
        }

        val newlineIndex = cursor - 1
        val prevLineStart = newText.lastIndexOf('\n', startIndex = (newlineIndex - 1).coerceAtLeast(0))
            .let { if (it < 0 || newlineIndex == 0) 0 else it + 1 }
        val prevLine = newText.substring(prevLineStart, newlineIndex)

        // Cas 1 : Liste à puces "- "
        val ulMatch = UNORDERED_LIST_REGEX.find(prevLine)
        if (ulMatch != null) {
            val itemContent = prevLine.substring(ulMatch.range.last + 1)
            return if (itemContent.isBlank()) {
                // Ligne de liste vide : on retire le "- " pour quitter la liste proprement
                val cleaned = newText.removeRange(prevLineStart, newlineIndex)
                val newCursor = (cursor - prevLine.length).coerceAtLeast(0)
                TextFieldValue(text = cleaned, selection = TextRange(newCursor))
            } else {
                // On continue la liste à puces sur la nouvelle ligne
                val insertion = "- "
                val updated = StringBuilder(newText).insert(cursor, insertion).toString()
                TextFieldValue(text = updated, selection = TextRange(cursor + insertion.length))
            }
        }

        // Cas 2 : Liste numérotée "1. "
        val olMatch = ORDERED_LIST_REGEX.find(prevLine)
        if (olMatch != null) {
            val number = olMatch.groupValues[1].toIntOrNull() ?: 1
            val itemContent = prevLine.substring(olMatch.range.last + 1)
            return if (itemContent.isBlank()) {
                // Ligne numérotée vide : on retire "N. " pour quitter la liste proprement
                val cleaned = newText.removeRange(prevLineStart, newlineIndex)
                val newCursor = (cursor - prevLine.length).coerceAtLeast(0)
                TextFieldValue(text = cleaned, selection = TextRange(newCursor))
            } else {
                val insertion = "${number + 1}. "
                val updated = StringBuilder(newText).insert(cursor, insertion).toString()
                TextFieldValue(text = updated, selection = TextRange(cursor + insertion.length))
            }
        }

        return current
    }

    private fun toggleLineHeading(value: TextFieldValue, targetPrefix: String): TextFieldValue {
        val text = value.text
        val selMin = value.selection.min.coerceIn(0, text.length)
        val selMax = value.selection.max.coerceIn(0, text.length)

        val lineStart = if (selMin == 0) 0 else text.lastIndexOf('\n', selMin - 1) + 1
        val lineEnd = text.indexOf('\n', selMax).let { if (it < 0) text.length else it }
        val lineText = text.substring(lineStart, lineEnd)

        val newLineText: String
        val delta: Int
        if (lineText.startsWith(targetPrefix)) {
            newLineText = lineText.removePrefix(targetPrefix)
            delta = -targetPrefix.length
        } else {
            val existingPrefix = ANY_LINE_PREFIX_REGEX.find(lineText)?.value.orEmpty()
            newLineText = targetPrefix + lineText.removePrefix(existingPrefix)
            delta = targetPrefix.length - existingPrefix.length
        }

        val updatedText = text.replaceRange(lineStart, lineEnd, newLineText)
        val newStart = (selMin + delta).coerceIn(lineStart, lineStart + newLineText.length)
        val newEnd = (selMax + delta).coerceIn(newStart, lineStart + newLineText.length)
        return TextFieldValue(
            text = updatedText,
            selection = TextRange(newStart, newEnd),
        )
    }

    private fun toggleInlineWrap(value: TextFieldValue, marker: String): TextFieldValue {
        val text = value.text
        val min = value.selection.min.coerceIn(0, text.length)
        val max = value.selection.max.coerceIn(0, text.length)
        val markerLen = marker.length

        // Si aucune sélection : insère les balises et place le curseur au milieu
        if (min == max) {
            val insertion = marker + marker
            val updated = StringBuilder(text).insert(min, insertion).toString()
            return TextFieldValue(
                text = updated,
                selection = TextRange(min + markerLen),
            )
        }

        val selected = text.substring(min, max)

        // Si la sélection contient déjà les balises aux extrémités : on les retire
        if (selected.length >= markerLen * 2 && selected.startsWith(marker) && selected.endsWith(marker)) {
            val unwrapped = selected.substring(markerLen, selected.length - markerLen)
            val updated = text.replaceRange(min, max, unwrapped)
            return TextFieldValue(
                text = updated,
                selection = TextRange(min, min + unwrapped.length),
            )
        }

        // Si les balises sont juste autour de la sélection : on les retire
        if (min >= markerLen &&
            max + markerLen <= text.length &&
            text.substring(min - markerLen, min) == marker &&
            text.substring(max, max + markerLen) == marker
        ) {
            val updated = text.replaceRange(min - markerLen, max + markerLen, selected)
            return TextFieldValue(
                text = updated,
                selection = TextRange(min - markerLen, max - markerLen),
            )
        }

        // Sinon, on entoure la sélection avec le marqueur
        val wrapped = marker + selected + marker
        val updated = text.replaceRange(min, max, wrapped)
        return TextFieldValue(
            text = updated,
            selection = TextRange(min + markerLen, max + markerLen),
        )
    }

    private fun toggleUnorderedList(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val selMin = value.selection.min.coerceIn(0, text.length)
        val selMax = value.selection.max.coerceIn(0, text.length)

        val blockStart = if (selMin == 0) 0 else text.lastIndexOf('\n', selMin - 1) + 1
        val blockEnd = text.indexOf('\n', selMax).let { if (it < 0) text.length else it }
        val lines = text.substring(blockStart, blockEnd).split('\n')

        val allAlreadyUnordered = lines.all { it.startsWith("- ") }
        val transformedLines = lines.map { line ->
            if (allAlreadyUnordered) {
                line.removePrefix("- ")
            } else {
                val stripped = line.replaceFirst(ANY_LINE_PREFIX_REGEX, "")
                "- $stripped"
            }
        }

        val replacement = transformedLines.joinToString("\n")
        val updatedText = text.replaceRange(blockStart, blockEnd, replacement)
        val cursorEnd = blockStart + replacement.length
        return TextFieldValue(
            text = updatedText,
            selection = if (selMin == selMax) {
                TextRange(cursorEnd)
            } else {
                TextRange(blockStart, cursorEnd)
            },
        )
    }

    private fun toggleOrderedList(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val selMin = value.selection.min.coerceIn(0, text.length)
        val selMax = value.selection.max.coerceIn(0, text.length)

        val blockStart = if (selMin == 0) 0 else text.lastIndexOf('\n', selMin - 1) + 1
        val blockEnd = text.indexOf('\n', selMax).let { if (it < 0) text.length else it }
        val lines = text.substring(blockStart, blockEnd).split('\n')

        // Détermine le numéro de départ en regardant si la ligne précédente est déjà numérotée
        var startNumber = 1
        if (blockStart > 0) {
            val prevLineEnd = blockStart - 1
            val prevLineStart = if (prevLineEnd == 0) 0 else text.lastIndexOf('\n', prevLineEnd - 1) + 1
            val prevLine = text.substring(prevLineStart, prevLineEnd)
            val prevMatch = ORDERED_LIST_REGEX.find(prevLine)
            if (prevMatch != null) {
                startNumber = (prevMatch.groupValues[1].toIntOrNull() ?: 0) + 1
            }
        }

        val allAlreadyOrdered = lines.all { ORDERED_LIST_REGEX.containsMatchIn(it) }
        val transformedLines = lines.mapIndexed { index, line ->
            if (allAlreadyOrdered) {
                line.replaceFirst(ORDERED_LIST_REGEX, "")
            } else {
                val stripped = line.replaceFirst(ANY_LINE_PREFIX_REGEX, "")
                "${startNumber + index}. $stripped"
            }
        }

        val replacement = transformedLines.joinToString("\n")
        val updatedText = text.replaceRange(blockStart, blockEnd, replacement)
        val cursorEnd = blockStart + replacement.length
        return TextFieldValue(
            text = updatedText,
            selection = if (selMin == selMax) {
                TextRange(cursorEnd)
            } else {
                TextRange(blockStart, cursorEnd)
            },
        )
    }

    /**
     * Construit un [AnnotatedString] appliquant à la fois le style visuel Markdown
     * (Titres H1/H2/H3, Gras, Italique, Souligné, Puces UL/OL) et le surlignage des occurrences
     * de recherche, sans modifier la longueur du texte (`OffsetMapping.Identity`).
     */
    fun buildMarkdownAnnotatedString(
        content: String,
        textColor: Color,
        accentColor: Color,
        fontScale: Float = 1.0f,
        matchRanges: List<IntRange> = emptyList(),
        activeOccurrenceIndex: Int = 0,
        activeHighlightColor: Color = accentColor.copy(alpha = 0.42f),
        passiveHighlightColor: Color = accentColor.copy(alpha = 0.20f),
    ): AnnotatedString {
        if (content.isEmpty()) return AnnotatedString("")

        val syntaxMarkerStyle = SpanStyle(color = textColor.copy(alpha = 0.38f))

        return buildAnnotatedString {
            append(content)

            // 1. Styles par ligne : Titres (#, ##, ###) et marqueurs de listes (- , 1. )
            var lineStart = 0
            while (lineStart <= content.length) {
                val lineEnd = content.indexOf('\n', lineStart).let { if (it < 0) content.length else it }
                val line = content.substring(lineStart, lineEnd)

                when {
                    line.startsWith("### ") -> {
                        addStyle(
                            style = SpanStyle(
                                fontSize = (18.5f * fontScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                            ),
                            start = lineStart,
                            end = lineEnd,
                        )
                        addStyle(syntaxMarkerStyle, lineStart, (lineStart + 4).coerceAtMost(lineEnd))
                    }

                    line.startsWith("## ") -> {
                        addStyle(
                            style = SpanStyle(
                                fontSize = (20.5f * fontScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                            ),
                            start = lineStart,
                            end = lineEnd,
                        )
                        addStyle(syntaxMarkerStyle, lineStart, (lineStart + 3).coerceAtMost(lineEnd))
                    }

                    line.startsWith("# ") -> {
                        addStyle(
                            style = SpanStyle(
                                fontSize = (23f * fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                            ),
                            start = lineStart,
                            end = lineEnd,
                        )
                        addStyle(syntaxMarkerStyle, lineStart, (lineStart + 2).coerceAtMost(lineEnd))
                    }

                    else -> {
                        val ulMatch = UNORDERED_LIST_REGEX.find(line)
                        if (ulMatch != null) {
                            addStyle(
                                style = SpanStyle(
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold,
                                ),
                                start = lineStart,
                                end = lineStart + ulMatch.range.last + 1,
                            )
                        } else {
                            val olMatch = ORDERED_LIST_REGEX.find(line)
                            if (olMatch != null) {
                                addStyle(
                                    style = SpanStyle(
                                        color = accentColor,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    start = lineStart,
                                    end = lineStart + olMatch.range.last + 1,
                                )
                            }
                        }
                    }
                }

                if (lineEnd == content.length) break
                lineStart = lineEnd + 1
            }

            // 2. Styles inline : Gras (**...**), Souligné (__...__), Italique (*...*)
            BOLD_REGEX.findAll(content).forEach { match ->
                val start = match.range.first
                val endExclusive = match.range.last + 1
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, endExclusive)
                addStyle(syntaxMarkerStyle, start, start + 2)
                addStyle(syntaxMarkerStyle, endExclusive - 2, endExclusive)
            }

            UNDERLINE_REGEX.findAll(content).forEach { match ->
                val start = match.range.first
                val endExclusive = match.range.last + 1
                addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, endExclusive)
                addStyle(syntaxMarkerStyle, start, start + 2)
                addStyle(syntaxMarkerStyle, endExclusive - 2, endExclusive)
            }

            ITALIC_REGEX.findAll(content).forEach { match ->
                val start = match.range.first
                val endExclusive = match.range.last + 1
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, endExclusive)
                addStyle(syntaxMarkerStyle, start, start + 1)
                addStyle(syntaxMarkerStyle, endExclusive - 1, endExclusive)
            }

            // 3. Surlignage des occurrences de recherche par-dessus
            matchRanges.forEachIndexed { index, range ->
                val start = range.first.coerceIn(0, content.length)
                val endExclusive = (range.last + 1).coerceIn(start, content.length)
                if (start < endExclusive) {
                    val isActive = index == activeOccurrenceIndex
                    addStyle(
                        style = SpanStyle(
                            background = if (isActive) activeHighlightColor else passiveHighlightColor,
                            fontWeight = if (isActive) FontWeight.Bold else null,
                        ),
                        start = start,
                        end = endExclusive,
                    )
                }
            }
        }
    }
}
