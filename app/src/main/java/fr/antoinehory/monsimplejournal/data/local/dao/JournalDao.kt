package fr.antoinehory.monsimplejournal.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import fr.antoinehory.monsimplejournal.data.local.entity.JournalEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Interface d'accès aux données Room pour les entrées du journal.
 */
@Dao
interface JournalDao {

    /** Observe l'entrée d'une date précise (`YYYY-MM-DD`). */
    @Query("SELECT * FROM journal_entries WHERE iso_date = :isoDate LIMIT 1")
    fun observeByDate(isoDate: String): Flow<JournalEntryEntity?>

    /** Récupère ponctuellement l'entrée d'une date précise (`YYYY-MM-DD`). */
    @Query("SELECT * FROM journal_entries WHERE iso_date = :isoDate LIMIT 1")
    suspend fun getByDate(isoDate: String): JournalEntryEntity?

    /** Observe tous les journaux des jours précédents (`iso_date < :activeSessionIsoDate`), du plus récent au plus ancien. */
    @Query(
        """
        SELECT * FROM journal_entries
        WHERE iso_date < :activeSessionIsoDate AND TRIM(content) != ''
        ORDER BY epoch_day DESC
        """,
    )
    fun observeAllPastEntries(activeSessionIsoDate: String): Flow<List<JournalEntryEntity>>

    /** Observe l'intégralité des journaux non vides, triés du plus récent au plus ancien (pour la recherche et l'export). */
    @Query(
        """
        SELECT * FROM journal_entries
        WHERE TRIM(content) != ''
        ORDER BY epoch_day DESC
        """,
    )
    fun observeAllEntries(): Flow<List<JournalEntryEntity>>

    /** Récupère la liste complète de tous les journaux non vides pour l'export `.md` / `.zip`. */
    @Query(
        """
        SELECT * FROM journal_entries
        WHERE TRIM(content) != ''
        ORDER BY epoch_day ASC
        """,
    )
    suspend fun getAllEntriesChronological(): List<JournalEntryEntity>

    /** Insère ou met à jour une entrée de journal. */
    @Upsert
    suspend fun upsert(entity: JournalEntryEntity)

    /** Insère ou met à jour un lot d'entrées de journal (lors d'un import `.zip` / `.md`). */
    @Upsert
    suspend fun upsertAll(entities: List<JournalEntryEntity>)

    /** Supprime une entrée par sa date ISO (ex. lorsque le journal du jour est intégralement vidé). */
    @Query("DELETE FROM journal_entries WHERE iso_date = :isoDate")
    suspend fun deleteByDate(isoDate: String)

    /** Supprime un lot d'entrées par leurs dates ISO (ex. nettoyage des entrées de démonstration). */
    @Query("DELETE FROM journal_entries WHERE iso_date IN (:isoDates)")
    suspend fun deleteByDates(isoDates: List<String>)
}
