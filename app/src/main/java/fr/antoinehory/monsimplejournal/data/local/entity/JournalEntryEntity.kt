package fr.antoinehory.monsimplejournal.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import fr.antoinehory.monsimplejournal.domain.JournalEntry

/**
 * Entité Room représentant un journal quotidien stocké dans SQLite (`journal_entries`).
 */
@Entity(
    tableName = "journal_entries",
    indices = [Index(value = ["epoch_day"], unique = true)],
)
data class JournalEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "iso_date")
    val isoDate: String,

    @ColumnInfo(name = "epoch_day")
    val epochDay: Long,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

/** Convertit une entité Room [JournalEntryEntity] en modèle métier [JournalEntry]. */
fun JournalEntryEntity.toDomain(): JournalEntry = JournalEntry(
    isoDate = isoDate,
    epochDay = epochDay,
    content = content,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Convertit un modèle métier [JournalEntry] en entité Room [JournalEntryEntity]. */
fun JournalEntry.toEntity(): JournalEntryEntity = JournalEntryEntity(
    isoDate = isoDate,
    epochDay = epochDay,
    content = content,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
