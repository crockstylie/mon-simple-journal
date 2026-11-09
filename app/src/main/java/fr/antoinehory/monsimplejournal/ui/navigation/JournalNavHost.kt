package fr.antoinehory.monsimplejournal.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            )
        }

        composable(route = JournalDestinations.ARCHIVES) {
            val archiveViewModel: ArchiveViewModel = viewModel(
                factory = ArchiveViewModel.provideFactory(
                    journalRepository = journalRepository,
                    userPreferencesRepository = userPreferencesRepository,
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

            val readerViewModel: ReaderViewModel = viewModel(
                key = "reader_${isoDate}_${query}_$occurrenceIndex",
                factory = ReaderViewModel.provideFactory(
                    isoDate = isoDate,
                    initialHighlightQuery = query,
                    initialOccurrenceIndex = occurrenceIndex,
                    journalRepository = journalRepository,
                    userPreferencesRepository = userPreferencesRepository,
                ),
            )

            ReaderRoute(
                viewModel = readerViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
