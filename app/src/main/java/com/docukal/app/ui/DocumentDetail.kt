package com.docukal.app.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.docukal.app.DetailRow
import com.docukal.app.Title
import com.docukal.app.statusText
import com.docukal.app.utils.AttachmentUtils
import com.docukal.app.utils.DocKind
import com.docukal.app.utils.formatDate
import com.docukal.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun DocumentDetailScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val docs by vm.documents.collectAsState()
    val doc = docs.firstOrNull { it.id == id }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Guards against popping the back stack twice (delete button + "document no longer exists").
    var leaving by remember { mutableStateOf(false) }

    if (doc == null) {
        // On a cold start (e.g. opened from a reminder notification) the document list is
        // still empty for a moment, so wait briefly instead of leaving immediately.
        LaunchedEffect(Unit) {
            delay(1500)
            if (!leaving) {
                leaving = true
                onBack()
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    if (editing) {
        DocumentEditForm(vm, doc) { editing = false }
    } else {
        val kind = DocKind.detect(doc.name, doc.type, doc.number)
        val attachmentUri: Uri? = doc.attachmentUri?.let { Uri.parse(it) }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DocTypeBadge(kind, size = 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Title(doc.name, "${kind.label} \u2022 ${doc.category}")
                }
                ShareDocumentButton(doc)
            }

            Card(shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Event, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(statusText(doc.expiryDate, doc.reminderDays), fontWeight = FontWeight.Bold)
                }
            }

            if (attachmentUri != null) {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AttachmentPreview(attachmentUri, Modifier.clickable { AttachmentUtils.open(context, attachmentUri) })
                        Button(
                            onClick = { AttachmentUtils.open(context, attachmentUri) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AttachFile, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open Attachment")
                        }
                    }
                }
            }

            DetailRow("Type", doc.type)
            DetailRow("Number", doc.number)
            DetailRow("Issue date", formatDate(doc.issueDate))
            DetailRow("Expiry date", formatDate(doc.expiryDate))
            DetailRow("Reminder", if (doc.reminderDays <= 0) "No reminder" else "${doc.reminderDays} days before")
            DetailRow("Notes", doc.notes)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button({ editing = true }, Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
                OutlinedButton({ confirmDelete = true }, Modifier.weight(1f)) {
                    Icon(Icons.Default.DeleteForever, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
                IconButton({ vm.toggleFavorite(doc) }) {
                    Icon(if (doc.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Favorite")
                }
            }
            TextButton({ if (!leaving) { leaving = true; onBack() } }) { Text("Back") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete document?") },
            text = { Text("Are you sure you want to delete this document?") },
            confirmButton = {
                Button({
                    confirmDelete = false
                    vm.deleteDocument(doc)
                    if (!leaving) {
                        leaving = true
                        onBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
