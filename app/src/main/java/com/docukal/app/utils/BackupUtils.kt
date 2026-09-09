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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupData(
    val documents: List<DocumentEntity>,
    val categories: List<CategoryEntity>,
    val warranties: List<WarrantyEntity>,
    val dates: List<ImportantDateEntity>,
    val settings: SettingsEntity?
)

/**
 * Local-only backup/restore: writes a single-entry ZIP (data.json) via Android's
 * Storage Access Framework. Nothing here ever touches the network.
 */
object BackupUtils {

    fun export(
        context: Context,
        uri: Uri,
        docs: List<DocumentEntity>,
        cats: List<CategoryEntity>,
        warranties: List<WarrantyEntity>,
        dates: List<ImportantDateEntity>,
        settings: SettingsEntity?
    ) {
        val outputStream = context.contentResolver.openOutputStream(uri) ?: return
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
            val root = JSONObject()
                .put("version", 2)
                .put("documents", JSONArray(docs.map { it.toJson() }))
                .put("categories", JSONArray(cats.map { it.toJson() }))
                .put("warranties", JSONArray(warranties.map { it.toJson() }))
                .put("dates", JSONArray(dates.map { it.toJson() }))
            settings?.let { root.put("settings", it.toJson()) }

            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(root.toString().toByteArray())
            zip.closeEntry()
        }
    }

    fun read(context: Context, uri: Uri): BackupData? = try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            var text: String? = null
            while (entry != null) {
                if (entry.name == "data.json") text = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
            val root = JSONObject(text ?: return null)

            val docs = root.optJSONArray("documents")?.toObjectList { it.toDocument() } ?: emptyList()
            val cats = root.optJSONArray("categories")?.toObjectList { it.toCategory() } ?: emptyList()
            val warranties = root.optJSONArray("warranties")?.toObjectList { it.toWarranty() } ?: emptyList()
            val dates = root.optJSONArray("dates")?.toObjectList { it.toImportantDate() } ?: emptyList()
            val settings = root.optJSONObject("settings")?.toSettings()

            BackupData(docs, cats, warranties, dates, settings)
        }
    } catch (_: Exception) {
        null
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

    private fun JSONObject.toDocument() = DocumentEntity(
        name = optString("name"),
        type = optString("type"),
        category = optString("category", "Other"),
        description = optString("description"),
        number = optString("number"),
        issueDate = optLong("issueDate").takeIf { !isNull("issueDate") },
        expiryDate = optLong("expiryDate").takeIf { !isNull("expiryDate") },
        reminderDays = optInt("reminderDays", 30),
        notes = optString("notes"),
        attachmentUri = optString("attachmentUri").takeIf { it.isNotBlank() },
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
