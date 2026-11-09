package fr.antoinehory.monsimplejournal

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.antoinehory.monsimplejournal.data.local.AppDatabase
import fr.antoinehory.monsimplejournal.data.preferences.UserPreferences
import fr.antoinehory.monsimplejournal.data.preferences.UserPreferencesRepository
import fr.antoinehory.monsimplejournal.data.repository.JournalRepository
import fr.antoinehory.monsimplejournal.ui.editor.EditorRoute
import fr.antoinehory.monsimplejournal.ui.editor.EditorViewModel
import fr.antoinehory.monsimplejournal.ui.theme.MonSimpleJournalTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val journalRepository = JournalRepository(database.journalDao())
        val userPreferencesRepository = UserPreferencesRepository(applicationContext)

        setContent {
            val userPreferences by remember(userPreferencesRepository) {
                userPreferencesRepository.userPreferencesFlow
            }.collectAsStateWithLifecycle(initialValue = UserPreferences())

            val editorViewModel: EditorViewModel = viewModel(
                factory = EditorViewModel.provideFactory(
                    journalRepository = journalRepository,
                    userPreferencesRepository = userPreferencesRepository,
                ),
            )

            MonSimpleJournalTheme(userPreferences = userPreferences) {
                EditorRoute(
                    viewModel = editorViewModel,
                    onNavigateToArchives = { /* Phase 4 */ },
                    onNavigateToSearch = { /* Phase 5 */ },
                    onNavigateToSettings = { /* Phase 6 */ },
                )
            }
        }
    }
}
