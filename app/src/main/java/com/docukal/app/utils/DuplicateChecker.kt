package com.docukal.app.utils

import android.content.Context
import android.net.Uri
import com.docukal.app.data.local.DocumentEntity
import com.docukal.app.data.local.ImportantDateEntity
import com.docukal.app.data.local.WarrantyEntity
import java.security.MessageDigest

/**
 * One definition of "this document already exists", used both when saving a document
 * and when importing a backup, so the two always agree.
 *
 * Two documents are duplicates when any of these is true:
 *  1. IDENTITY_NUMBER - same identity document type (Aadhaar, PAN, Passport, Driving
 *     Licence, Voter ID) with the same number.
 *  2. SAME_FILE - the attached files are byte-for-byte identical.
 *  3. SAME_DETAILS - neither has a file, and name, type, number, issue date and expiry
 *     date are all the same.
 *
 * Two documents with the same name but different attached files (for example two
 * monthly bills) are NOT duplicates.
 *
 * Does file I/O when comparing attachments: call from a background thread.
 */
object DuplicateChecker {

    enum class Reason { IDENTITY_NUMBER, SAME_FILE, SAME_DETAILS }

    data class Match(val other: DocumentEntity, val reason: Reason)

    /**
     * @param candidate the document being saved / imported (id 0 when it is new).
     * @param others documents it must be unique against.
     * @param original when editing: the document as it was before the edit. A document
     *   that was already a duplicate of another one before the edit is not blocked
     *   again for unrelated changes.
     */
    fun findDocumentDuplicate(
        context: Context,
        candidate: DocumentEntity,
        others: List<DocumentEntity>,
        original: DocumentEntity? = null,
        hashCache: MutableMap<String, String?> = mutableMapOf()
    ): Match? {
        for (other in others) {
            if (candidate.id != 0L && other.id == candidate.id) continue
            val reason = reasonFor(context, candidate, other, hashCache) ?: continue
            if (original != null && reasonFor(context, original, other, hashCache) == reason) continue
            return Match(other, reason)
        }
        return null
    }

    fun describe(match: Match): String {
        val name = match.other.name
        val kind = DocKind.detect(match.other.name, match.other.type, match.other.number)
        return when (match.reason) {
            Reason.IDENTITY_NUMBER ->
                "A ${kind.label} with this number is already saved as \"$name\".\n\nEach document can be saved only once."
            Reason.SAME_FILE ->
                "This file is already saved in the document \"$name\".\n\nEach document can be saved only once."
            Reason.SAME_DETAILS ->
                "A document with the same name, type, number and dates is already saved (\"$name\").\n\nEach document can be saved only once."
        }
    }

    fun warrantyKey(w: WarrantyEntity): String = listOf(
        w.productName.trim().lowercase(), w.brand.trim().lowercase(),
        w.modelNumber.trim().lowercase(), w.serialNumber.trim().lowercase(),
        day(w.purchaseDate)?.toString().orEmpty(), day(w.endDate)?.toString().orEmpty()
    ).joinToString("|")

    fun dateKey(d: ImportantDateEntity): String =
        d.title.trim().lowercase() + "|" + startOfDay(d.date)

    // ---------------------------------------------------------------------

    private fun reasonFor(
        context: Context,
        a: DocumentEntity,
        b: DocumentEntity,
        cache: MutableMap<String, String?>
    ): Reason? {
        val kindA = DocKind.detect(a.name, a.type, a.number)
        val kindB = DocKind.detect(b.name, b.type, b.number)
        val numberA = DocumentValidator.normalizeNumber(kindA, a.number).lowercase()
        val numberB = DocumentValidator.normalizeNumber(kindB, b.number).lowercase()

        if (kindA.uniqueNumber && kindA == kindB && numberA.isNotEmpty() && numberA == numberB) {
            return Reason.IDENTITY_NUMBER
        }
        if (sameFile(context, a.attachmentUri, b.attachmentUri, cache)) return Reason.SAME_FILE

        if (a.attachmentUri == null && b.attachmentUri == null &&
            nameKey(a) == nameKey(b) &&
            kindA == kindB &&
            (kindA != DocKind.OTHER || a.type.trim().lowercase() == b.type.trim().lowercase()) &&
            numberA == numberB &&
            day(a.issueDate) == day(b.issueDate) &&
            day(a.expiryDate) == day(b.expiryDate)
        ) {
            return Reason.SAME_DETAILS
        }
        return null
    }

    private fun nameKey(d: DocumentEntity): String =
        d.name.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun day(value: Long?): Long? = value?.let { startOfDay(it) }

    private fun sameFile(context: Context, x: String?, y: String?, cache: MutableMap<String, String?>): Boolean {
        if (x == null || y == null) return false
        if (x == y) return true
        val sizeX = AttachmentUtils.sizeOf(context, Uri.parse(x))
        val sizeY = AttachmentUtils.sizeOf(context, Uri.parse(y))
        // Different sizes can never be the same file, so most pairs are rejected without reading them.
        if (sizeX >= 0 && sizeY >= 0 && sizeX != sizeY) return false
        val hashX = hashOf(context, x, cache) ?: return false
        val hashY = hashOf(context, y, cache) ?: return false
        return hashX == hashY
    }

    private fun hashOf(context: Context, key: String, cache: MutableMap<String, String?>): String? {
        if (cache.containsKey(key)) return cache[key]
        val hash = sha256(context, Uri.parse(key))
        cache[key] = hash
        return hash
    }

    /** SHA-256 of a file's bytes, or null if it cannot be read. */
    fun sha256(context: Context, uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            stream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
