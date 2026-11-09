package fr.antoinehory.monsimplejournal.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import fr.antoinehory.monsimplejournal.data.local.dao.JournalDao
import fr.antoinehory.monsimplejournal.data.local.entity.JournalEntryEntity

/**
 * Base de données locale Room pour **Mon Simple Journal**.
 */
@Database(
    entities = [JournalEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun journalDao(): JournalDao

    companion object {
        private const val DATABASE_NAME = "mon_simple_journal.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { instance = it }
            }
        }
    }
}
