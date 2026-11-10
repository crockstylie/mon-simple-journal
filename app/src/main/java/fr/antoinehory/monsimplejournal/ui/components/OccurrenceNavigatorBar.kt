package fr.antoinehory.monsimplejournal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.antoinehory.monsimplejournal.R

/**
 * Barre flottante discrète (`2 / 5 [▲] [▼] [✕]`) permettant de naviguer :
 * - soit entre les occurrences d'un mot recherché (au sein du journal courant et des autres journaux),
 * - soit d'un journal d'archive à l'autre lorsque [onClose] est `null`.
 */
@Composable
fun OccurrenceNavigatorBar(
    activeIndex: Int,
    totalCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    customLabel: String? = null,
) {
    if (totalCount <= 0) return

    val labelText = customLabel ?: "${activeIndex + 1} / $totalCount"

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            IconButton(
                onClick = onPrevious,
                enabled = totalCount > 1,
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.occurrence_previous),
                )
            }

            IconButton(
                onClick = onNext,
                enabled = totalCount > 1,
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.occurrence_next),
                )
            }

            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.occurrence_close),
                    )
                }
            }
        }
    }
}
