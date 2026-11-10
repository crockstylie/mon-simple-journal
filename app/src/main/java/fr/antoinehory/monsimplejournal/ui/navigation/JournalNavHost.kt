package fr.antoinehory.monsimplejournal.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.antoinehory.monsimplejournal.data.preferences.UserPreferencesRepository
import fr.antoinehory.monsimplejournal.data.repository.JournalRepository
import fr.antoinehory.monsimplejournal.ui.archive.ArchiveRoute
import fr.antoinehory.monsimplejournal.ui.archive.ArchiveViewModel
import fr.antoinehory.monsimplejournal.ui.archive.ReaderRoute
import fr.antoinehory.monsimplejournal.ui.archive.ReaderViewModel
import fr.antoinehory.monsimplejournal.ui.editor.EditorRoute
import fr.antoinehory.monsimplejournal.ui.editor.EditorUiEvent
import fr.antoinehory.monsimplejournal.ui.editor.EditorViewModel
import fr.antoinehory.monsimplejournal.ui.info.InfoScreen
import fr.antoinehory.monsimplejournal.ui.search.SearchRoute
import fr.antoinehory.monsimplejournal.ui.search.SearchViewModel
import fr.antoinehory.monsimplejournal.ui.settings.SettingsRoute
import fr.antoinehory.monsimplejournal.ui.settings.SettingsViewModel

/**
 * Routes de navigation de **Mon Simple Journal**.
 */
object JournalDestinations {
    const val EDITOR = "editor"
    const val ARCHIVES = "archives"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val INFO = "info"

    const val ARG_ISO_DATE = "isoDate"
    const val ARG_QUERY = "query"
    const val ARG_OCCURRENCE_INDEX = "occurrenceIndex"

    const val READER_ROUTE_PATTERN =
        "reader/{$ARG_ISO_DATE}?$ARG_QUERY={$ARG_QUERY}&$ARG_OCCURRENCE_INDEX={$ARG_OCCURRENCE_INDEX}"

    fun readerRoute(
        isoDate: String,
        highlightQuery: String = "",
        occurrenceIndex: Int = 0,
    ): String {
        val encodedQuery = Uri.encode(highlightQuery)
        return "reader/$isoDate?$ARG_QUERY=$encodedQuery&$ARG_OCCURRENCE_INDEX=$occurrenceIndex"
    }
}

/**
 * Graphe de navigation principal de **Mon Simple Journal**, démarrant directement
 * sur l'éditeur du jour (`JournalDestinations.EDITOR`).
 */
@Composable
fun JournalNavHost(
    journalRepository: JournalRepository,
    userPreferencesRepository: UserPreferencesRepository,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val editorViewModel: EditorViewModel = viewModel(
        factory = EditorViewModel.provideFactory(
            journalRepository = journalRepository,
            userPreferencesRepository = userPreferencesRepository,
        ),
    )

    val editorUiState by editorViewModel.uiState.collectAsStateWithLifecycle()
    val activityLifecycleOwner = LocalLifecycleOwner.current

    // Observation du cycle de vie au niveau de l'Activity (et non de la seule destination Editor)
    // afin que la mise en arrière-plan et le réveil un nouveau jour fonctionnent quel que soit l'écran actif.
    DisposableEffect(activityLifecycleOwner, editorViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> editorViewModel.onEvent(EditorUiEvent.OnLifecycleStopped)
                Lifecycle.Event.ON_RESUME -> editorViewModel.onEvent(EditorUiEvent.OnLifecycleResumed)
                else -> Unit
            }
        }
        activityLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            activityLifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Lorsqu'une nouvelle journée démarre, ramener automatiquement l'utilisateur sur l'éditeur du jour
    var lastObservedSessionDate by remember { mutableStateOf(editorUiState.sessionDate) }
    LaunchedEffect(editorUiState.sessionDate) {
        if (editorUiState.sessionDate != lastObservedSessionDate) {
            lastObservedSessionDate = editorUiState.sessionDate
            navController.popBackStack(JournalDestinations.EDITOR, inclusive = false)
        }
    }

    NavHost(
        navController = navController,
        startDestination = JournalDestinations.EDITOR,
        modifier = modifier,
    ) {
        composable(route = JournalDestinations.EDITOR) {
            EditorRoute(
                viewModel = editorViewModel,
                onNavigateToArchives = {
                    navController.navigate(JournalDestinations.ARCHIVES)
                },
                onNavigateToSearch = {
                    navController.navigate(JournalDestinations.SEARCH)
                },
                onNavigateToSettings = {
                    navController.navigate(JournalDestinations.SETTINGS)
                },
                onJumpToArchiveOccurrence = { isoDate, query, occurrenceIndex ->
                    navController.navigate(
                        JournalDestinations.readerRoute(
                            isoDate = isoDate,
                            highlightQuery = query,
                            occurrenceIndex = occurrenceIndex,
                        ),
                    )
                },
            )
        }

        composable(route = JournalDestinations.ARCHIVES) {
            val activeSessionDate = editorUiState.sessionDate
            val archiveViewModel: ArchiveViewModel = viewModel(
                key = "archives_$activeSessionDate",
                factory = ArchiveViewModel.provideFactory(
                    journalRepository = journalRepository,
                    userPreferencesRepository = userPreferencesRepository,
                    activeSessionDate = activeSessionDate,
                ),
            )
            ArchiveRoute(
                viewModel = archiveViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenPastJournal = { isoDate ->
                    navController.navigate(JournalDestinations.readerRoute(isoDate = isoDate))
                },
            )
        }

        composable(route = JournalDestinations.SEARCH) {
            val searchViewModel: SearchViewModel = viewModel(
                factory = SearchViewModel.provideFactory(
                    journalRepository = journalRepository,
                    userPreferencesRepository = userPreferencesRepository,
                ),
            )
            SearchRoute(
                viewModel = searchViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOccurrenceClicked = { isoDate, query, occurrenceIndex ->
                    val activeSessionIsoDate = editorViewModel.uiState.value.sessionDate.toString()
                    if (isoDate == activeSessionIsoDate) {
                        editorViewModel.onEvent(
                            EditorUiEvent.OnHighlightOccurrenceRequested(
                                query = query,
                                occurrenceIndex = occurrenceIndex,
                            ),
                        )
                        navController.popBackStack(JournalDestinations.EDITOR, inclusive = false)
                    } else {
                        navController.navigate(
                            JournalDestinations.readerRoute(
                                isoDate = isoDate,
                                highlightQuery = query,
                                occurrenceIndex = occurrenceIndex,
                            ),
                        )
                    }
                },
            )
        }

        composable(route = JournalDestinations.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(
                    userPreferencesRepository = userPreferencesRepository,
                    journalRepository = journalRepository,
                ),
            )
            SettingsRoute(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInfo = { navController.navigate(JournalDestinations.INFO) },
                onBackupImported = {
                    editorViewModel.reloadActiveSessionFromStorage()
                },
            )
        }

        composable(route = JournalDestinations.INFO) {
            InfoScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = JournalDestinations.READER_ROUTE_PATTERN,
            arguments = listOf(
                navArgument(JournalDestinations.ARG_ISO_DATE) {
                    type = NavType.StringType
                },
                navArgument(JournalDestinations.ARG_QUERY) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(JournalDestinations.ARG_OCCURRENCE_INDEX) {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) { backStackEntry ->
            val isoDate = backStackEntry.arguments?.getString(JournalDestinations.ARG_ISO_DATE).orEmpty()
            val query = backStackEntry.arguments?.getString(JournalDestinations.ARG_QUERY).orEmpty()
            val occurrenceIndex = backStackEntry.arguments?.getInt(JournalDestinations.ARG_OCCURRENCE_INDEX) ?: 0
            val activeSessionDate = editorUiState.sessionDate

            val readerViewModel: ReaderViewModel = viewModel(
                key = "reader_${isoDate}_${query}_${occurrenceIndex}_$activeSessionDate",
                factory = ReaderViewModel.provideFactory(
                    isoDate = isoDate,
                    initialHighlightQuery = query,
                    initialOccurrenceIndex = occurrenceIndex,
                    activeSessionDate = activeSessionDate,
                    journalRepository = journalRepository,
                    userPreferencesRepository = userPreferencesRepository,
                ),
            )

            ReaderRoute(
                viewModel = readerViewModel,
                onNavigateBack = { navController.popBackStack() },
                onJumpToEditorOccurrence = { targetQuery, targetOccurrenceIndex ->
                    editorViewModel.onEvent(
                        EditorUiEvent.OnHighlightOccurrenceRequested(
                            query = targetQuery,
                            occurrenceIndex = targetOccurrenceIndex,
                        ),
                    )
                    navController.popBackStack(JournalDestinations.EDITOR, inclusive = false)
                },
            )
        }
    }
}
