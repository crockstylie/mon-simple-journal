package fr.antoinehory.monsimplejournal.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.antoinehory.monsimplejournal.R
import fr.antoinehory.monsimplejournal.ui.components.OccurrenceNavigatorBar
import fr.antoinehory.monsimplejournal.ui.theme.MonSimpleJournalTheme

/**
 * Route stateful connectant [EditorViewModel] au Composable stateless [EditorScreen]
 * et gérant les événements de cycle de vie (`ON_STOP` / `ON_RESUME`) pour la sauvegarde
 * immédiate et la continuité à minuit (`23h55` ➔ `00h00`).
 */
@Composable
fun EditorRoute(
    viewModel: EditorViewModel,
    onNavigateToArchives: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onEvent(EditorUiEvent.OnLifecycleStopped)
                Lifecycle.Event.ON_RESUME -> viewModel.onEvent(EditorUiEvent.OnLifecycleResumed)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    EditorScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToArchives = onNavigateToArchives,
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier,
    )
}

/**
 * Écran principal minimaliste d'écriture quotidienne de **Mon Simple Journal**.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    uiState: EditorUiState,
    onEvent: (EditorUiEvent) -> Unit,
    onNavigateToArchives: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenModifier = if (uiState.keepScreenOn) {
        modifier.keepScreenOn()
    } else {
        modifier
    }

    val scrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Défilement automatique vers l'occurrence recherchée lorsque le journal du jour est ciblé
    val targetCharOffset = uiState.targetCharOffset
    LaunchedEffect(targetCharOffset, textLayoutResult) {
        val layout = textLayoutResult ?: return@LaunchedEffect
        val offset = targetCharOffset ?: return@LaunchedEffect
        if (offset in 0..uiState.textFieldValue.text.length) {
            val rect = layout.getBoundingBox(offset.coerceAtMost((uiState.textFieldValue.text.length - 1).coerceAtLeast(0)))
            val targetScrollY = (rect.top.toInt() - 120).coerceAtLeast(0)
            scrollState.animateScrollTo(targetScrollY)
        }
    }

    val accentColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground
    val highlightVisualTransformation = remember(
        uiState.matchRanges,
        uiState.activeOccurrenceIndex,
        accentColor,
    ) {
        createHighlightTransformation(
            matchRanges = uiState.matchRanges,
            activeIndex = uiState.activeOccurrenceIndex,
            activeHighlightColor = accentColor.copy(alpha = 0.42f),
            passiveHighlightColor = accentColor.copy(alpha = 0.20f),
        )
    }

    Scaffold(
        modifier = screenModifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = textColor,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.formattedTitle,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        IconButton(onClick = { onEvent(EditorUiEvent.OnInsertTimestampClicked) }) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = stringResource(R.string.action_insert_time),
                            )
                        }
                        IconButton(onClick = onNavigateToArchives) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = stringResource(R.string.action_archives),
                            )
                        }
                        IconButton(onClick = onNavigateToSearch) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.action_search),
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.action_settings),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = textColor,
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
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            BasicTextField(
                value = uiState.textFieldValue,
                onValueChange = { onEvent(EditorUiEvent.OnTextChanged(it)) },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = textColor,
                ),
                cursorBrush = SolidColor(accentColor),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                visualTransformation = highlightVisualTransformation,
                onTextLayout = { textLayoutResult = it },
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (uiState.textFieldValue.text.isEmpty() && !uiState.isLoading) {
                            Text(
                                text = stringResource(R.string.editor_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor.copy(alpha = 0.42f),
                            )
                        }
                        innerTextField()
                    }
                },
            )

            if (uiState.matchRanges.isNotEmpty()) {
                OccurrenceNavigatorBar(
                    activeIndex = uiState.activeOccurrenceIndex,
                    totalCount = uiState.matchRanges.size,
                    onPrevious = { onEvent(EditorUiEvent.OnPreviousOccurrenceClicked) },
                    onNext = { onEvent(EditorUiEvent.OnNextOccurrenceClicked) },
                    onClose = { onEvent(EditorUiEvent.OnClearHighlightClicked) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                )
            }
        }
    }
}

private fun createHighlightTransformation(
    matchRanges: List<IntRange>,
    activeIndex: Int,
    activeHighlightColor: androidx.compose.ui.graphics.Color,
    passiveHighlightColor: androidx.compose.ui.graphics.Color,
): VisualTransformation {
    if (matchRanges.isEmpty()) return VisualTransformation.None
    return VisualTransformation { originalText ->
        val annotated = buildAnnotatedString {
            append(originalText.text)
            matchRanges.forEachIndexed { index, range ->
                val start = range.first.coerceIn(0, originalText.length)
                val endExclusive = (range.last + 1).coerceIn(start, originalText.length)
                if (start < endExclusive) {
                    val isActive = index == activeIndex
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
        TransformedText(annotated, OffsetMapping.Identity)
    }
}

@Preview(showBackground = true)
@Composable
private fun EditorScreenPreview() {
    MonSimpleJournalTheme {
        EditorScreen(
            uiState = EditorUiState(
                formattedTitle = "Lundi 9 novembre 2026",
                textFieldValue = TextFieldValue(
                    "Aujourd'hui j'ai commencé à écrire dans Mon Simple Journal.\n\n— 23:55 — Même après minuit, ma session continue sans aucune coupure.",
                ),
                isLoading = false,
            ),
            onEvent = {},
            onNavigateToArchives = {},
            onNavigateToSearch = {},
            onNavigateToSettings = {},
        )
    }
}
