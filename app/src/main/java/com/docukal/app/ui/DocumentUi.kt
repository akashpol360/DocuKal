package com.docukal.app.ui

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docukal.app.utils.AttachmentUtils
import com.docukal.app.utils.DocKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

// ---------------------------------------------------------------------------
// Document type icon / colour (DigiLocker-style tiles). Material icons are used
// instead of government logos, which are trademarked and must not be copied.
// ---------------------------------------------------------------------------

fun DocKind.icon(): ImageVector = when (this) {
    DocKind.AADHAAR -> Icons.Default.Fingerprint
    DocKind.PAN -> Icons.Default.Badge
    DocKind.PASSPORT -> Icons.Default.Flight
    DocKind.DRIVING_LICENCE -> Icons.Default.DirectionsCar
    DocKind.VOTER_ID -> Icons.Default.HowToVote
    DocKind.ELECTRICITY_BILL -> Icons.Default.Bolt
    DocKind.AFFIDAVIT -> Icons.Default.Gavel
    DocKind.RESUME -> Icons.Default.Work
    DocKind.BANK -> Icons.Default.AccountBalance
    DocKind.INSURANCE -> Icons.Default.HealthAndSafety
    DocKind.EDUCATION -> Icons.Default.School
    DocKind.OTHER -> Icons.Default.Description
}

fun DocKind.tint(): Color = when (this) {
    DocKind.AADHAAR -> Color(0xFFE65100)
    DocKind.PAN -> Color(0xFF1565C0)
    DocKind.PASSPORT -> Color(0xFF283593)
    DocKind.DRIVING_LICENCE -> Color(0xFF2E7D32)
    DocKind.VOTER_ID -> Color(0xFF6A1B9A)
    DocKind.ELECTRICITY_BILL -> Color(0xFFF9A825)
    DocKind.AFFIDAVIT -> Color(0xFF5D4037)
    DocKind.RESUME -> Color(0xFF00838F)
    DocKind.BANK -> Color(0xFF3949AB)
    DocKind.INSURANCE -> Color(0xFFC2185B)
    DocKind.EDUCATION -> Color(0xFFD84315)
    DocKind.OTHER -> Color(0xFF546E7A)
}

@Composable
fun DocTypeBadge(kind: DocKind, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val tint = kind.tint()
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(kind.icon(), contentDescription = kind.label, tint = tint, modifier = Modifier.size(size * 0.46f))
            if (size >= 52.dp) {
                Text(kind.badge, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = tint, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Horizontal row of tappable document types, like DigiLocker's document categories. */
@Composable
fun DocTypePicker(selected: DocKind, onSelect: (DocKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Document type", style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DocKind.entries.forEach { k ->
                val isSelected = k == selected
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier
                        .width(76.dp)
                        .clip(shape)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) k.tint() else MaterialTheme.colorScheme.outlineVariant,
                            shape = shape
                        )
                        .clickable { onSelect(k) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DocTypeBadge(k, size = 48.dp)
                    Text(
                        k.shortLabel,
                        fontSize = 11.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// In-app preview of the attached file (image or first page of a PDF).
// The preview reads the file's own bytes, so it never depends on which external
// app Android picks to open it.
// ---------------------------------------------------------------------------

private sealed interface PreviewState {
    object Loading : PreviewState
    object NoPreview : PreviewState
    class Ready(val bitmap: Bitmap) : PreviewState
    class Failed(val message: String) : PreviewState
}

@Composable
fun AttachmentPreview(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by produceState<PreviewState>(PreviewState.Loading, uri) {
        value = withContext(Dispatchers.IO) { loadPreview(context, uri) }
    }
    when (val s = state) {
        is PreviewState.Loading -> Box(modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is PreviewState.Ready -> Image(
            bitmap = s.bitmap.asImageBitmap(),
            contentDescription = "Document preview",
            modifier = modifier.fillMaxWidth().heightIn(max = 440.dp),
            contentScale = ContentScale.Fit
        )
        is PreviewState.NoPreview -> Box(modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
            Text("Preview not available for this file type. Tap Open to view it.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        is PreviewState.Failed -> Box(modifier.fillMaxWidth().heightIn(min = 72.dp).padding(8.dp), contentAlignment = Alignment.Center) {
            Text(s.message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val MAX_PREVIEW_SIDE = 2048

private fun loadPreview(context: Context, uri: Uri): PreviewState {
    return try {
        // Fail early with a clear message if the file is gone or access was lost.
        (context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException()).close()
        val mime = AttachmentUtils.mimeOf(context, uri)
        when {
            mime == "application/pdf" -> renderPdf(context, uri)
            mime.startsWith("image/") -> decodeImage(context, uri)
            else -> PreviewState.NoPreview
        }
    } catch (e: FileNotFoundException) {
        PreviewState.Failed("The original file was moved or deleted, so it can't be shown. Edit the document and attach it again.")
    } catch (e: SecurityException) {
        PreviewState.Failed("Access to this file was lost. Edit the document and attach it again.")
    } catch (e: OutOfMemoryError) {
        PreviewState.Failed("This file is too large to preview here. Tap Open to view it.")
    } catch (e: Exception) {
        PreviewState.Failed("Preview unavailable. Tap Open to view the file.")
    }
}

private fun decodeImage(context: Context, uri: Uri): PreviewState {
    val resolver = context.contentResolver
    val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder applies EXIF rotation itself and also reads HEIC.
        val path = uri.path
        val source = if (uri.scheme == "file" && path != null) {
            ImageDecoder.createSource(File(path))
        } else {
            ImageDecoder.createSource(resolver, uri)
        }
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            val w = info.size.width
            val h = info.size.height
            var sample = 1
            while (w / sample > MAX_PREVIEW_SIDE || h / sample > MAX_PREVIEW_SIDE) sample *= 2
            decoder.setTargetSampleSize(sample)
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (resolver.openInputStream(uri) ?: throw FileNotFoundException()).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / sample > MAX_PREVIEW_SIDE || bounds.outHeight / sample > MAX_PREVIEW_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = (resolver.openInputStream(uri) ?: throw FileNotFoundException()).use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        raw?.let { rotateByExif(resolver, uri, it) }
    }
    return if (bitmap != null) PreviewState.Ready(bitmap)
    else PreviewState.Failed("This image can't be previewed here. Tap Open to view it.")
}

private fun rotateByExif(resolver: ContentResolver, uri: Uri, src: Bitmap): Bitmap {
    val orientation = runCatching {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return src
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}

private fun renderPdf(context: Context, uri: Uri): PreviewState {
    val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException()
    return pfd.use { fd ->
        PdfRenderer(fd).use { renderer ->
            if (renderer.pageCount == 0) {
                PreviewState.Failed("This PDF has no pages.")
            } else {
                renderer.openPage(0).use { page ->
                    val width = 1080
                    val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    PreviewState.Ready(bmp)
                }
            }
        }
    }
}
