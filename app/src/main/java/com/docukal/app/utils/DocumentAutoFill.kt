package com.docukal.app.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

/** Values that can be inferred locally when a user selects an attachment. */
data class DocumentDetails(
    val name: String? = null,
    val type: String? = null,
    val number: String? = null,
    val category: String? = null,
    val notes: String? = null
)

/**
 * A privacy-preserving first-pass document parser. It never uploads a selected
 * file. Text files are read locally; all other formats provide their filename
 * and MIME metadata. The form remains editable so users can correct a guess.
 */
object DocumentAutoFill {
    fun analyse(context: Context, uri: Uri, onComplete: (DocumentDetails) -> Unit) {
        val resolver = context.contentResolver
        val filename = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment.orEmpty()
        val mime = resolver.getType(uri).orEmpty()
        val stem = filename.substringBeforeLast('.', filename).replace('_', ' ').replace('-', ' ').trim()
        val readableText = if (mime.startsWith("text/")) {
            runCatching { resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(4_000) } }.getOrNull()
        } else null
        val source = listOf(filename, readableText.orEmpty()).joinToString(" ")
        val number = Regex("(?i)(?:number|no|id|policy|invoice|passport)[\\s:#-]*([A-Z0-9][A-Z0-9/-]{3,})")
            .find(source)?.groupValues?.getOrNull(1)

        onComplete(
            DocumentDetails(
                name = stem.takeIf { it.isNotBlank() },
                type = friendlyType(mime, filename),
                number = number,
                category = suggestCategory(source),
                notes = readableText?.takeIf { it.isNotBlank() }?.take(500)
            )
        )
    }

    private fun friendlyType(mime: String, filename: String): String? = when {
        mime == "application/pdf" || filename.endsWith(".pdf", true) -> "PDF document"
        mime.startsWith("image/") -> "Image document"
        mime.startsWith("text/") -> "Text document"
        else -> filename.substringAfterLast('.', "").uppercase(Locale.ROOT).takeIf { it.isNotBlank() }
    }

    private fun suggestCategory(text: String): String? {
        val value = text.lowercase(Locale.ROOT)
        return when {
            listOf("policy", "insurance", "premium").any(value::contains) -> "Insurance"
            listOf("passport", "aadhaar", "pan", "medical", "prescription").any(value::contains) -> "Personal"
            listOf("car", "vehicle", "registration", "driving").any(value::contains) -> "Vehicle"
            listOf("bank", "loan", "tax", "invoice", "receipt").any(value::contains) -> "Finance"
            listOf("degree", "certificate", "school", "university").any(value::contains) -> "Education"
            else -> null
        }
    }
}
