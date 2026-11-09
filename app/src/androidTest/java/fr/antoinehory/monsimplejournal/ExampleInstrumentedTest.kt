package fr.antoinehory.monsimplejournal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.antoinehory.monsimplejournal.data.local.AppDatabase
import fr.antoinehory.monsimplejournal.data.local.dao.JournalDao
import fr.antoinehory.monsimplejournal.data.local.entity.JournalEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Tests instrumentés de [JournalDao] et [AppDatabase] en mémoire (Phase 7).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    private lateinit var database: AppDatabase
    private lateinit var journalDao: JournalDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        journalDao = database.journalDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun upsertAndObserveByDate_returnsSavedEntry() = runBlocking {
        val date = LocalDate.of(2026, 11, 9)
        val entity = JournalEntryEntity(
            isoDate = date.toString(),
            epochDay = date.toEpochDay(),
            content = "Aujourd'hui, j'ai testé la base Room de Mon Simple Journal.",
            createdAt = 1000L,
            updatedAt = 2000L,
        )

        journalDao.upsert(entity)

        val retrieved = journalDao.observeByDate("2026-11-09").first()
        assertNotNull(retrieved)
        assertEquals("Aujourd'hui, j'ai testé la base Room de Mon Simple Journal.", retrieved?.content)
        assertEquals(date.toEpochDay(), retrieved?.epochDay)
    }

    @Test
    fun observeAllPastEntries_excludesActiveSessionDateAndBlankEntries_andSortsDescending() = runBlocking {
        val d1 = LocalDate.of(2026, 11, 7)
        val d2 = LocalDate.of(2026, 11, 8)
        val today = LocalDate.of(2026, 11, 9)

        journalDao.upsertAll(
            listOf(
                JournalEntryEntity(d1.toString(), d1.toEpochDay(), "Journal du 7 novembre", 100L, 100L),
                JournalEntryEntity(d2.toString(), d2.toEpochDay(), "Journal du 8 novembre", 200L, 200L),
                JournalEntryEntity(today.toString(), today.toEpochDay(), "Journal actif du 9 novembre", 300L, 300L),
            ),
        )

        val pastEntries = journalDao.observeAllPastEntries(activeSessionIsoDate = today.toString()).first()
        assertEquals(2, pastEntries.size)
        // Tri antéchronologique : 8 novembre d'abord, puis 7 novembre
        assertEquals("2026-11-08", pastEntries[0].isoDate)
        assertEquals("2026-11-07", pastEntries[1].isoDate)

        // Export chronologique : du plus ancien au plus récent
        val chronological = journalDao.getAllEntriesChronological()
        assertEquals(3, chronological.size)
        assertEquals("2026-11-07", chronological[0].isoDate)
        assertEquals("2026-11-08", chronological[1].isoDate)
        assertEquals("2026-11-09", chronological[2].isoDate)
    }

    @Test
    fun deleteByDate_removesEntry() = runBlocking {
        val date = LocalDate.of(2026, 11, 9)
        journalDao.upsert(
            JournalEntryEntity(date.toString(), date.toEpochDay(), "Texte temporaire", 100L, 100L),
        )
        assertNotNull(journalDao.getByDate("2026-11-09"))

        journalDao.deleteByDate("2026-11-09")
        assertNull(journalDao.getByDate("2026-11-09"))
    }
}
