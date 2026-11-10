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

    /**
     * Génère 5 anciens journaux de démonstration (Lorem Ipsum + exemples de mise en forme et d'accents)
     * sur des dates antérieures pour tester l'historique (Archives), la lecture seule et la recherche.
     * Ne remplace jamais un journal réel déjà écrit par l'utilisateur.
     *
     * @return Le nombre de journaux de démonstration insérés.
     */
    suspend fun seedDemoPastEntries(referenceDate: LocalDate = LocalDate.now(clock)): Int = withContext(ioDispatcher) {
        val nowMillis = clock.millis()
        val demoSamples = buildDemoSamples(referenceDate)
        val entitiesToInsert = mutableListOf<JournalEntryEntity>()

        for ((date, content) in demoSamples) {
            val isoDate = date.toString()
            val existing = journalDao.getByDate(isoDate)
            if (existing == null || existing.content.isBlank() || existing.content.contains(DEMO_MARKER)) {
                entitiesToInsert.add(
                    JournalEntryEntity(
                        isoDate = isoDate,
                        epochDay = date.toEpochDay(),
                        content = content,
                        createdAt = nowMillis,
                        updatedAt = nowMillis,
                    ),
                )
            }
        }

        if (entitiesToInsert.isNotEmpty()) {
            journalDao.upsertAll(entitiesToInsert)
        }
        entitiesToInsert.size
    }

    /**
     * Supprime uniquement les journaux de démonstration générés via [seedDemoPastEntries],
     * sans toucher aux vrais journaux de l'utilisateur.
     */
    suspend fun clearDemoPastEntries(): Int = withContext(ioDispatcher) {
        val allEntries = journalDao.getAllEntriesChronological()
        val demoDates = allEntries
            .filter { it.content.contains(DEMO_MARKER) }
            .map { it.isoDate }
        if (demoDates.isNotEmpty()) {
            journalDao.deleteByDates(demoDates)
        }
        demoDates.size
    }

    companion object {
        const val DEMO_MARKER = "[DEMO_LOREM_IPSUM]"

        private fun buildDemoSamples(today: LocalDate): List<Pair<LocalDate, String>> = listOf(
            today.minusDays(2) to """
                # Pensées d'automne $DEMO_MARKER

                — 09:15 — Lorem ipsum dolor sit amet, **consectetur adipiscing elit**. Sed non risus. Suspendisse lectus tortor, dignissim sit amet, *adipiscing nec*, ultricies sed, dolor.

                ## Ce que j'ai retenu aujourd'hui
                - Profiter du calme de la matinée et d'un bon café
                - Relire mes notes d'Été sans distraction
                - Tester la __mise en forme__ dans l'historique

                — 21:40 — Cras elementum ultrices diam. Maecenas ligula massa, varius a, semper congue, euismod non, mi.
            """.trimIndent(),
            today.minusDays(4) to """
                # Soirée tranquille $DEMO_MARKER

                — 18:30 — Proin porttitor, orci nec nonummy molestie, enim est eleifend mi, non fermentum diam nisl sit amet erat. **Duis semper.**

                1. Première étape validée
                2. Deuxième réflexion sur le projet
                3. Troisième idée pour cet été

                Ut velit mauris, *egestas sed*, gravida nec, ornare ut, mi. Aenean ut orci vel massa suscipit pulvinar.
            """.trimIndent(),
            today.minusDays(7) to """
                # Souvenirs de voyage $DEMO_MARKER

                — 14:10 — Nulla sollicitudin. Fusce varius, ligula non tempus aliquam, nunc turpis ullamcorper nibh, in tempus sapien eros vitae ligula.

                ### Notes de lecture
                Pellentesque rhoncus nunc et augue. Integer id felis. Curabitur aliquet pellentesque diam. Integer quis metus vitae elit lobortis egestas. Lorem ipsum dolor sit amet, **consectetur** adipiscing elit.
            """.trimIndent(),
            today.minusDays(12) to """
                # Dimanche pluvieux $DEMO_MARKER

                — 11:05 — Morbi vel erat non mauris convallis vehicula. Nulla et sapien. Integer tortor tellus, aliquam faucibus, **convallis id**, congue eu, quam. Mauris ullamcorper felis vitae erat.

                - Lecture au coin du feu
                - Rangement du bureau
                - Préparation de la semaine
            """.trimIndent(),
            today.minusDays(20) to """
                # Première page d'archive $DEMO_MARKER

                — 22:50 — Aliquam convallis sollicitudin purus. Praesent aliquam, enim at fermentum mollis, ligula massa adipiscing nisl, ac *euismod nibh* nisl eu lectus.

                Fusce vulputate sem at sapien. Vivamus leo. Aliquam euismod libero eu enim. Nulla nec felis sed leo placerat imperdiet. Aenean suscipit nulla in justo.
            """.trimIndent(),
        )
    }
}
