package fr.antoinehory.monsimplejournal.ui.search

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.antoinehory.monsimplejournal.R
import fr.antoinehory.monsimplejournal.ui.theme.MonSimpleJournalTheme

/**
 * Route stateful de l'écran de recherche d'occurrences (`SearchScreen`).
 */
@Composable
fun SearchRoute(
    viewModel: SearchViewModel,
    onNavigateBack: () -> Unit,
    onOccurrenceClicked: (isoDate: String, query: String, occurrenceIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SearchScreen(
        uiState = uiState,
        onQueryChanged = viewModel::onQueryChanged,
        onClearQuery = viewModel::onClearQuery,
        onNavigateBack = onNavigateBack,
        onOccurrenceClicked = { item ->
            onOccurrenceClicked(item.isoDate, uiState.query.trim(), item.occurrenceIndex)
        },
        modifier = modifier,
    )
}

/**
 * Écran de recherche d'occurrences avec extraits contextuels et saut direct.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onClearQuery: () -> Unit,
    onNavigateBack: () -> Unit,
    onOccurrenceClicked: (SearchOccurrenceUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val accentColor = MaterialTheme.colorScheme.primary
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = textColor,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        BasicTextField(
                            value = uiState.query,
                            onValueChange = onQueryChanged,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = textColor,
                            ),
                            cursorBrush = SolidColor(accentColor),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (uiState.query.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.search_placeholder),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = textColor.copy(alpha = 0.45f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
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
                    actions = {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = onClearQuery) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.search_clear),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = textColor,
                        navigationIconContentColor = textColor,
                        actionIconContentColor = textColor.copy(alpha = 0.78f),
                    ),
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                )
            }
        },
    ) { innerPadding ->
        val trimmedQuery = uiState.query.trim()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            when {
                trimmedQuery.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.search_idle_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = textColor.copy(alpha = 0.65f),
                        )
                    }
                }

                uiState.results.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.search_no_results, trimmedQuery),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = textColor.copy(alpha = 0.65f),
                        )
                    }
                }

                else -> {
                    val totalCount = uiState.results.size
                    val countHeader = if (totalCount == 1) {
                        stringResource(R.string.search_results_count_single)
                    } else {
                        stringResource(R.string.search_results_count_plural, totalCount)
                    }
                    val badgeTemplate = stringResource(R.string.search_occurrence_badge)

                    Text(
                        text = countHeader,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = uiState.results,
                            key = { it.id },
                            contentType = { "search_occurrence" },
                        ) { occurrence ->
                            val badgeLabel = String.format(
                                badgeTemplate,
                                occurrence.occurrenceIndex + 1,
                                occurrence.totalOccurrencesInEntry,
                            )

                            SearchOccurrenceRow(
                                item = occurrence,
                                badgeLabel = badgeLabel,
                                onClick = { onOccurrenceClicked(occurrence) },
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
    }
}

@Composable
private fun SearchOccurrenceRow(
    item: SearchOccurrenceUiModel,
    badgeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val accentColor = MaterialTheme.colorScheme.primary

    val snippetAnnotated = remember(
        item.snippetBefore,
        item.matchText,
        item.snippetAfter,
        accentColor,
        textColor,
    ) {
        buildAnnotatedString {
            append(item.snippetBefore)
            withStyle(
                style = SpanStyle(
                    background = accentColor.copy(alpha = 0.35f),
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                ),
            ) {
                append(item.matchText)
            }
            append(item.snippetAfter)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 20.dp, vertical = 14.dp)),
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
            if (item.totalOccurrencesInEntry > 1) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = snippetAnnotated,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor.copy(alpha = 0.82f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
    MonSimpleJournalTheme {
        SearchScreen(
            uiState = SearchUiState(
                query = "ete",
                results = listOf(
                    SearchOccurrenceUiModel(
                        id = "2026-11-08_0",
                        isoDate = "2026-11-08",
                        formattedDateTitle = "Dimanche 8 novembre 2026",
                        occurrenceIndex = 0,
                        totalOccurrencesInEntry = 2,
                        charOffset = 24,
                        snippetBefore = "…Je repensais à nos vacances d'",
                        matchText = "été",
                        snippetAfter = " au bord de l'océan Atlantique…",
                    ),
                    SearchOccurrenceUiModel(
                        id = "2026-11-08_1",
                        isoDate = "2026-11-08",
                        formattedDateTitle = "Dimanche 8 novembre 2026",
                        occurrenceIndex = 1,
                        totalOccurrencesInEntry = 2,
                        charOffset = 140,
                        snippetBefore = "…Cet ",
                        matchText = "Été",
                        snippetAfter = " restera un souvenir inoubliable…",
                    ),
                ),
            ),
            onQueryChanged = {},
            onClearQuery = {},
            onNavigateBack = {},
            onOccurrenceClicked = {},
        )
    }
}
