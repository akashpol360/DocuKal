package com.docukal.app.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Helpers for the file attached to a document.
 *
 * Why this exists: images were opened with an ACTION_VIEW intent that carried no
 * explicit MIME type. Many file providers report a wrong or generic type for images,
 * so Android offered text viewers and the picture appeared as random characters.
 * Here the type is taken from the file's real signature (magic bytes) first.
 */
object AttachmentUtils {

    const val MAX_BYTES = 20L * 1024L * 1024L

    data class Check(
        val ok: Boolean,
        val error: String? = null,
        val warning: String? = null,
        val name: String = "",
        val mime: String = ""
    )

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }
    }.getOrNull()

    fun sizeOf(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            val path = uri.path ?: return -1L
            val file = File(path)
            return if (file.exists()) file.length() else -1L
        }
        return querySize(context, uri)
    }

    private fun querySize(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L }
    }.getOrNull() ?: -1L

    /** First bytes of the file, or null if it cannot be opened. */
    private fun readHeader(context: Context, uri: Uri, max: Int = 1024): ByteArray? =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(max)
            var total = 0
            while (total < max) {
                val n = input.read(buf, total, max - total)
                if (n <= 0) break
                total += n
            }
            buf.copyOf(total)
        }

    /** Detects JPEG / PNG / GIF / WEBP / HEIC / PDF from the file's own bytes. */
    fun sniffMime(h: ByteArray): String? {
        fun at(i: Int): Int = if (i < h.size) (h[i].toInt() and 0xFF) else -1
        fun starts(vararg b: Int): Boolean = b.indices.all { at(it) == b[it] }
        return when {
            starts(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            starts(0x89, 0x50, 0x4E, 0x47) -> "image/png"
            starts(0x47, 0x49, 0x46, 0x38) -> "image/gif"
            starts(0x52, 0x49, 0x46, 0x46) && at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50 -> "image/webp"
            at(4) == 0x66 && at(5) == 0x74 && at(6) == 0x79 && at(7) == 0x70 -> "image/heif"
            hasPdfHeader(h) -> "application/pdf"
            else -> null
        }
    }

    // A PDF header may legally appear anywhere in the first 1024 bytes.
    private fun hasPdfHeader(h: ByteArray): Boolean {
        val sig = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D) // "%PDF-"
        if (h.size < sig.size) return false
        for (i in 0..(h.size - sig.size)) {
            var match = true
            for (j in sig.indices) {
                if (h[i + j] != sig[j]) { match = false; break }
            }
            if (match) return true
        }
        return false
    }

    /** Real MIME type: file signature first, then what the provider says, then the extension. */
    fun mimeOf(context: Context, uri: Uri): String {
        val sniffed = runCatching { readHeader(context, uri)?.let { sniffMime(it) } }.getOrNull()
        if (sniffed != null) return sniffed

        val declared = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (!declared.isNullOrBlank() && declared != "application/octet-stream" && declared != "*/*") return declared

        val name = displayName(context, uri) ?: uri.lastPathSegment.orEmpty()
        val ext = name.substringAfterLast('.', "").lowercase()
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        return declared ?: "*/*"
    }

    /**
     * Checks a freshly picked file before it is attached. Errors block the attachment;
     * warnings are shown but allow it. Does file I/O: call from a background thread.
     */
    fun check(context: Context, uri: Uri): Check {
        val name = displayName(context, uri) ?: uri.lastPathSegment ?: "file"
        val header = try {
            readHeader(context, uri)
        } catch (e: Exception) {
            null
        } ?: return Check(false, error = "This file can't be read. Please choose it again or pick a different file.", name = name)

        val size = sizeOf(context, uri)
        if (header.isEmpty() || size == 0L) {
            return Check(false, error = "This file is empty.", name = name)
        }
        if (size > MAX_BYTES) {
            return Check(false, error = "This file is larger than 20 MB. Please choose a smaller image or PDF.", name = name)
        }

        val sniffed = sniffMime(header)
        val declared = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        val ext = name.substringAfterLast('.', "").lowercase()
        val byExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext).orEmpty()
        val effective = sniffed ?: declared.ifBlank { byExt }

        val isPdfClaim = declared == "application/pdf" || byExt == "application/pdf"
        if (isPdfClaim && sniffed != "application/pdf") {
            return Check(false, error = "This file is not a valid PDF (it may be damaged). Please choose another copy.", name = name)
        }

        val allowed = effective.startsWith("image/") || effective == "application/pdf" || effective.startsWith("text/")
        val unknown = effective.isBlank() || effective == "application/octet-stream" || effective == "*/*"
        if (!allowed && !unknown) {
            return Check(false, error = "Unsupported file type. Please choose an image or a PDF.", name = name)
        }

        val warning = when {
            unknown -> "Could not confirm the file type. It will be saved, but may not preview correctly."
            effective.startsWith("image/") && sniffed == null ->
                "Could not verify this image's format. It will be saved, but may not preview correctly."
            else -> null
        }
        return Check(true, warning = warning, name = name, mime = effective)
    }

    /** Opens the attachment in another app with its real MIME type set. */
    fun open(context: Context, uri: Uri) {
        val mime = mimeOf(context, uri)
        // Files restored from a backup live in the app's private folder (file:// URI).
        // Other apps may only receive them through a FileProvider content:// URI.
        val shareUri = try {
            val path = uri.path
            if (uri.scheme == "file" && path != null) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
            } else {
                uri
            }
        } catch (e: Exception) {
            toast(context, "Could not open this file.")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareUri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(context, "No app found to open this file.")
        } catch (e: SecurityException) {
            toast(context, "Access to this file was lost. Edit the document and attach the file again.")
        } catch (e: Exception) {
            toast(context, "Could not open this file.")
        }
    }

    /**
     * Copies the attachment into the cache folder under its real file name and returns a
     * FileProvider URI for it plus its MIME type, so ANY app can receive it, whichever
     * app or folder it originally came from. Does file I/O: call from a background thread.
     * Returns null if the file cannot be read.
     */
    fun prepareShare(context: Context, uri: Uri, fallbackName: String): Pair<Uri, String>? {
        val mime = mimeOf(context, uri)
        var name = (displayName(context, uri) ?: uri.lastPathSegment ?: fallbackName)
            .substringAfterLast('/')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { fallbackName.ifBlank { "document" } }
        if (!name.contains('.')) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { name = "$name.$it" }
        }

        val root = File(context.cacheDir, "shared")
        // Remove copies made by earlier shares (older than 10 minutes) so the cache doesn't grow.
        root.listFiles()?.forEach { old ->
            if (System.currentTimeMillis() - old.lastModified() > 10 * 60 * 1000L) old.deleteRecursively()
        }
        val dir = File(root, java.util.UUID.randomUUID().toString())
        dir.mkdirs()
        val target = File(dir, name)

        val input = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        } ?: return null
        input.use { src -> target.outputStream().use { out -> src.copyTo(out) } }

        val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        return shareUri to mime
    }

    /** Opens the system share sheet for a file prepared by [prepareShare]. */
    fun startShare(context: Context, shareUri: Uri, mime: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = android.content.ClipData.newRawUri(title, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share document").apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            toast(context, "No app found to share this file.")
        }
    }

    private fun toast(context: Context, message: String) =
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
