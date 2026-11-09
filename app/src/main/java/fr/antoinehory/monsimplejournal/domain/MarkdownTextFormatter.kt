package fr.antoinehory.monsimplejournal.domain

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
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
 * Résultat du formatage visuel WYSIWYG d'un texte Markdown :
 * - [annotatedString] : le texte affiché à l'écran **sans aucun symbole Markdown visible** (`#`, `**`, `*`, `__`).
 * - [offsetMapping] : la table de correspondance bidirectionnelle exacte entre le texte brut Markdown
 *   et le texte affiché à l'écran.
 */
data class FormattedMarkdownResult(
    val annotatedString: AnnotatedString,
    val offsetMapping: OffsetMapping,
)

/**
 * Moteur métier pur de mise en forme WYSIWYG Markdown légère (Titres H1/H2/H3, Gras, Italique, Souligné,
 * Listes à puces `UL` et Listes numérotées `OL`), de détection des styles actifs au curseur,
 * et de construction d'[AnnotatedString] masquant intégralement la syntaxe Markdown.
 */
object MarkdownTextFormatter {

    /** Espace de largeur nulle utilisé comme point d'ancrage invisible lorsqu'un style inline est activé à vide. */
    const val ZERO_WIDTH_SPACE = '\u200B'

    private val UNORDERED_LIST_REGEX = Regex("""^-\s""")
    private val ORDERED_LIST_REGEX = Regex("""^(\d+)\.\s""")
    private val ANY_LINE_PREFIX_REGEX = Regex("""^(#{1,3}\s|-\s|\d+\.\s)""")

    // Expressions régulières pour les styles inline (du plus spécifique au plus général)
    private val BOLD_ITALIC_REGEX = Regex("""\*\*\*(.+?)\*\*\*""")
    private val BOLD_REGEX = Regex("""(?<!\*)\*\*(?!\*)(.+?)(?<!\*)\*\*(?!\*)""")
    private val ITALIC_REGEX = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""")
    private val UNDERLINE_REGEX = Regex("""__(.+?)__""")

    private data class InlineSpan(
        val action: MarkdownFormatAction,
        val fullStart: Int,
        val fullEndExclusive: Int,
        val markerLength: Int,
    ) {
        val contentStart: Int get() = fullStart + markerLength
        val contentEndExclusive: Int get() = fullEndExclusive - markerLength
    }

    /**
     * Détecte l'ensemble des actions de mise en forme ([MarkdownFormatAction]) actives
     * à la position actuelle du curseur ou de la sélection dans [value].
     */
    fun detectActiveFormats(value: TextFieldValue): Set<MarkdownFormatAction> {
        val text = value.text
        if (text.isEmpty()) return emptySet()

        val active = mutableSetOf<MarkdownFormatAction>()
        val selMin = value.selection.min.coerceIn(0, text.length)
        val selMax = value.selection.max.coerceIn(0, text.length)

        // 1. Style de ligne (H1, H2, H3, UL, OL) sur la ligne du curseur
        val lineStart = if (selMin == 0) 0 else text.lastIndexOf('\n', selMin - 1) + 1
        val lineEnd = text.indexOf('\n', selMin).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)

        when {
            line.startsWith("### ") -> active.add(MarkdownFormatAction.HEADING_3)
            line.startsWith("## ") -> active.add(MarkdownFormatAction.HEADING_2)
            line.startsWith("# ") -> active.add(MarkdownFormatAction.HEADING_1)
            UNORDERED_LIST_REGEX.containsMatchIn(line) -> active.add(MarkdownFormatAction.UNORDERED_LIST)
            ORDERED_LIST_REGEX.containsMatchIn(line) -> active.add(MarkdownFormatAction.ORDERED_LIST)
        }

        // 2. Styles inline (Gras, Italique, Souligné) englobant le curseur ou la sélection
        val inlineSpans = parseInlineSpans(text)
        for (span in inlineSpans) {
            val isMatch = if (selMin == selMax) {
                selMin in span.contentStart..span.contentEndExclusive
            } else {
                selMin >= span.fullStart && selMax <= span.fullEndExclusive
            }
            if (isMatch) {
                active.add(span.action)
            }
        }

        return active
    }

    /**
     * Applique ou bascule une [action] de mise en forme sur la sélection ou la ligne courante d'un [TextFieldValue].
     */
    fun applyFormat(value: TextFieldValue, action: MarkdownFormatAction): TextFieldValue {
        return when (action) {
            MarkdownFormatAction.HEADING_1 -> toggleLineHeading(value, "# ")
            MarkdownFormatAction.HEADING_2 -> toggleLineHeading(value, "## ")
            MarkdownFormatAction.HEADING_3 -> toggleLineHeading(value, "### ")
            MarkdownFormatAction.BOLD -> toggleInlineWrap(value, "**", MarkdownFormatAction.BOLD)
            MarkdownFormatAction.ITALIC -> toggleInlineWrap(value, "*", MarkdownFormatAction.ITALIC)
            MarkdownFormatAction.UNDERLINE -> toggleInlineWrap(value, "__", MarkdownFormatAction.UNDERLINE)
            MarkdownFormatAction.UNORDERED_LIST -> toggleUnorderedList(value)
            MarkdownFormatAction.ORDERED_LIST -> toggleOrderedList(value)
        }
    }

    /**
     * Gère intelligemment les modifications de texte lors de la frappe :
     * 1. Nettoyage automatique du marqueur invisible [ZERO_WIDTH_SPACE] dès qu'un vrai caractère est saisi.
     * 2. Gestion du `Backspace` sur un délimiteur Markdown masqué ou un préfixe de ligne.
     * 3. Continuation automatique des listes à puces (`- `) et numérotées (`1. `, `2. `) lors d'un appui sur `Entrée`.
     */
    fun handleTextChangeWithListContinuation(
        previous: TextFieldValue,
        current: TextFieldValue,
    ): TextFieldValue {
        val oldText = previous.text
        var working = current

        // 1. Si un caractère vient d'être tapé à côté d'un ZERO_WIDTH_SPACE, on retire le ZERO_WIDTH_SPACE
        if (working.text.length > oldText.length && working.text.contains(ZERO_WIDTH_SPACE)) {
            val cleaned = cleanupZeroWidthSpacesWithContent(working)
            if (cleaned != working) {
                working = cleaned
            }
        }

        // 2. Si l'utilisateur a appuyé sur Backspace (suppression d'exactement 1 caractère)
        if (working.selection.collapsed && working.text.length == oldText.length - 1) {
            return handleSmartBackspace(previous, working)
        }

        // 3. Vérifie si l'utilisateur vient d'insérer exactement un saut de ligne (\n) au curseur
        val newText = working.text
        val cursor = working.selection.min
        if (!working.selection.collapsed || newText.length != oldText.length + 1 || cursor <= 0) {
            return working
        }
        if (newText[cursor - 1] != '\n') {
            return working
        }

        val newlineIndex = cursor - 1
        val prevLineStart = newText.lastIndexOf('\n', startIndex = (newlineIndex - 1).coerceAtLeast(0))
            .let { if (it < 0 || newlineIndex == 0) 0 else it + 1 }
        val prevLine = newText.substring(prevLineStart, newlineIndex)

        // Cas A : Liste à puces "- "
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

        // Cas B : Liste numérotée "1. "
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

        return working
    }

    private fun cleanupZeroWidthSpacesWithContent(value: TextFieldValue): TextFieldValue {
        val sb = StringBuilder(value.text.length)
        var cursor = value.selection.min
        val text = value.text
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == ZERO_WIDTH_SPACE) {
                // Garde le ZERO_WIDTH_SPACE uniquement s'il est encore seul entre deux délimiteurs (ex: **\u200B** ou *\u200B* ou __\u200B__)
                val prevIsMarker = i > 0 && (text[i - 1] == '*' || text[i - 1] == '_')
                val nextIsMarker = i + 1 < text.length && (text[i + 1] == '*' || text[i + 1] == '_')
                if (prevIsMarker && nextIsMarker) {
                    sb.append(ch)
                } else {
                    if (i < cursor) cursor--
                }
            } else {
                sb.append(ch)
            }
            i++
        }
        val cleaned = sb.toString()
        val safeCursor = cursor.coerceIn(0, cleaned.length)
        return if (cleaned == text) value else TextFieldValue(cleaned, TextRange(safeCursor))
    }

    private fun handleSmartBackspace(previous: TextFieldValue, current: TextFieldValue): TextFieldValue {
        val oldText = previous.text
        val oldCursor = previous.selection.min.coerceIn(0, oldText.length)
        val newCursor = current.selection.min.coerceIn(0, current.text.length)

        // Si le curseur était juste après un préfixe de titre ou de liste (ex: "# |", "## |", "### |", "- |", "1. |")
        val lineStart = if (oldCursor == 0) 0 else oldText.lastIndexOf('\n', oldCursor - 1) + 1
        val lineEnd = oldText.indexOf('\n', oldCursor).let { if (it < 0) oldText.length else it }
        val oldLine = oldText.substring(lineStart, lineEnd)
        val prefixMatch = ANY_LINE_PREFIX_REGEX.find(oldLine)
        if (prefixMatch != null) {
            val prefixEnd = lineStart + prefixMatch.value.length
            if (oldCursor == prefixEnd && newCursor == prefixEnd - 1) {
                val updated = oldText.removeRange(lineStart, prefixEnd)
                return TextFieldValue(updated, TextRange(lineStart))
            }
        }

        // Si le curseur était dans un span inline ne contenant plus qu'un seul caractère (ou ZERO_WIDTH_SPACE),
        // supprimer ce dernier caractère supprime aussi les balises englobantes pour ne pas laisser "**" orphelin
        val spans = parseInlineSpans(oldText)
        for (span in spans) {
            val innerLen = span.contentEndExclusive - span.contentStart
            if (innerLen <= 1 && oldCursor in span.contentStart..span.fullEndExclusive) {
                val updated = oldText.removeRange(span.fullStart, span.fullEndExclusive)
                return TextFieldValue(updated, TextRange(span.fullStart.coerceIn(0, updated.length)))
            }
            // Si le curseur était juste après les balises fermantes (ex: "**mot**|") et que Backspace a mangé une '*'
            if (oldCursor == span.fullEndExclusive && newCursor == span.fullEndExclusive - 1) {
                return if (innerLen <= 1) {
                    val updated = oldText.removeRange(span.fullStart, span.fullEndExclusive)
                    TextFieldValue(updated, TextRange(span.fullStart.coerceIn(0, updated.length)))
                } else {
                    // Supprime le dernier caractère visible à l'intérieur du span tout en préservant les balises
                    val charToDeleteIndex = span.contentEndExclusive - 1
                    val updated = oldText.removeRange(charToDeleteIndex, charToDeleteIndex + 1)
                    TextFieldValue(updated, TextRange(charToDeleteIndex))
                }
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
        val minAllowed = if (newLineText.startsWith(targetPrefix)) lineStart + targetPrefix.length else lineStart
        val newStart = (selMin + delta).coerceIn(minAllowed, lineStart + newLineText.length)
        val newEnd = (selMax + delta).coerceIn(newStart, lineStart + newLineText.length)
        return TextFieldValue(
            text = updatedText,
            selection = TextRange(newStart, newEnd),
        )
    }

    private fun toggleInlineWrap(
        value: TextFieldValue,
        marker: String,
        action: MarkdownFormatAction,
    ): TextFieldValue {
        val text = value.text
        val min = value.selection.min.coerceIn(0, text.length)
        val max = value.selection.max.coerceIn(0, text.length)
        val markerLen = marker.length

        // Vérifie si le curseur ou la sélection se trouve déjà dans un span actif de ce type
        val existingSpan = parseInlineSpans(text).firstOrNull { span ->
            span.action == action && if (min == max) {
                min in span.contentStart..span.contentEndExclusive
            } else {
                min >= span.fullStart && max <= span.fullEndExclusive
            }
        }

        if (existingSpan != null) {
            val innerText = text.substring(existingSpan.contentStart, existingSpan.contentEndExclusive)
            // Cas 1 : Le span ne contenait que le placeholder invisible -> on supprime tout le span
            if (innerText == ZERO_WIDTH_SPACE.toString() || innerText.isEmpty()) {
                val updated = text.removeRange(existingSpan.fullStart, existingSpan.fullEndExclusive)
                return TextFieldValue(
                    text = updated,
                    selection = TextRange(existingSpan.fullStart.coerceIn(0, updated.length)),
                )
            }

            // Cas 2 : Curseur simple à la fin du mot stylisé -> on sort simplement du span pour continuer en texte normal
            if (min == max && min == existingSpan.contentEndExclusive) {
                return TextFieldValue(
                    text = text,
                    selection = TextRange(existingSpan.fullEndExclusive),
                )
            }

            // Cas 3 : Sélection ou curseur au milieu du mot -> on retire les balises (unwrap)
            val withoutClosing = text.removeRange(existingSpan.contentEndExclusive, existingSpan.fullEndExclusive)
            val withoutBoth = withoutClosing.removeRange(existingSpan.fullStart, existingSpan.contentStart)
            val newStart = (min - markerLen).coerceIn(existingSpan.fullStart, withoutBoth.length)
            val newEnd = (max - markerLen).coerceIn(newStart, withoutBoth.length)
            return TextFieldValue(
                text = withoutBoth,
                selection = TextRange(newStart, newEnd),
            )
        }

        // Si aucune sélection : insère un span avec ZERO_WIDTH_SPACE pour activer immédiatement le bouton
        if (min == max) {
            val insertion = "$marker$ZERO_WIDTH_SPACE$marker"
            val updated = StringBuilder(text).insert(min, insertion).toString()
            return TextFieldValue(
                text = updated,
                selection = TextRange(min + markerLen + 1),
            )
        }

        // Sélection non vide : entoure la sélection avec le marqueur
        val selected = text.substring(min, max)
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

    private fun parseInlineSpans(content: String): List<InlineSpan> {
        val spans = mutableListOf<InlineSpan>()

        BOLD_ITALIC_REGEX.findAll(content).forEach { match ->
            val start = match.range.first
            val endExclusive = match.range.last + 1
            spans.add(InlineSpan(MarkdownFormatAction.BOLD, start, endExclusive, 3))
            spans.add(InlineSpan(MarkdownFormatAction.ITALIC, start, endExclusive, 3))
        }

        BOLD_REGEX.findAll(content).forEach { match ->
            spans.add(InlineSpan(MarkdownFormatAction.BOLD, match.range.first, match.range.last + 1, 2))
        }

        ITALIC_REGEX.findAll(content).forEach { match ->
            spans.add(InlineSpan(MarkdownFormatAction.ITALIC, match.range.first, match.range.last + 1, 1))
        }

        UNDERLINE_REGEX.findAll(content).forEach { match ->
            spans.add(InlineSpan(MarkdownFormatAction.UNDERLINE, match.range.first, match.range.last + 1, 2))
        }

        return spans
    }

    /**
     * Formate un texte Markdown en [FormattedMarkdownResult] (**WYSIWYG**) :
     * - Masque complètement les symboles `# `, `## `, `### `, `***`, `**`, `*`, `__` et `\u200B`.
     * - Remplace les puces `- ` par de vraies puces visuelles `• `.
     * - Construit l'[OffsetMapping] exact garantissant la précision du curseur et du défilement des recherches.
     */
    fun formatForDisplay(
        content: String,
        textColor: Color,
        accentColor: Color,
        fontScale: Float = 1.0f,
        matchRanges: List<IntRange> = emptyList(),
        activeOccurrenceIndex: Int = 0,
        activeHighlightColor: Color = accentColor.copy(alpha = 0.42f),
        passiveHighlightColor: Color = accentColor.copy(alpha = 0.20f),
    ): FormattedMarkdownResult {
        if (content.isEmpty()) {
            return FormattedMarkdownResult(AnnotatedString(""), OffsetMapping.Identity)
        }

        val n = content.length
        val isHidden = BooleanArray(n)
        val replacementChars = CharArray(n) { content[it] }

        // Table des styles par plages dans les coordonnées d'origine (avant masquage)
        val origStyleSpans = mutableListOf<Triple<Int, Int, SpanStyle>>()

        // 1. Analyse ligne par ligne : Titres (#, ##, ###) et listes (- , 1. )
        var lineStart = 0
        while (lineStart <= n) {
            val lineEnd = content.indexOf('\n', lineStart).let { if (it < 0) n else it }
            val line = content.substring(lineStart, lineEnd)

            when {
                line.startsWith("### ") -> {
                    for (i in lineStart until (lineStart + 4).coerceAtMost(lineEnd)) {
                        isHidden[i] = true
                    }
                    origStyleSpans.add(
                        Triple(
                            lineStart,
                            lineEnd,
                            SpanStyle(
                                fontSize = (18.5f * fontScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                            ),
                        ),
                    )
                }

                line.startsWith("## ") -> {
                    for (i in lineStart until (lineStart + 3).coerceAtMost(lineEnd)) {
                        isHidden[i] = true
                    }
                    origStyleSpans.add(
                        Triple(
                            lineStart,
                            lineEnd,
                            SpanStyle(
                                fontSize = (20.5f * fontScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                            ),
                        ),
                    )
                }

                line.startsWith("# ") -> {
                    for (i in lineStart until (lineStart + 2).coerceAtMost(lineEnd)) {
                        isHidden[i] = true
                    }
                    origStyleSpans.add(
                        Triple(
                            lineStart,
                            lineEnd,
                            SpanStyle(
                                fontSize = (23f * fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                            ),
                        ),
                    )
                }

                else -> {
                    val ulMatch = UNORDERED_LIST_REGEX.find(line)
                    if (ulMatch != null) {
                        // Transforme '-' en puce '•' pour un rendu visuel propre sans décaler la longueur
                        replacementChars[lineStart] = '•'
                        origStyleSpans.add(
                            Triple(
                                lineStart,
                                lineStart + ulMatch.range.last + 1,
                                SpanStyle(
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold,
                                ),
                            ),
                        )
                    } else {
                        val olMatch = ORDERED_LIST_REGEX.find(line)
                        if (olMatch != null) {
                            origStyleSpans.add(
                                Triple(
                                    lineStart,
                                    lineStart + olMatch.range.last + 1,
                                    SpanStyle(
                                        color = accentColor,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }

            if (lineEnd == n) break
            lineStart = lineEnd + 1
        }

        // 2. Analyse des styles inline (***, **, *, __) et masquage des balises
        val inlineSpans = parseInlineSpans(content)
        for (span in inlineSpans) {
            for (i in span.fullStart until span.contentStart) {
                isHidden[i] = true
            }
            for (i in span.contentEndExclusive until span.fullEndExclusive) {
                isHidden[i] = true
            }
            val style = when (span.action) {
                MarkdownFormatAction.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                MarkdownFormatAction.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                MarkdownFormatAction.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                else -> continue
            }
            origStyleSpans.add(Triple(span.contentStart, span.contentEndExclusive, style))
        }

        // 3. Masquage des éventuels ZERO_WIDTH_SPACE d'ancrage
        for (i in 0 until n) {
            if (content[i] == ZERO_WIDTH_SPACE) {
                isHidden[i] = true
            }
        }

        // 4. Construction de la chaîne visible et des tables OffsetMapping
        val origToTrans = IntArray(n + 1)
        val visibleCount = isHidden.count { !it }
        val transToOrig = IntArray(visibleCount + 1)
        val visibleBuilder = StringBuilder(visibleCount)

        var transIdx = 0
        for (origIdx in 0 until n) {
            origToTrans[origIdx] = transIdx
            if (!isHidden[origIdx]) {
                transToOrig[transIdx] = origIdx
                visibleBuilder.append(replacementChars[origIdx])
                transIdx++
            }
        }
        origToTrans[n] = transIdx
        transToOrig[transIdx] = n

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return origToTrans[offset.coerceIn(0, n)]
            }

            override fun transformedToOriginal(offset: Int): Int {
                return transToOrig[offset.coerceIn(0, visibleCount)]
            }
        }

        // 5. Construction de l'AnnotatedString final avec styles convertis dans les coordonnées visibles
        val visibleText = visibleBuilder.toString()
        val annotated = buildAnnotatedString {
            append(visibleText)

            for ((origStart, origEnd, style) in origStyleSpans) {
                val tStart = origToTrans[origStart.coerceIn(0, n)]
                val tEnd = origToTrans[origEnd.coerceIn(0, n)]
                if (tStart < tEnd) {
                    addStyle(style, tStart, tEnd)
                }
            }

            matchRanges.forEachIndexed { index, range ->
                val oStart = range.first.coerceIn(0, n)
                val oEnd = (range.last + 1).coerceIn(oStart, n)
                val tStart = origToTrans[oStart]
                val tEnd = origToTrans[oEnd]
                if (tStart < tEnd) {
                    val isActive = index == activeOccurrenceIndex
                    addStyle(
                        style = SpanStyle(
                            background = if (isActive) activeHighlightColor else passiveHighlightColor,
                            fontWeight = if (isActive) FontWeight.Bold else null,
                        ),
                        start = tStart,
                        end = tEnd,
                    )
                }
            }
        }

        return FormattedMarkdownResult(
            annotatedString = annotated,
            offsetMapping = mapping,
        )
    }

    /**
     * Conservé pour compatibilité : renvoie directement l'[AnnotatedString] formaté sans symboles Markdown.
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
        return formatForDisplay(
            content = content,
            textColor = textColor,
            accentColor = accentColor,
            fontScale = fontScale,
            matchRanges = matchRanges,
            activeOccurrenceIndex = activeOccurrenceIndex,
            activeHighlightColor = activeHighlightColor,
            passiveHighlightColor = passiveHighlightColor,
        ).annotatedString
    }
}
