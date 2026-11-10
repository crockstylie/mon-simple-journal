package fr.antoinehory.monsimplejournal.data.repository

import fr.antoinehory.monsimplejournal.data.local.dao.JournalDao
import fr.antoinehory.monsimplejournal.data.local.entity.JournalEntryEntity
import fr.antoinehory.monsimplejournal.data.local.entity.toDomain
import fr.antoinehory.monsimplejournal.domain.DiacriticsMatcher
import fr.antoinehory.monsimplejournal.domain.JournalEntry
import fr.antoinehory.monsimplejournal.domain.SearchOccurrence
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate

/**
 * Repository principal orchestrant l'accès aux entrées du journal, la protection d'immuabilité
 * des journaux passés, la recherche d'occurrences insensible aux accents et la fusion sans perte.
 */
class JournalRepository(
    private val journalDao: JournalDao,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /** Observe l'entrée correspondant à une [date] précise. */
    fun observeEntryByDate(date: LocalDate): Flow<JournalEntry?> {
        return journalDao.observeByDate(date.toString())
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)
    }

    /** Récupère ponctuellement l'entrée correspondant à une [date] précise. */
    suspend fun getEntryByDate(date: LocalDate): JournalEntry? = withContext(ioDispatcher) {
        journalDao.getByDate(date.toString())?.toDomain()
    }

    /**
     * Charge l'entrée de la session d'écriture active pour [sessionDate].
     *
     * Si une entrée non vide pour [sessionDate] possède un contenu strictement identique
     * à celui de la veille (`sessionDate.minusDays(1)`) suite à une bascule nocturne antérieure,
     * la copie dupliquée du jour est automatiquement nettoyée tout en préservant l'archive de la veille.
     */
    suspend fun loadActiveSessionEntry(sessionDate: LocalDate): JournalEntry? = withContext(ioDispatcher) {
        val currentEntity = journalDao.getByDate(sessionDate.toString()) ?: return@withContext null
        if (currentEntity.content.isNotBlank()) {
            val previousEntity = journalDao.getByDate(sessionDate.minusDays(1).toString())
            if (previousEntity != null && previousEntity.content == currentEntity.content) {
                journalDao.deleteByDate(sessionDate.toString())
                return@withContext null
            }
        }
        currentEntity.toDomain()
    }

    /**
     * Observe la liste des journaux passés (`isoDate < activeSessionDate`),
     * triés par ordre antéchronologique.
     */
    fun observePastEntries(activeSessionDate: LocalDate): Flow<List<JournalEntry>> {
        return journalDao.observeAllPastEntries(activeSessionDate.toString())
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    /**
     * Récupère tous les journaux non vides par ordre chronologique croissant pour l'export `.md` / `.zip`.
     */
    suspend fun getAllEntriesForExport(): List<JournalEntry> = withContext(ioDispatcher) {
        journalDao.getAllEntriesChronological().map { it.toDomain() }
    }

    /**
     * Sauvegarde le contenu de la session d'écriture active.
     *
     * **Garde-fou d'immuabilité (ADR-002)** : Refuse toute modification si [targetDate] ne correspond
     * pas à [activeSessionDate].
     */
    suspend fun saveActiveSessionContent(
        activeSessionDate: LocalDate,
        targetDate: LocalDate,
        content: String,
    ) = withContext(ioDispatcher) {
        require(targetDate == activeSessionDate) {
            "Modification refusée : les journaux des jours passés ($targetDate) sont strictement en lecture seule."
        }

        val isoDate = activeSessionDate.toString()
        if (content.isBlank()) {
            journalDao.deleteByDate(isoDate)
            return@withContext
        }

        val nowMillis = clock.millis()
        val existing = journalDao.getByDate(isoDate)
        val entity = JournalEntryEntity(
            isoDate = isoDate,
            epochDay = activeSessionDate.toEpochDay(),
            content = content,
            createdAt = existing?.createdAt ?: nowMillis,
            updatedAt = nowMillis,
        )
        journalDao.upsert(entity)
    }

    /**
     * Recherche toutes les occurrences de [query] (insensible à la casse et aux accents)
     * à travers l'ensemble des journaux.
     */
    fun searchOccurrences(query: String): Flow<List<SearchOccurrence>> {
        val trimmedQuery = query.trim()
        return journalDao.observeAllEntries()
            .map { entities ->
                if (trimmedQuery.isEmpty()) {
                    emptyList()
                } else {
                    entities.flatMap { entity ->
                        DiacriticsMatcher.findOccurrencesInEntry(
                            isoDate = entity.isoDate,
                            epochDay = entity.epochDay,
                            content = entity.content,
                            query = trimmedQuery,
                        )
                    }
                }
            }
            .flowOn(defaultDispatcher)
    }

    /**
     * Importe une liste de journaux issus d'une sauvegarde `.zip` ou `.md` avec **fusion intelligente sans perte**.
     *
     * Si une date existe déjà en base avec un contenu non vide différent de celui importé,
     * les deux textes sont concaténés via `\n\n---\n\n`.
     *
     * @return Le nombre d'entrées importées ou fusionnées.
     */
    suspend fun importAndMergeEntries(importedEntries: List<JournalEntry>): Int = withContext(ioDispatcher) {
        if (importedEntries.isEmpty()) return@withContext 0

        val nowMillis = clock.millis()
        val entitiesToUpsert = mutableListOf<JournalEntryEntity>()

        for (imported in importedEntries) {
            val importedTrimmed = imported.content.trim()
            if (importedTrimmed.isEmpty()) continue

            val existing = journalDao.getByDate(imported.isoDate)
            val mergedContent = if (existing == null || existing.content.isBlank()) {
                imported.content
            } else {
                val existingTrimmed = existing.content.trim()
                when {
                    existingTrimmed == importedTrimmed -> existing.content
                    existingTrimmed.contains(importedTrimmed) -> existing.content
                    importedTrimmed.contains(existingTrimmed) -> imported.content
                    else -> "${existing.content.trimEnd()}\n\n---\n\n$importedTrimmed"
                }
            }

            entitiesToUpsert.add(
                JournalEntryEntity(
                    isoDate = imported.isoDate,
                    epochDay = imported.epochDay,
                    content = mergedContent,
                    createdAt = existing?.createdAt ?: imported.createdAt.takeIf { it > 0 } ?: nowMillis,
                    updatedAt = nowMillis,
                ),
            )
        }

        if (entitiesToUpsert.isNotEmpty()) {
            journalDao.upsertAll(entitiesToUpsert)
        }
        entitiesToUpsert.size
    }
}
