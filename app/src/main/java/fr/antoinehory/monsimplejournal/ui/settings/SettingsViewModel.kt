package fr.antoinehory.monsimplejournal.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.antoinehory.monsimplejournal.data.backup.BackupResult
import fr.antoinehory.monsimplejournal.data.backup.MarkdownZipManager
import fr.antoinehory.monsimplejournal.data.preferences.UserPreferences
import fr.antoinehory.monsimplejournal.data.preferences.UserPreferencesRepository
import fr.antoinehory.monsimplejournal.data.repository.JournalRepository
import fr.antoinehory.monsimplejournal.domain.ContrastPreset
import fr.antoinehory.monsimplejournal.domain.DateFormatStyle
import fr.antoinehory.monsimplejournal.domain.FontFamilyOption
import fr.antoinehory.monsimplejournal.domain.FontScaleOption
import fr.antoinehory.monsimplejournal.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * État UI de l'écran des paramètres (`SettingsScreen`).
 */
data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val todayDate: LocalDate = LocalDate.now(),
    val isBackupInProgress: Boolean = false,
    val lastBackupResult: BackupResult? = null,
) {
    /** Nom de fichier suggéré lors de l'export complet en `.zip`. */
    val suggestedZipFileName: String
        get() {
            val compactDate = todayDate.format(DateTimeFormatter.BASIC_ISO_DATE)
            return "mon_simple_journal_backup_$compactDate.zip"
        }
}

/**
 * ViewModel pilotant les préférences utilisateur (format de date, thèmes Dark/Light et presets,
 * couleurs personnalisées, famille et taille de police, maintien de l'écran actif, verrouillage biométrique)
 * ainsi que l'export et l'import complet `.zip` / `.md`.
 */
class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val journalRepository: JournalRepository,
    private val markdownZipManager: MarkdownZipManager = MarkdownZipManager(),
    clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val todayDate: LocalDate = LocalDate.now(clock)
    private val isBackupInProgressFlow = MutableStateFlow(false)
    private val lastBackupResultFlow = MutableStateFlow<BackupResult?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesRepository.userPreferencesFlow,
        isBackupInProgressFlow,
        lastBackupResultFlow,
    ) { prefs, inProgress, backupResult ->
        SettingsUiState(
            preferences = prefs,
            todayDate = todayDate,
            isBackupInProgress = inProgress,
            lastBackupResult = backupResult,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState(todayDate = todayDate),
    )

    fun onDateFormatSelected(style: DateFormatStyle) {
        viewModelScope.launch {
            userPreferencesRepository.setDateFormatStyle(style)
        }
    }

    fun onThemeModeAutoSelected() {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(ThemeMode.SYSTEM_AUTO)
        }
    }

    fun onContrastPresetSelected(preset: ContrastPreset) {
        viewModelScope.launch {
            userPreferencesRepository.selectContrastPreset(preset)
        }
    }

    fun onCustomColorsSaved(backgroundColorArgb: Long, textColorArgb: Long) {
        viewModelScope.launch {
            userPreferencesRepository.setCustomColors(
                backgroundColorArgb = backgroundColorArgb,
                textColorArgb = textColorArgb,
            )
        }
    }

    fun onFontFamilySelected(option: FontFamilyOption) {
        viewModelScope.launch {
            userPreferencesRepository.setFontFamilyOption(option)
        }
    }

    fun onFontScaleSelected(option: FontScaleOption) {
        viewModelScope.launch {
            userPreferencesRepository.setFontScaleOption(option)
        }
    }

    fun onKeepScreenOnToggled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setKeepScreenOn(enabled)
        }
    }

    fun onBiometricLockToggled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBiometricLockEnabled(enabled)
        }
    }

    /**
     * Exporte l'intégralité des journaux dans une archive `.zip` (contenant des fichiers `YYYY-MM-DD.md`).
     */
    fun exportAllToZip(contentResolver: ContentResolver, targetUri: Uri) {
        if (isBackupInProgressFlow.value) return
        viewModelScope.launch {
            isBackupInProgressFlow.value = true
            val entries = journalRepository.getAllEntriesForExport()
            val currentStyle = uiState.value.preferences.dateFormatStyle
            val result = markdownZipManager.exportEntriesToZipUri(
                contentResolver = contentResolver,
                uri = targetUri,
                entries = entries,
                dateFormatStyle = currentStyle,
            )
            isBackupInProgressFlow.value = false
            lastBackupResultFlow.value = result
        }
    }

    /**
     * Importe une archive `.zip` ou un fichier `.md` avec fusion intelligente sans perte.
     */
    fun importFromUri(
        contentResolver: ContentResolver,
        sourceUri: Uri,
        onImportCompleted: () -> Unit = {},
    ) {
        if (isBackupInProgressFlow.value) return
        viewModelScope.launch {
            isBackupInProgressFlow.value = true
            val result = runCatching {
                val parsedEntries = markdownZipManager.readEntriesFromUri(
                    contentResolver = contentResolver,
                    uri = sourceUri,
                )
                val mergedCount = journalRepository.importAndMergeEntries(parsedEntries)
                BackupResult.ImportSuccess(mergedCount)
            }.getOrElse { e ->
                BackupResult.Error(e.message ?: "Erreur d'import")
            }
            isBackupInProgressFlow.value = false
            lastBackupResultFlow.value = result
            if (result is BackupResult.ImportSuccess) {
                onImportCompleted()
            }
        }
    }

    fun onBackupResultConsumed() {
        lastBackupResultFlow.value = null
    }

    /**
     * Génère 5 anciens journaux de démonstration (Lorem Ipsum) pour tester l'historique (Archives)
     * et la recherche sur smartphone.
     */
    fun seedDemoEntries(onDone: (Int) -> Unit) {
        if (isBackupInProgressFlow.value) return
        viewModelScope.launch {
            isBackupInProgressFlow.value = true
            val count = journalRepository.seedDemoPastEntries(todayDate)
            isBackupInProgressFlow.value = false
            onDone(count)
        }
    }

    /**
     * Supprime uniquement les anciens journaux de démonstration (Lorem Ipsum) sans toucher aux vrais journaux.
     */
    fun clearDemoEntries(onDone: (Int) -> Unit) {
        if (isBackupInProgressFlow.value) return
        viewModelScope.launch {
            isBackupInProgressFlow.value = true
            val count = journalRepository.clearDemoPastEntries()
            isBackupInProgressFlow.value = false
            onDone(count)
        }
    }

    companion object {
        fun provideFactory(
            userPreferencesRepository: UserPreferencesRepository,
            journalRepository: JournalRepository,
            clock: Clock = Clock.systemDefaultZone(),
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    userPreferencesRepository = userPreferencesRepository,
                    journalRepository = journalRepository,
                    clock = clock,
                ) as T
            }
        }
    }
}
