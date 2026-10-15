package com.docukal.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.docukal.app.data.local.DocumentEntity
import com.docukal.app.utils.AttachmentUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

/**
 * Share icon for a document. Shares the attached image / PDF exactly as it is
 * (same file, same name) through the Android share sheet, so it can be sent from
 * WhatsApp, Gmail, Drive, etc. without searching for it in phone storage.
 * Disabled (greyed out) when the document has no attached file.
 */
@Composable
fun ShareDocumentButton(
    doc: DocumentEntity,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val attachment = doc.attachmentUri

    IconButton(
        onClick = {
            if (attachment != null && !busy) {
                busy = true
                scope.launch {
                    val prepared = withContext(Dispatchers.IO) {
                        runCatching { AttachmentUtils.prepareShare(context, attachment.toUri(), doc.name) }.getOrNull()
                    }
                    busy = false
                    if (prepared != null) {
                        AttachmentUtils.startShare(context, prepared.first, prepared.second, doc.name)
                    } else {
                        Toast.makeText(
                            context,
                            "This file can't be shared because it was moved or deleted.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        },
        enabled = attachment != null,
        modifier = modifier
    ) {
        Icon(
            Icons.Default.Share,
            contentDescription = "Share ${doc.name}",
            modifier = Modifier.size(iconSize),
            tint = if (attachment != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}
