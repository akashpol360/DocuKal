package com.docukal.app.utils

import android.content.Context
import android.net.Uri
import com.docukal.app.data.local.CategoryEntity
import com.docukal.app.data.local.DocumentEntity
import com.docukal.app.data.local.ImportantDateEntity
import com.docukal.app.data.local.SettingsEntity
import com.docukal.app.data.local.WarrantyEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupData(
    val documents: List<DocumentEntity>,
    val categories: List<CategoryEntity>,
    val warranties: List<WarrantyEntity>,
    val dates: List<ImportantDateEntity>,
    val settings: SettingsEntity?,
    /** Attached files that were found in the backup and copied into the app. */
    val attachmentsRestored: Int = 0,
    /** Documents that had an attachment but whose file is not part of the backup (older backups). */
    val attachmentsNotIncluded: Int = 0,
    /** URIs (file://) of the attachment copies made while reading the backup. */
    val restoredUris: Set<String> = emptySet(),
    /** Items left out because they already exist (see [BackupUtils.dropDuplicates]). */
    val duplicatesSkipped: Int = 0
)

data class ExportSummary(
    val documents: Int,
    val warranties: Int,
    val dates: Int,
    val attachmentsIncluded: Int,
    val attachmentsMissing: Int
)

/**
 * Local-only backup/restore: writes a ZIP (data.json plus the attached files) via Android's
 * Storage Access Framework. Nothing here ever touches the network.
 */
object BackupUtils {

    /**
     * Writes the backup ZIP. Besides data.json it now also stores every attached file
     * under files/, so documents keep their image / PDF after a restore (a saved URI
     * alone stops working once the app is reinstalled or its data is cleared).
     * Does file I/O: call from a background thread. Throws on failure.
     */
    fun export(
        context: Context,
        uri: Uri,
        docs: List<DocumentEntity>,
        cats: List<CategoryEntity>,
        warranties: List<WarrantyEntity>,
        dates: List<ImportantDateEntity>,
        settings: SettingsEntity?
    ): ExportSummary {
        val resolver = context.contentResolver
        val outputStream = resolver.openOutputStream(uri)
            ?: throw IOException("Could not open the selected location for writing.")
        var included = 0
        var missing = 0

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
            val docsJson = JSONArray()
            docs.forEachIndexed { index, doc ->
                val json = doc.toJson()
                val attachment = doc.attachmentUri
                if (attachment != null) {
                    val source = Uri.parse(attachment)
                    // Only "cannot open" counts as a missing file; real I/O errors abort the export.
                    val stream = try {
                        resolver.openInputStream(source)
                    } catch (e: Exception) {
                        null
                    }
                    if (stream == null) {
                        missing++
                    } else {
                        val originalName = AttachmentUtils.displayName(context, source)
                            ?: source.lastPathSegment
                            ?: "attachment"
                        val entryName = "files/${index}_${safeName(originalName)}"
                        stream.use { input ->
                            zip.putNextEntry(ZipEntry(entryName))
                            input.copyTo(zip)
                            zip.closeEntry()
                        }
                        json.put("attachmentFile", entryName).put("attachmentName", originalName)
                        included++
                    }
                }
                docsJson.put(json)
            }

            val root = JSONObject()
                .put("version", 3)
                .put("documents", docsJson)
                .put("categories", JSONArray(cats.map { it.toJson() }))
                .put("warranties", JSONArray(warranties.map { it.toJson() }))
                .put("dates", JSONArray(dates.map { it.toJson() }))
            settings?.let { root.put("settings", it.toJson()) }

            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(root.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return ExportSummary(docs.size, warranties.size, dates.size, included, missing)
    }

    /**
     * Reads a backup. Attached files found inside it are copied into the app's private
     * storage and the restored documents point at those copies, so previews work right
     * after an import. Backups made by older versions (no files/ entries) still import.
     * Does file I/O: call from a background thread. Returns null if the file is not a
     * valid backup.
     */
    fun read(context: Context, uri: Uri): BackupData? {
        val restoredFiles = mutableMapOf<String, String>() // zip entry name -> file:// URI
        val createdFiles = mutableListOf<File>()
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            var text: String? = null
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == "data.json") {
                        text = zip.readBytes().toString(Charsets.UTF_8)
                    } else if (name.startsWith("files/") && !entry.isDirectory) {
                        val file = newAttachmentFile(context, name.substringAfterLast('/'))
                        createdFiles += file
                        file.outputStream().use { out -> zip.copyTo(out) }
                        restoredFiles[name] = Uri.fromFile(file).toString()
                    }
                    entry = zip.nextEntry
                }
            }
            val root = JSONObject(text ?: throw IOException("data.json missing"))

            val docs = root.optJSONArray("documents")?.toObjectList { it.toDocument(restoredFiles) } ?: emptyList()
            val cats = root.optJSONArray("categories")?.toObjectList { it.toCategory() } ?: emptyList()
            val warranties = root.optJSONArray("warranties")?.toObjectList { it.toWarranty() } ?: emptyList()
            val dates = root.optJSONArray("dates")?.toObjectList { it.toImportantDate() } ?: emptyList()
            val settings = root.optJSONObject("settings")?.toSettings()

            val restored = docs.count { d -> d.attachmentUri != null && restoredFiles.containsValue(d.attachmentUri) }
            val notIncluded = docs.count { it.attachmentUri != null } - restored
            BackupData(docs, cats, warranties, dates, settings, restored, notIncluded, restoredFiles.values.toSet())
        } catch (_: Exception) {
            // Invalid backup: remove any files copied so far so nothing is left behind.
            createdFiles.forEach { runCatching { it.parentFile?.deleteRecursively() } }
            null
        }
    }

    /**
     * Removes everything that already exists in the app (or appears twice inside the
     * backup) so imports keep every document, warranty and date unique. Attachment
     * copies belonging to skipped documents are deleted again. Does file I/O: call from
     * a background thread.
     */
    fun dropDuplicates(
        context: Context,
        data: BackupData,
        existingDocs: List<DocumentEntity>,
        existingWarranties: List<WarrantyEntity>,
        existingDates: List<ImportantDateEntity>
    ): BackupData {
        val cache = mutableMapOf<String, String?>()
        val acceptedDocs = mutableListOf<DocumentEntity>()
        var skipped = 0
        for (doc in data.documents) {
            val duplicate = DuplicateChecker.findDocumentDuplicate(context, doc, existingDocs + acceptedDocs, null, cache)
            if (duplicate != null) {
                skipped++
                if (doc.attachmentUri in data.restoredUris) deleteRestoredCopy(doc.attachmentUri)
            } else {
                acceptedDocs += doc
            }
        }

        val seenWarranties = existingWarranties.map { DuplicateChecker.warrantyKey(it) }.toMutableSet()
        val warranties = data.warranties.filter { seenWarranties.add(DuplicateChecker.warrantyKey(it)) }
        skipped += data.warranties.size - warranties.size

        val seenDates = existingDates.map { DuplicateChecker.dateKey(it) }.toMutableSet()
        val dates = data.dates.filter { seenDates.add(DuplicateChecker.dateKey(it)) }
        skipped += data.dates.size - dates.size

        val restored = acceptedDocs.count { it.attachmentUri in data.restoredUris }
        val notIncluded = acceptedDocs.count { it.attachmentUri != null } - restored
        return data.copy(
            documents = acceptedDocs,
            warranties = warranties,
            dates = dates,
            attachmentsRestored = restored,
            attachmentsNotIncluded = notIncluded,
            duplicatesSkipped = skipped
        )
    }

    private fun deleteRestoredCopy(uriText: String?) {
        val path = uriText?.let { Uri.parse(it).path } ?: return
        runCatching { File(path).parentFile?.deleteRecursively() }
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.').take(100)

    /** A new, private file for a restored attachment (the original file name is kept). */
    private fun newAttachmentFile(context: Context, entryFileName: String): File {
        val original = entryFileName.substringAfter('_', entryFileName)
        val dir = File(context.filesDir, "attachments/${UUID.randomUUID()}")
        dir.mkdirs()
        return File(dir, safeName(original).ifBlank { "attachment" })
    }

    fun validate(context: Context, uri: Uri): Boolean = try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return false
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            var found = false
            while (entry != null) {
                if (entry.name == "data.json") {
                    found = true
                    break
                }
                entry = zip.nextEntry
            }
            found
        }
    } catch (_: Exception) {
        false
    }

    // --- JSON mapping -------------------------------------------------------

    private fun DocumentEntity.toJson() = JSONObject()
        .put("name", name).put("type", type).put("category", category)
        .put("description", description).put("number", number)
        .put("issueDate", issueDate).put("expiryDate", expiryDate)
        .put("reminderDays", reminderDays).put("notes", notes)
        .put("attachmentUri", attachmentUri).put("favorite", favorite)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun CategoryEntity.toJson() = JSONObject().put("name", name).put("isDefault", isDefault)

    private fun WarrantyEntity.toJson() = JSONObject()
        .put("productName", productName).put("brand", brand).put("modelNumber", modelNumber)
        .put("serialNumber", serialNumber).put("purchaseDate", purchaseDate)
        .put("startDate", startDate).put("endDate", endDate).put("price", price)
        .put("seller", seller).put("notes", notes).put("invoiceUri", invoiceUri)
        .put("favorite", favorite).put("reminderDays", reminderDays)

    private fun ImportantDateEntity.toJson() = JSONObject()
        .put("title", title).put("date", date).put("description", description)
        .put("reminderDays", reminderDays).put("notes", notes)

    private fun SettingsEntity.toJson() = JSONObject()
        .put("theme", theme).put("defaultReminder", defaultReminder)
        .put("notifications", notifications).put("appLock", appLock)

    private fun JSONObject.toDocument(restoredFiles: Map<String, String>) = DocumentEntity(
        name = optString("name"),
        type = optString("type"),
        category = optString("category", "Other"),
        description = optString("description"),
        number = optString("number"),
        issueDate = optLong("issueDate").takeIf { !isNull("issueDate") },
        expiryDate = optLong("expiryDate").takeIf { !isNull("expiryDate") },
        reminderDays = optInt("reminderDays", 30),
        notes = optString("notes"),
        attachmentUri = restoredFiles[optString("attachmentFile")]
            ?: optString("attachmentUri").takeIf { it.isNotBlank() },
        favorite = optBoolean("favorite")
    )

    private fun JSONObject.toCategory() = CategoryEntity(
        name = optString("name"),
        isDefault = optBoolean("isDefault")
    )

    private fun JSONObject.toWarranty() = WarrantyEntity(
        productName = optString("productName"),
        brand = optString("brand"),
        modelNumber = optString("modelNumber"),
        serialNumber = optString("serialNumber"),
        purchaseDate = optLong("purchaseDate").takeIf { !isNull("purchaseDate") },
        startDate = optLong("startDate").takeIf { !isNull("startDate") },
        endDate = optLong("endDate").takeIf { !isNull("endDate") },
        price = optDouble("price").takeIf { !isNull("price") },
        seller = optString("seller"),
        notes = optString("notes"),
        invoiceUri = optString("invoiceUri").takeIf { it.isNotBlank() },
        favorite = optBoolean("favorite"),
        reminderDays = optInt("reminderDays", 30)
    )

    private fun JSONObject.toImportantDate() = ImportantDateEntity(
        title = optString("title"),
        date = optLong("date"),
        description = optString("description"),
        reminderDays = optInt("reminderDays", 1),
        notes = optString("notes")
    )

    private fun JSONObject.toSettings() = SettingsEntity(
        theme = optString("theme", "System"),
        defaultReminder = optInt("defaultReminder", 30),
        notifications = optBoolean("notifications", true),
        appLock = optBoolean("appLock", false)
    )

    private fun <T> JSONArray.toObjectList(map: (JSONObject) -> T): List<T> =
        (0 until length()).map { map(getJSONObject(it)) }
}
