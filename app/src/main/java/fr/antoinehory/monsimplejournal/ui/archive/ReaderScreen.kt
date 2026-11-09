package fr.antoinehory.monsimplejournal.ui.archive

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.antoinehory.monsimplejournal.R
import fr.antoinehory.monsimplejournal.ui.components.OccurrenceNavigatorBar
import fr.antoinehory.monsimplejournal.ui.theme.MonSimpleJournalTheme

/**
 * Route stateful de l'écran de consultation en lecture seule (`ReaderScreen`).
 */
@Composable
fun ReaderRoute(
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val createMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportSingleMarkdown(
                contentResolver = context.contentResolver,
                uri = uri,
                successMsgRes = R.string.reader_export_success,
                errorMsgRes = R.string.reader_export_error,
            )
        }
    }

    val exportMsgRes = uiState.exportMessageResId
    LaunchedEffect(exportMsgRes) {
        if (exportMsgRes != null) {
            Toast.makeText(context, exportMsgRes, Toast.LENGTH_SHORT).show()
            viewModel.onExportMessageShown()
        }
    }

    ReaderScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onExportMarkdownClicked = {
            createMdLauncher.launch("${uiState.isoDate}.md")
        },
        onPreviousOccurrence = viewModel::onPreviousOccurrence,
        onNextOccurrence = viewModel::onNextOccurrence,
        onClearHighlight = viewModel::onClearHighlight,
        modifier = modifier,
    )
}

/**
 * Écran de consultation d'un ancien journal en **mode 100 % Lecture Seule** (ADR-002).
 *
 * Utilise [SelectionContainer] + [Text] sans aucun champ d'édition afin qu'aucune modification
 * accidentelle ne soit possible et qu'aucun clavier virtuel ne s'ouvre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    uiState: ReaderUiState,
    onNavigateBack: () -> Unit,
    onExportMarkdownClicked: () -> Unit,
    onPreviousOccurrence: () -> Unit,
    onNextOccurrence: () -> Unit,
    onClearHighlight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val accentColor = MaterialTheme.colorScheme.primary
    val scrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Saut direct (auto-scroll) jusqu'à l'occurrence recherchée via TextLayoutResult.getBoundingBox
    val targetCharOffset = uiState.targetCharOffset
    LaunchedEffect(targetCharOffset, textLayoutResult) {
        val layout = textLayoutResult ?: return@LaunchedEffect
        val offset = targetCharOffset ?: return@LaunchedEffect
        if (uiState.content.isNotEmpty() && offset in 0..uiState.content.length) {
            val safeOffset = offset.coerceIn(0, (uiState.content.length - 1).coerceAtLeast(0))
            val rect = layout.getBoundingBox(safeOffset)
            val targetScrollY = (rect.top.toInt() - 120).coerceAtLeast(0)
            scrollState.animateScrollTo(targetScrollY)
        }
    }

    val highlightedText = remember(
        uiState.content,
        uiState.matchRanges,
        uiState.activeOccurrenceIndex,
        textColor,
        accentColor,
    ) {
        fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.buildMarkdownAnnotatedString(
            content = uiState.content,
            textColor = textColor,
            accentColor = accentColor,
            matchRanges = uiState.matchRanges,
            activeOccurrenceIndex = uiState.activeOccurrenceIndex,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = textColor,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = uiState.formattedTitle,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.reader_read_only_badge),
                                style = MaterialTheme.typography.labelMedium,
                                color = textColor.copy(alpha = 0.55f),
                            )
                        }
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
                        IconButton(onClick = onExportMarkdownClicked) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = stringResource(R.string.reader_export_md),
                            )
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            SelectionContainer {
                Text(
                    text = highlightedText,
                    style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                )
            }

            if (uiState.matchRanges.isNotEmpty()) {
                OccurrenceNavigatorBar(
                    activeIndex = uiState.activeOccurrenceIndex,
                    totalCount = uiState.matchRanges.size,
                    onPrevious = onPreviousOccurrence,
                    onNext = onNextOccurrence,
                    onClose = onClearHighlight,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderScreenPreview() {
    MonSimpleJournalTheme {
        ReaderScreen(
            uiState = ReaderUiState(
                isoDate = "2026-11-08",
                formattedTitle = "Dimanche 8 novembre 2026",
                content = "Une longue promenade en forêt cet après-midi.\n\nCe journal passé est en lecture seule : je peux sélectionner ou exporter mes souvenirs en .MD, mais jamais les altérer par erreur.",
                isLoading = false,
            ),
            onNavigateBack = {},
            onExportMarkdownClicked = {},
            onPreviousOccurrence = {},
            onNextOccurrence = {},
            onClearHighlight = {},
        )
    }
}
