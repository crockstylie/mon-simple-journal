package fr.antoinehory.monsimplejournal.data.backup

import android.content.ContentResolver
import android.net.Uri
import fr.antoinehory.monsimplejournal.domain.DateFormatStyle
import fr.antoinehory.monsimplejournal.domain.JournalDateFormatter
import fr.antoinehory.monsimplejournal.domain.JournalEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Résultat d'une opération d'export ou d'import `.md` / `.zip`.
 */
sealed interface BackupResult {
    data class ExportSuccess(val entryCount: Int) : BackupResult
    data class ImportSuccess(val mergedCount: Int) : BackupResult
    data class Error(val reason: String) : BackupResult
}

/**
 * Gestionnaire de sauvegarde et de migration au format universel **Markdown (`.md`)**
 * et **Archive (`.zip`)** via le Storage Access Framework (zéro permission système).
 */
class MarkdownZipManager(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Exporte une liste de [JournalEntry] dans un flux `.zip` où chaque journal est un fichier `YYYY-MM-DD.md`.
     */
    suspend fun exportEntriesToZipStream(
        entries: List<JournalEntry>,
        outputStream: OutputStream,
        dateFormatStyle: DateFormatStyle = DateFormatStyle.FULL_DAY_FIRST,
    ): Int = withContext(ioDispatcher) {
        var count = 0
        ZipOutputStream(BufferedOutputStream(outputStream), Charsets.UTF_8).use { zipOut ->
            for (entry in entries) {
                if (entry.content.isBlank()) continue
                val fileName = "${entry.isoDate}.md"
                val zipEntry = ZipEntry(fileName).apply {
                    time = entry.updatedAt
                }
                zipOut.putNextEntry(zipEntry)
                val formattedMarkdown = formatEntryAsMarkdown(entry, dateFormatStyle)
                zipOut.write(formattedMarkdown.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
                count++
            }
        }
        count
    }

    /**
     * Exporte les journaux vers une [Uri] fournie par le Storage Access Framework.
     */
    suspend fun exportEntriesToZipUri(
        contentResolver: ContentResolver,
        uri: Uri,
        entries: List<JournalEntry>,
        dateFormatStyle: DateFormatStyle = DateFormatStyle.FULL_DAY_FIRST,
    ): BackupResult = withContext(ioDispatcher) {
        runCatching {
            val out = contentResolver.openOutputStream(uri)
                ?: return@withContext BackupResult.Error("Impossible d'ouvrir le fichier de destination.")
            out.use { stream ->
                val count = exportEntriesToZipStream(entries, stream, dateFormatStyle)
                BackupResult.ExportSuccess(count)
            }
        }.getOrElse { e ->
            BackupResult.Error(e.message ?: "Erreur d'export .zip")
        }
    }

    /**
     * Lit une archive `.zip` depuis un [InputStream] en appliquant une protection stricte contre *Zip Slip*,
     * et extrait toutes les entrées `.md` valides.
     */
    suspend fun readEntriesFromZipStream(inputStream: InputStream): List<JournalEntry> = withContext(ioDispatcher) {
        val parsedEntries = mutableListOf<JournalEntry>()

        ZipInputStream(BufferedInputStream(inputStream), Charsets.UTF_8).use { zipIn ->
            var zipEntry: ZipEntry? = zipIn.nextEntry
            while (zipEntry != null) {
                val entryName = zipEntry.name
                if (!zipEntry.isDirectory && isSafeZipEntryName(entryName) && entryName.endsWith(".md", ignoreCase = true)) {
                    val rawMarkdown = zipIn.readBytes().toString(Charsets.UTF_8)
                    val entryTime = zipEntry.time.takeIf { it > 0 } ?: System.currentTimeMillis()
                    parseMarkdownToJournalEntry(
                        fileName = entryName.substringAfterLast('/'),
                        rawMarkdown = rawMarkdown,
                        fallbackTimestamp = entryTime,
                    )?.let { parsedEntries.add(it) }
                }
                zipIn.closeEntry()
                zipEntry = zipIn.nextEntry
            }
        }

        parsedEntries
    }

    /**
     * Lit soit une archive `.zip`, soit un fichier `.md` individuel depuis une [Uri] SAF.
     */
    suspend fun readEntriesFromUri(
        contentResolver: ContentResolver,
        uri: Uri,
        fallbackFileName: String? = null,
    ): List<JournalEntry> = withContext(ioDispatcher) {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext emptyList()

        // Détection de la signature magique ZIP (PK\x03\x04)
        val isZip = bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()

        if (isZip) {
            readEntriesFromZipStream(ByteArrayInputStream(bytes))
        } else {
            val rawText = bytes.toString(Charsets.UTF_8)
            val entry = parseMarkdownToJournalEntry(
                fileName = fallbackFileName ?: uri.lastPathSegment.orEmpty(),
                rawMarkdown = rawText,
                fallbackTimestamp = System.currentTimeMillis(),
            )
            listOfNotNull(entry)
        }
    }

    companion object {
        private val ISO_DATE_REGEX = Regex("""(\d{4}-\d{2}-\d{2})""")

        /**
         * Protection contre la vulnérabilité *Zip Slip* : rejette toute entrée contenant `..` ou des chemins absolus.
         */
        fun isSafeZipEntryName(entryName: String): Boolean {
            if (entryName.isBlank()) return false
            if (entryName.contains("..")) return false
            if (entryName.startsWith("/") || entryName.startsWith("\\")) return false
            if (entryName.contains("\\")) return false
            return true
        }

        /**
         * Formate une [JournalEntry] en document Markdown standard avec `# Titre Date` en première ligne.
         */
        fun formatEntryAsMarkdown(
            entry: JournalEntry,
            dateFormatStyle: DateFormatStyle = DateFormatStyle.FULL_DAY_FIRST,
        ): String {
            val title = JournalDateFormatter.formatTitle(entry.localDate, dateFormatStyle)
            val cleanBody = entry.content.trimEnd()
            return "# $title\n\n$cleanBody\n"
        }

        /**
         * Analyse un fichier `.md` pour reconstruire une [JournalEntry].
         * Supprime l'en-tête `# Titre Date` initial s'il est présent afin d'éviter toute duplication.
         */
        fun parseMarkdownToJournalEntry(
            fileName: String,
            rawMarkdown: String,
            fallbackTimestamp: Long = System.currentTimeMillis(),
        ): JournalEntry? {
            val dateFromFileName = ISO_DATE_REGEX.find(fileName)?.groupValues?.get(1)
            val dateFromHeader = rawMarkdown.lineSequence().firstOrNull()
                ?.let { ISO_DATE_REGEX.find(it)?.groupValues?.get(1) }

            val isoCandidate = dateFromFileName ?: dateFromHeader
            val localDate = isoCandidate
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now()

            val body = stripLeadingMarkdownTitle(rawMarkdown).trim()
            if (body.isEmpty()) return null

            return JournalEntry(
                isoDate = localDate.toString(),
                epochDay = localDate.toEpochDay(),
                content = body,
                createdAt = fallbackTimestamp,
                updatedAt = fallbackTimestamp,
            )
        }

        private fun stripLeadingMarkdownTitle(rawMarkdown: String): String {
            val normalized = rawMarkdown.replace("\r\n", "\n")
            if (!normalized.startsWith("# ")) return normalized
            val firstNewline = normalized.indexOf('\n')
            if (firstNewline < 0) return ""
            return normalized.substring(firstNewline + 1).trimStart('\n')
        }
    }
}
