package fr.antoinehory.monsimplejournal

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import fr.antoinehory.monsimplejournal.data.backup.MarkdownZipManager
import fr.antoinehory.monsimplejournal.data.local.dao.JournalDao
import fr.antoinehory.monsimplejournal.data.local.entity.JournalEntryEntity
import fr.antoinehory.monsimplejournal.data.preferences.UserPreferencesRepository
import fr.antoinehory.monsimplejournal.data.repository.JournalRepository
import fr.antoinehory.monsimplejournal.domain.DateFormatStyle
import fr.antoinehory.monsimplejournal.domain.DiacriticsMatcher
import fr.antoinehory.monsimplejournal.domain.JournalDateFormatter
import fr.antoinehory.monsimplejournal.domain.JournalEntry
import fr.antoinehory.monsimplejournal.ui.editor.EditorUiEvent
import fr.antoinehory.monsimplejournal.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Suite complète de tests unitaires JVM pour **Mon Simple Journal** (Phase 7) :
 * 1. Recherche d'occurrences insensible aux accents et à la casse (`DiacriticsMatcher`) + exactitude des `charOffset`.
 * 2. Formatage des dates en toutes lettres et de l'horodatage (`JournalDateFormatter`).
 * 3. Garantie stricte de lecture seule sur les journaux des jours passés (`JournalRepository`).
 * 4. Continuité d'écriture au passage de minuit (`23h55` ➔ `00h00`) avec `Clock` simulée (`EditorViewModel`).
 * 5. Cycle complet d'export `.zip` / réimport `.zip`, fusion sans perte (`\n\n---\n\n`) et protection *Zip Slip* (`MarkdownZipManager`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. Recherche insensible aux accents & exactitude des charOffset
    // =========================================================================

    @Test
    fun diacriticsMatcher_findsAccentedAndCapitalizedOccurrencesWithExactCharOffsets() {
        val text = "Cet été, j'ai adoré l'Été indien. Quel bel ete !"
        val occurrences = DiacriticsMatcher.findOccurrencesInEntry(
            isoDate = "2026-11-09",
            epochDay = LocalDate.of(2026, 11, 9).toEpochDay(),
            content = text,
            query = "ete",
        )

        assertEquals(3, occurrences.size)

        // 1re occurrence : "été"
        assertEquals(0, occurrences[0].occurrenceIndex)
        assertEquals(3, occurrences[0].totalOccurrencesInEntry)
        assertEquals("été", occurrences[0].matchText)
        assertEquals("été", text.substring(occurrences[0].charOffset, occurrences[0].charOffset + occurrences[0].matchLength))

        // 2e occurrence : "Été"
        assertEquals(1, occurrences[1].occurrenceIndex)
        assertEquals("Été", occurrences[1].matchText)
        assertEquals("Été", text.substring(occurrences[1].charOffset, occurrences[1].charOffset + occurrences[1].matchLength))

        // 3e occurrence : "ete"
        assertEquals(2, occurrences[2].occurrenceIndex)
        assertEquals("ete", occurrences[2].matchText)
        assertEquals("ete", text.substring(occurrences[2].charOffset, occurrences[2].charOffset + occurrences[2].matchLength))
    }

    @Test
    fun diacriticsMatcher_accentedQueryMatchesUnaccentedText() {
        val text = "Une belle journee d'automne et une autre journée calme."
        val ranges = DiacriticsMatcher.findMatchRanges(content = text, query = "Journée")

        assertEquals(2, ranges.size)
        assertEquals("journee", text.substring(ranges[0]))
        assertEquals("journée", text.substring(ranges[1]))
    }

    // =========================================================================
    // 2. Formatage de la date et de l'horodatage intra-journée
    // =========================================================================

    @Test
    fun journalDateFormatter_formatsAllStylesInFrenchAndTimestampMarker() {
        val date = LocalDate.of(2026, 11, 9) // Lundi 9 novembre 2026
        val fr = Locale.FRANCE

        assertEquals(
            "Lundi 9 novembre 2026",
            JournalDateFormatter.formatTitle(date, DateFormatStyle.FULL_DAY_FIRST, fr),
        )
        assertEquals(
            "9 novembre 2026 — Lundi",
            JournalDateFormatter.formatTitle(date, DateFormatStyle.DATE_FIRST_DASH_DAY, fr),
        )
        assertEquals(
            "2026-11-09 · Lundi",
            JournalDateFormatter.formatTitle(date, DateFormatStyle.ISO_WITH_DAY, fr),
        )
        assertEquals(
            "— 23:05 — ",
            JournalDateFormatter.formatTimestampMarker(LocalTime.of(23, 5)),
        )
    }

    // =========================================================================
    // 3. Garantie stricte de lecture seule sur les journaux antérieurs (ADR-002)
    // =========================================================================

    @Test(expected = IllegalArgumentException::class)
    fun journalRepository_rejectsModificationOfPastEntry() = runTest(testDispatcher) {
        val fakeDao = FakeJournalDao()
        val clock = MutableClock(Instant.parse("2026-11-09T14:00:00Z"), ZoneId.of("Europe/Paris"))
        val repository = JournalRepository(
            journalDao = fakeDao,
            clock = clock,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
        )

        val today = LocalDate.of(2026, 11, 9)
        val yesterday = LocalDate.of(2026, 11, 8)

        // Tenter d'écrire sur le journal de la veille doit lever une IllegalArgumentException
        repository.saveActiveSessionContent(
            activeSessionDate = today,
            targetDate = yesterday,
            content = "Tentative de modification d'une archive passée",
        )
    }

    @Test
    fun journalRepository_savesAndDeletesCurrentDayEntry() = runTest(testDispatcher) {
        val fakeDao = FakeJournalDao()
        val clock = MutableClock(Instant.parse("2026-11-09T14:00:00Z"), ZoneId.of("Europe/Paris"))
        val repository = JournalRepository(
            journalDao = fakeDao,
            clock = clock,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
        )

        val today = LocalDate.of(2026, 11, 9)
        repository.saveActiveSessionContent(
            activeSessionDate = today,
            targetDate = today,
            content = "Première note du jour.",
        )

        val saved = repository.getEntryByDate(today)
        assertNotNull(saved)
        assertEquals("Première note du jour.", saved?.content)

        // Si l'utilisateur efface tout le texte du jour, l'entrée vide est supprimée proprement
        repository.saveActiveSessionContent(
            activeSessionDate = today,
            targetDate = today,
            content = "   ",
        )
        assertNull(repository.getEntryByDate(today))
    }

    @Test
    fun journalRepository_searchOccurrencesFindsMatchesAcrossMultipleEntries() = runTest(testDispatcher) {
        val fakeDao = FakeJournalDao()
        val clock = MutableClock(Instant.parse("2026-11-09T14:00:00Z"), ZoneId.of("Europe/Paris"))
        val repository = JournalRepository(
            journalDao = fakeDao,
            clock = clock,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
        )

        val d1 = LocalDate.of(2026, 11, 8)
        val d2 = LocalDate.of(2026, 11, 9)
        fakeDao.upsertAll(
            listOf(
                JournalEntryEntity(d1.toString(), d1.toEpochDay(), "Un café en été au soleil.", 100L, 100L),
                JournalEntryEntity(d2.toString(), d2.toEpochDay(), "Souvenir d'Été et autre ete.", 200L, 200L),
            ),
        )

        val occurrences = repository.searchOccurrences("ete").first()
        assertEquals(3, occurrences.size)
        assertEquals("2026-11-09", occurrences[0].isoDate)
        assertEquals("Été", occurrences[0].matchText)
        assertEquals("2026-11-09", occurrences[1].isoDate)
        assertEquals("ete", occurrences[1].matchText)
        assertEquals("2026-11-08", occurrences[2].isoDate)
        assertEquals("été", occurrences[2].matchText)
    }

    // =========================================================================
    // 4. Continuité d'écriture au passage de minuit (23h55 ➔ 00h00)
    // =========================================================================

    @Test
    fun editorViewModel_preservesSessionAcrossMidnightWhileScreenIsActive_andSwitchesImmediatelyWhenReopened() = runTest(testDispatcher) {
        val zone = ZoneId.of("Europe/Paris")
        // 9 novembre 2026 à 23h55 (heure de Paris, UTC+1 -> 22:55 UTC)
        val clock = MutableClock(Instant.parse("2026-11-09T22:55:00Z"), zone)
        val fakeDao = FakeJournalDao()
        val repository = JournalRepository(
            journalDao = fakeDao,
            clock = clock,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
        )
        val prefsRepository = UserPreferencesRepository(FakePreferencesDataStore())

        val viewModel = EditorViewModel(
            journalRepository = repository,
            userPreferencesRepository = prefsRepository,
            clock = clock,
        )
        advanceUntilIdle()

        val initialDate = LocalDate.of(2026, 11, 9)
        assertEquals(initialDate, viewModel.uiState.value.sessionDate)

        // L'utilisateur commence à écrire à 23h55
        viewModel.onEvent(
            EditorUiEvent.OnTextChanged(
                TextFieldValue("Début d'écriture à 23h55.", TextRange(25)),
            ),
        )
        advanceTimeBy(EditorViewModel.AUTO_SAVE_DEBOUNCE_MS + 50L)
        advanceUntilIdle()

        // L'horloge passe à 00h05 (10 novembre 2026) pendant que l'écran reste actif (pas de ON_STOP)
        clock.advance(Duration.ofMinutes(10))
        assertEquals(LocalDate.of(2026, 11, 10), LocalDate.now(clock))

        viewModel.onEvent(
            EditorUiEvent.OnTextChanged(
                TextFieldValue("Début d'écriture à 23h55. Suite après minuit !", TextRange(46)),
            ),
        )
        advanceTimeBy(EditorViewModel.AUTO_SAVE_DEBOUNCE_MS + 50L)
        advanceUntilIdle()

        // Tant que l'écran reste actif, la session reste verrouillée sur le 9 novembre 2026 : aucune coupure !
        assertEquals(initialDate, viewModel.uiState.value.sessionDate)
        assertEquals(
            "Début d'écriture à 23h55. Suite après minuit !",
            repository.getEntryByDate(initialDate)?.content,
        )
        assertNull(repository.getEntryByDate(LocalDate.of(2026, 11, 10)))

        // Dès que l'utilisateur quitte l'application (ON_STOP) et revient (ON_RESUME), même 1 minute plus tard :
        viewModel.onEvent(EditorUiEvent.OnLifecycleStopped)
        advanceUntilIdle()
        clock.advance(Duration.ofMinutes(1))
        viewModel.onEvent(EditorUiEvent.OnLifecycleResumed)
        advanceUntilIdle()

        // L'application bascule immédiatement sur la nouvelle entrée du 10 novembre 2026
        assertEquals(LocalDate.of(2026, 11, 10), viewModel.uiState.value.sessionDate)
        assertEquals("", viewModel.uiState.value.textFieldValue.text)
    }

    @Test
    fun editorViewModel_ignoresStaleImeTextCallbackFromPreviousDay_andCleansAccidentalDuplicate() = runTest(testDispatcher) {
        val zone = ZoneId.of("Europe/Paris")
        // Lundi 9 novembre 2026 à 18h00 (17:00 UTC)
        val clock = MutableClock(Instant.parse("2026-11-09T17:00:00Z"), zone)
        val fakeDao = FakeJournalDao()
        val repository = JournalRepository(
            journalDao = fakeDao,
            clock = clock,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
        )
        val prefsRepository = UserPreferencesRepository(FakePreferencesDataStore())

        val viewModel = EditorViewModel(
            journalRepository = repository,
            userPreferencesRepository = prefsRepository,
            clock = clock,
        )
        advanceUntilIdle()

        val monday = LocalDate.of(2026, 11, 9)
        val tuesday = LocalDate.of(2026, 11, 10)
        val mondayText = "Journal écrit le lundi 9 novembre."

        viewModel.onEvent(
            EditorUiEvent.OnTextChanged(
                newValue = TextFieldValue(mondayText, TextRange(mondayText.length)),
                sessionDate = monday,
            ),
        )
        advanceTimeBy(EditorViewModel.AUTO_SAVE_DEBOUNCE_MS + 50L)
        advanceUntilIdle()

        // L'utilisateur quitte l'application le lundi soir, puis la rouvre à 00h30 (mardi 10 novembre)
        viewModel.onEvent(EditorUiEvent.OnLifecycleStopped)
        advanceUntilIdle()
        clock.advance(Duration.ofHours(6).plusMinutes(30)) // Mardi 10 novembre 2026 à 00h30
        assertEquals(tuesday, LocalDate.now(clock))

        viewModel.onEvent(EditorUiEvent.OnLifecycleResumed)
        advanceUntilIdle()

        // Le titre et la session ont basculé sur mardi 10 novembre avec un éditeur vierge
        assertEquals(tuesday, viewModel.uiState.value.sessionDate)
        assertEquals("", viewModel.uiState.value.textFieldValue.text)

        // Simulation du bug clavier Samsung (IME) qui renvoie un callback retardé avec le texte de lundi
        viewModel.onEvent(
            EditorUiEvent.OnTextChanged(
                newValue = TextFieldValue(mondayText, TextRange(mondayText.length)),
                sessionDate = monday,
            ),
        )
        advanceTimeBy(EditorViewModel.AUTO_SAVE_DEBOUNCE_MS + 50L)
        advanceUntilIdle()

        // Le callback obsolète de lundi est rejeté : le journal de mardi reste vide et celui de lundi est intact !
        assertEquals("", viewModel.uiState.value.textFieldValue.text)
        assertNull(repository.getEntryByDate(tuesday))
        assertEquals(mondayText, repository.getEntryByDate(monday)?.content)

        // Vérification de l'auto-réparation (self-healing) si une base existante (v1.0.1) avait déjà copié lundi dans mardi
        fakeDao.upsert(
            JournalEntryEntity(
                isoDate = tuesday.toString(),
                epochDay = tuesday.toEpochDay(),
                content = mondayText,
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )
        val healedTuesdayEntry = repository.loadActiveSessionEntry(tuesday)
        assertNull(healedTuesdayEntry)
        assertNull(repository.getEntryByDate(tuesday))
        assertEquals(mondayText, repository.getEntryByDate(monday)?.content)
    }

    @Test
    fun journalRepository_seedsAndClearsDemoPastEntriesWithoutTouchingRealEntries() = runTest(testDispatcher) {
        val fakeDao = FakeJournalDao()
        val clock = MutableClock(Instant.parse("2026-11-10T10:00:00Z"), ZoneId.of("Europe/Paris"))
        val repository = JournalRepository(
            journalDao = fakeDao,
            clock = clock,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
        )

        val today = LocalDate.of(2026, 11, 10)
        val yesterday = LocalDate.of(2026, 11, 9)
        // L'utilisateur a déjà écrit un vrai journal hier (9 novembre)
        fakeDao.upsert(
            JournalEntryEntity(
                isoDate = yesterday.toString(),
                epochDay = yesterday.toEpochDay(),
                content = "Mon vrai journal de lundi.",
                createdAt = 500L,
                updatedAt = 500L,
            ),
        )

        val seededCount = repository.seedDemoPastEntries(today)
        assertEquals(5, seededCount)

        // Les archives contiennent le vrai journal d'hier + les 5 journaux de démo (6 au total)
        val pastEntriesAfterSeed = repository.observePastEntries(today).first()
        assertEquals(6, pastEntriesAfterSeed.size)

        // Suppression des journaux de démonstration : le vrai journal d'hier reste intact !
        val clearedCount = repository.clearDemoPastEntries()
        assertEquals(5, clearedCount)

        val pastEntriesAfterClear = repository.observePastEntries(today).first()
        assertEquals(1, pastEntriesAfterClear.size)
        assertEquals("Mon vrai journal de lundi.", pastEntriesAfterClear.first().content)
    }

    // =========================================================================
    // 5. Cycle Export .zip -> Réimport .zip, Fusion sans perte & Protection Zip Slip
    // =========================================================================

    @Test
    fun markdownZipManager_exportsAndReimportsZipWithoutHeaderDuplication_andMergesLosslessly() = runTest(testDispatcher) {
        val manager = MarkdownZipManager(ioDispatcher = testDispatcher)
        val entriesToExport = listOf(
            JournalEntry(
                isoDate = "2026-11-08",
                epochDay = LocalDate.of(2026, 11, 8).toEpochDay(),
                content = "Journal du dimanche soir.\nDeuxième ligne.",
                createdAt = 1000L,
                updatedAt = 2000L,
            ),
            JournalEntry(
                isoDate = "2026-11-09",
                epochDay = LocalDate.of(2026, 11, 9).toEpochDay(),
                content = "Note originale sur l'ancien téléphone.",
                createdAt = 3000L,
                updatedAt = 4000L,
            ),
        )

        // 1. Export en mémoire vers un flux .zip
        val zipBytesOut = ByteArrayOutputStream()
        val exportedCount = manager.exportEntriesToZipStream(
            entries = entriesToExport,
            outputStream = zipBytesOut,
            dateFormatStyle = DateFormatStyle.FULL_DAY_FIRST,
        )
        assertEquals(2, exportedCount)

        // 2. Réimport depuis le flux .zip
        val reimportedEntries = manager.readEntriesFromZipStream(
            ByteArrayInputStream(zipBytesOut.toByteArray()),
        )
        assertEquals(2, reimportedEntries.size)
        // Vérifie que le titre "# Date" ajouté au fichier .md a bien été retiré lors du parsing
        assertEquals("Journal du dimanche soir.\nDeuxième ligne.", reimportedEntries[0].content)
        assertEquals("Note originale sur l'ancien téléphone.", reimportedEntries[1].content)

        // 3. Test de fusion intelligente sans perte dans JournalRepository
        val fakeDao = FakeJournalDao()
        val clock = MutableClock(Instant.parse("2026-11-09T18:00:00Z"), ZoneId.of("Europe/Paris"))
        val repository = JournalRepository(
            journalDao = fakeDao,
            clock = clock,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
        )

        // Supposons que sur le nouveau téléphone, le 2026-11-09 contient déjà un texte différent
        val dateConflict = LocalDate.of(2026, 11, 9)
        repository.saveActiveSessionContent(
            activeSessionDate = dateConflict,
            targetDate = dateConflict,
            content = "Note écrite sur le nouveau téléphone.",
        )

        val mergedCount = repository.importAndMergeEntries(reimportedEntries)
        assertEquals(2, mergedCount)

        // Le 2026-11-08 a été créé tel quel
        assertEquals(
            "Journal du dimanche soir.\nDeuxième ligne.",
            repository.getEntryByDate(LocalDate.of(2026, 11, 8))?.content,
        )

        // Le 2026-11-09 a fusionné les deux textes via "\n\n---\n\n" sans aucune perte
        assertEquals(
            "Note écrite sur le nouveau téléphone.\n\n---\n\nNote originale sur l'ancien téléphone.",
            repository.getEntryByDate(dateConflict)?.content,
        )
    }

    @Test
    fun markdownZipManager_rejectsZipSlipTraversalEntries() = runTest(testDispatcher) {
        assertFalse(MarkdownZipManager.isSafeZipEntryName("../evil.md"))
        assertFalse(MarkdownZipManager.isSafeZipEntryName("backup/../../evil.md"))
        assertFalse(MarkdownZipManager.isSafeZipEntryName("/etc/passwd.md"))
        assertFalse(MarkdownZipManager.isSafeZipEntryName("C:\\Windows\\evil.md"))
        assertTrue(MarkdownZipManager.isSafeZipEntryName("2026-11-09.md"))
        assertTrue(MarkdownZipManager.isSafeZipEntryName("journal/2026-11-09.md"))

        // Création d'un ZIP contenant une entrée malveillante "../2026-11-07.md" et une entrée saine "2026-11-09.md"
        val zipOutBytes = ByteArrayOutputStream()
        ZipOutputStream(zipOutBytes, Charsets.UTF_8).use { zipOut ->
            zipOut.putNextEntry(ZipEntry("../2026-11-07.md"))
            zipOut.write("# Malicious\n\nContenu malveillant".toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            zipOut.putNextEntry(ZipEntry("2026-11-09.md"))
            zipOut.write("# Lundi 9 novembre 2026\n\nContenu légitime".toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()
        }

        val manager = MarkdownZipManager(ioDispatcher = testDispatcher)
        val imported = manager.readEntriesFromZipStream(ByteArrayInputStream(zipOutBytes.toByteArray()))

        assertEquals(1, imported.size)
        assertEquals("2026-11-09", imported.first().isoDate)
        assertEquals("Contenu légitime", imported.first().content)
    }

    // =========================================================================
    // 6. Outils de mise en forme Markdown (Titres, Gras/Italique/Souligné, Listes UL/OL)
    // =========================================================================

    @Test
    fun markdownTextFormatter_togglesHeadingsAndInlineStyles_andDetectsActiveFormats() {
        // Bascule H1 -> H2 -> retrait H2 et détection d'état actif dans la toolbar
        val initial = TextFieldValue("Mon titre de section", TextRange(5))
        assertTrue(fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.detectActiveFormats(initial).isEmpty())

        val h1 = fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.applyFormat(
            initial,
            fr.antoinehory.monsimplejournal.domain.MarkdownFormatAction.HEADING_1,
        )
        assertEquals("# Mon titre de section", h1.text)
        assertTrue(
            fr.antoinehory.monsimplejournal.domain.MarkdownFormatAction.HEADING_1 in
                fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.detectActiveFormats(h1),
        )

        val h2 = fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.applyFormat(
            h1,
            fr.antoinehory.monsimplejournal.domain.MarkdownFormatAction.HEADING_2,
        )
        assertEquals("## Mon titre de section", h2.text)
        assertTrue(
            fr.antoinehory.monsimplejournal.domain.MarkdownFormatAction.HEADING_2 in
                fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.detectActiveFormats(h2),
        )

        val unheaded = fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.applyFormat(
            h2,
            fr.antoinehory.monsimplejournal.domain.MarkdownFormatAction.HEADING_2,
        )
        assertEquals("Mon titre de section", unheaded.text)
        assertTrue(fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.detectActiveFormats(unheaded).isEmpty())

        // Gras autour d'une sélection + bouton Gras actif
        val wordSelected = TextFieldValue("Un mot important ici", TextRange(3, 6)) // "mot"
        val bolded = fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.applyFormat(
            wordSelected,
            fr.antoinehory.monsimplejournal.domain.MarkdownFormatAction.BOLD,
        )
        assertEquals("Un **mot** important ici", bolded.text)
        assertEquals(TextRange(5, 8), bolded.selection)
        assertTrue(
            fr.antoinehory.monsimplejournal.domain.MarkdownFormatAction.BOLD in
                fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.detectActiveFormats(bolded),
        )

        // Un second clic sur Gras retire les balises **
        val unbolded = fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.applyFormat(
            bolded,
            fr.antoinehory.monsimplejournal.domain.MarkdownFormatAction.BOLD,
        )
        assertEquals("Un mot important ici", unbolded.text)

        // Clic sur Gras sans sélection (curseur simple) -> active immédiatement le bouton Gras
        val emptyCursor = TextFieldValue("Bonjour ", TextRange(8))
        val boldAtCursor = fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.applyFormat(
            emptyCursor,
            fr.antoinehory.monsimplejournal.domain.MarkdownFormatAction.BOLD,
        )
        assertTrue(
            fr.antoinehory.monsimplejournal.domain.MarkdownFormatAction.BOLD in
                fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.detectActiveFormats(boldAtCursor),
        )

        // Vérifie que formatForDisplay masque intégralement tous les symboles Markdown (#, **, *, __)
        val rawMarkdown = "# Grand titre\nUn texte **gras**, *italique* et __souligné__.\n- Puce un"
        val displayResult = fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.formatForDisplay(
            content = rawMarkdown,
            textColor = androidx.compose.ui.graphics.Color.White,
            accentColor = androidx.compose.ui.graphics.Color.Cyan,
        )
        assertEquals(
            "Grand titre\nUn texte gras, italique et souligné.\n• Puce un",
            displayResult.annotatedString.text,
        )
    }

    @Test
    fun markdownTextFormatter_handlesUnorderedAndOrderedListsWithAutoContinuation() {
        // Transformation en liste à puces UL
        val line = TextFieldValue("Acheter un carnet", TextRange(17))
        val ul = fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.applyFormat(
            line,
            fr.antoinehory.monsimplejournal.domain.MarkdownFormatAction.UNORDERED_LIST,
        )
        assertEquals("- Acheter un carnet", ul.text)

        // Appui sur Entrée à la fin de la puce -> continue automatiquement avec "- "
        val afterEnter = TextFieldValue("- Acheter un carnet\n", TextRange(20))
        val continuedUl = fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter
            .handleTextChangeWithListContinuation(previous = ul, current = afterEnter)
        assertEquals("- Acheter un carnet\n- ", continuedUl.text)
        assertEquals(22, continuedUl.selection.min)

        // Appui sur Entrée sur la puce vide -> quitte proprement la liste
        val afterSecondEnter = TextFieldValue("- Acheter un carnet\n- \n", TextRange(23))
        val exitedUl = fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter
            .handleTextChangeWithListContinuation(previous = continuedUl, current = afterSecondEnter)
        assertEquals("- Acheter un carnet\n\n", exitedUl.text)

        // Liste numérotée OL sur plusieurs lignes + continuation automatique à "3. "
        val multiLine = TextFieldValue("Premier\nDeuxième", TextRange(0, 16))
        val ol = fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter.applyFormat(
            multiLine,
            fr.antoinehory.monsimplejournal.domain.MarkdownFormatAction.ORDERED_LIST,
        )
        assertEquals("1. Premier\n2. Deuxième", ol.text)

        val olAtEnd = ol.copy(selection = TextRange(ol.text.length))
        val olAfterEnter = TextFieldValue("1. Premier\n2. Deuxième\n", TextRange(23))
        val continuedOl = fr.antoinehory.monsimplejournal.domain.MarkdownTextFormatter
            .handleTextChangeWithListContinuation(previous = olAtEnd, current = olAfterEnter)
        assertEquals("1. Premier\n2. Deuxième\n3. ", continuedOl.text)
    }
}

/**
 * Horloge mutable permettant de simuler le passage de `23h55` à `00h00` et le temps passé en arrière-plan.
 */
private class MutableClock(
    private var currentInstant: Instant,
    private val zoneId: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)
    override fun instant(): Instant = currentInstant

    fun advance(duration: Duration) {
        currentInstant = currentInstant.plus(duration)
    }
}

/**
 * Implémentation en mémoire de [JournalDao] pour les tests unitaires JVM rapides.
 */
private class FakeJournalDao : JournalDao {
    private val entriesFlow = MutableStateFlow<Map<String, JournalEntryEntity>>(emptyMap())

    override fun observeByDate(isoDate: String): Flow<JournalEntryEntity?> =
        entriesFlow.map { it[isoDate] }

    override suspend fun getByDate(isoDate: String): JournalEntryEntity? =
        entriesFlow.value[isoDate]

    override fun observeAllPastEntries(activeSessionIsoDate: String): Flow<List<JournalEntryEntity>> =
        entriesFlow.map { map ->
            map.values
                .filter { it.isoDate < activeSessionIsoDate && it.content.trim().isNotEmpty() }
                .sortedByDescending { it.epochDay }
        }

    override fun observeAllEntries(): Flow<List<JournalEntryEntity>> =
        entriesFlow.map { map ->
            map.values
                .filter { it.content.trim().isNotEmpty() }
                .sortedByDescending { it.epochDay }
        }

    override suspend fun getAllEntriesChronological(): List<JournalEntryEntity> =
        entriesFlow.value.values
            .filter { it.content.trim().isNotEmpty() }
            .sortedBy { it.epochDay }

    override suspend fun upsert(entity: JournalEntryEntity) {
        entriesFlow.value = entriesFlow.value.toMutableMap().apply {
            put(entity.isoDate, entity)
        }
    }

    override suspend fun upsertAll(entities: List<JournalEntryEntity>) {
        entriesFlow.value = entriesFlow.value.toMutableMap().apply {
            for (entity in entities) {
                put(entity.isoDate, entity)
            }
        }
    }

    override suspend fun deleteByDate(isoDate: String) {
        entriesFlow.value = entriesFlow.value.toMutableMap().apply {
            remove(isoDate)
        }
    }

    override suspend fun deleteByDates(isoDates: List<String>) {
        entriesFlow.value = entriesFlow.value.toMutableMap().apply {
            for (isoDate in isoDates) {
                remove(isoDate)
            }
        }
    }
}

/**
 * DataStore en mémoire pour tester [UserPreferencesRepository] et [EditorViewModel] sans fichier disque.
 */
private class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
