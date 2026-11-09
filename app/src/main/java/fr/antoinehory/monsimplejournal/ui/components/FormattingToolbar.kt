package fr.antoinehory.monsimplejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.antoinehory.monsimplejournal.R
import fr.antoinehory.monsimplejournal.domain.MarkdownFormatAction

/**
 * Barre d'outils horizontale discrète pour la mise en forme rapide :
 * - Titres de niveau : `H1`, `H2`, `H3`
 * - Styles inline : **Gras** (`B`), *Italique* (`I`), <ins>Souligné</ins> (`U`)
 * - Listes : Liste à puces (`UL`) et Liste numérotée (`OL`)
 *
 * Chaque bouton s'illumine dynamiquement lorsque son style est présent dans [activeFormats]
 * à la position actuelle du curseur.
 */
@Composable
fun FormattingToolbar(
    activeFormats: Set<MarkdownFormatAction>,
    onFormatActionClicked: (MarkdownFormatAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Titres H1 / H2 / H3
            HeadingActionButton(
                label = "H1",
                isActive = MarkdownFormatAction.HEADING_1 in activeFormats,
                description = stringResource(R.string.format_heading_1),
                onClick = { onFormatActionClicked(MarkdownFormatAction.HEADING_1) },
            )
            HeadingActionButton(
                label = "H2",
                isActive = MarkdownFormatAction.HEADING_2 in activeFormats,
                description = stringResource(R.string.format_heading_2),
                onClick = { onFormatActionClicked(MarkdownFormatAction.HEADING_2) },
            )
            HeadingActionButton(
                label = "H3",
                isActive = MarkdownFormatAction.HEADING_3 in activeFormats,
                description = stringResource(R.string.format_heading_3),
                onClick = { onFormatActionClicked(MarkdownFormatAction.HEADING_3) },
            )

            VerticalDivider(
                modifier = Modifier
                    .height(20.dp)
                    .padding(horizontal = 4.dp),
                color = dividerColor,
            )

            // Gras / Italique / Souligné
            IconActionButton(
                icon = Icons.Outlined.FormatBold,
                isActive = MarkdownFormatAction.BOLD in activeFormats,
                contentDescription = stringResource(R.string.format_bold),
                onClick = { onFormatActionClicked(MarkdownFormatAction.BOLD) },
            )
            IconActionButton(
                icon = Icons.Outlined.FormatItalic,
                isActive = MarkdownFormatAction.ITALIC in activeFormats,
                contentDescription = stringResource(R.string.format_italic),
                onClick = { onFormatActionClicked(MarkdownFormatAction.ITALIC) },
            )
            IconActionButton(
                icon = Icons.Outlined.FormatUnderlined,
                isActive = MarkdownFormatAction.UNDERLINE in activeFormats,
                contentDescription = stringResource(R.string.format_underline),
                onClick = { onFormatActionClicked(MarkdownFormatAction.UNDERLINE) },
            )

            VerticalDivider(
                modifier = Modifier
                    .height(20.dp)
                    .padding(horizontal = 4.dp),
                color = dividerColor,
            )

            // Listes UL / OL
            IconActionButton(
                icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                isActive = MarkdownFormatAction.UNORDERED_LIST in activeFormats,
                contentDescription = stringResource(R.string.format_unordered_list),
                onClick = { onFormatActionClicked(MarkdownFormatAction.UNORDERED_LIST) },
            )
            IconActionButton(
                icon = Icons.Outlined.FormatListNumbered,
                isActive = MarkdownFormatAction.ORDERED_LIST in activeFormats,
                contentDescription = stringResource(R.string.format_ordered_list),
                onClick = { onFormatActionClicked(MarkdownFormatAction.ORDERED_LIST) },
            )
        }
    }
}

@Composable
private fun HeadingActionButton(
    label: String,
    isActive: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val activeBg = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        Color.Transparent
    }
    val textColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
    }

    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(activeBg)
            .semantics {
                contentDescription = description
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = textColor,
        )
    }
}

@Composable
private fun IconActionButton(
    icon: ImageVector,
    isActive: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val activeBg = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        Color.Transparent
    }
    val tintColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(activeBg),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = tintColor,
        )
    }
}
