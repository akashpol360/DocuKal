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
    val notes: String? = null,
    /** Document kind guessed from the file name / readable text, if any. */
    val kind: DocKind? = null
)

/**
 * A privacy-preserving first-pass document parser. It never uploads a selected
 * file. Text files are read locally; all other formats provide their filename
 * and MIME metadata. The form remains editable so users can correct a guess.
 *
 * Does file I/O: call from a background thread.
 */
object DocumentAutoFill {

    // A keyword followed by a value. The keyword must be a whole word, and the value
    // must contain a digit, otherwise words like "Notification" produced the "number"
    // "tification".
    private val NUMBER_AFTER_KEYWORD =
        Regex("(?i)\\b(?:number|no|id|policy|invoice|passport)\\b[\\s:#.-]*((?=[A-Z0-9/-]*[0-9])[A-Z0-9][A-Z0-9/-]{3,})")

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

        val number = DocumentValidator.findKnownNumber(source)
            ?: NUMBER_AFTER_KEYWORD.find(source)?.groupValues?.getOrNull(1)?.takeIf { v -> v.any { it.isDigit() } }

        val detected = DocKind.detect(stem, "", number.orEmpty())
        val kind = detected.takeIf { it != DocKind.OTHER }

        onComplete(
            DocumentDetails(
                name = stem.takeIf { it.isNotBlank() },
                type = friendlyType(mime, filename),
                number = number,
                category = suggestCategory(source, detected),
                notes = readableText?.takeIf { it.isNotBlank() }?.take(500),
                kind = kind
            )
        )
    }

    private fun friendlyType(mime: String, filename: String): String? = when {
        mime == "application/pdf" || filename.endsWith(".pdf", true) -> "PDF document"
        mime.startsWith("image/") -> "Image document"
        mime.startsWith("text/") -> "Text document"
        else -> filename.substringAfterLast('.', "").uppercase(Locale.ROOT).takeIf { it.isNotBlank() }
    }

    private fun suggestCategory(text: String, kind: DocKind): String? {
        kind.suggestedCategory?.let { return it }
        val value = text.lowercase(Locale.ROOT).replace('_', ' ').replace('-', ' ')
        // Whole-word matching: plain substring checks wrongly matched "pan" in "company"
        // and "car" in "card".
        fun has(vararg words: String): Boolean =
            words.any { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(value) }
        return when {
            has("policy", "insurance", "premium") -> "Insurance"
            has("medical", "prescription") -> "Medical"
            has("car", "vehicle", "registration") -> "Vehicle"
            has("bank", "loan", "tax", "invoice", "receipt") -> "Finance"
            has("degree", "certificate", "school", "university") -> "Education"
            else -> null
        }
    }
}
