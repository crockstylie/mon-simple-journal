package fr.antoinehory.monsimplejournal.ui.archive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.antoinehory.monsimplejournal.R
import fr.antoinehory.monsimplejournal.ui.theme.MonSimpleJournalTheme

/**
 * Route stateful de l'écran des journaux précédents (`ArchiveScreen`).
 */
@Composable
fun ArchiveRoute(
    viewModel: ArchiveViewModel,
    onNavigateBack: () -> Unit,
    onOpenPastJournal: (isoDate: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ArchiveScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onOpenPastJournal = onOpenPastJournal,
        modifier = modifier,
    )
}

/**
 * Écran listant tous les journaux des jours précédents par ordre antéchronologique.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    uiState: ArchiveUiState,
    onNavigateBack: () -> Unit,
    onOpenPastJournal: (isoDate: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onBackground

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = textColor,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.archives_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = textColor,
                        navigationIconContentColor = textColor,
                    ),
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                )
            }
        },
    ) { innerPadding ->
        if (!uiState.isLoading && uiState.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.archives_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = textColor,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.archives_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = textColor.copy(alpha = 0.65f),
                    )
                }
            }
        } else {
            val wordSingleTemplate = stringResource(R.string.archives_word_count_single)
            val wordPluralTemplate = stringResource(R.string.archives_word_count)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding),
                contentPadding = innerPadding,
            ) {
                items(
                    items = uiState.items,
                    key = { it.isoDate },
                    contentType = { "archive_entry" },
                ) { item ->
                    val wordCountLabel = if (item.wordCount <= 1) {
                        wordSingleTemplate
                    } else {
                        String.format(wordPluralTemplate, item.wordCount)
                    }

                    ArchiveEntryRow(
                        item = item,
                        wordCountLabel = wordCountLabel,
                        onClick = { onOpenPastJournal(item.isoDate) },
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveEntryRow(
    item: ArchiveItemUiModel,
    wordCountLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onBackground

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 20.dp, vertical = 16.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.formattedDateTitle,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = wordCountLabel,
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.55f),
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        if (item.firstLinePreview.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.firstLinePreview,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ArchiveScreenPreview() {
    MonSimpleJournalTheme {
        ArchiveScreen(
            uiState = ArchiveUiState(
                items = listOf(
                    ArchiveItemUiModel(
                        isoDate = "2026-11-08",
                        formattedDateTitle = "Dimanche 8 novembre 2026",
                        firstLinePreview = "Une longue promenade en forêt cet après-midi sous les feuilles d'automne.",
                        wordCount = 142,
                    ),
                    ArchiveItemUiModel(
                        isoDate = "2026-11-07",
                        formattedDateTitle = "Samedi 7 novembre 2026",
                        firstLinePreview = "Lecture au calme et préparation du week-end.",
                        wordCount = 85,
                    ),
                ),
                isLoading = false,
            ),
            onNavigateBack = {},
            onOpenPastJournal = {},
        )
    }
}
