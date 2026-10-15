package com.docukal.app.ui

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docukal.app.utils.BackupData
import com.docukal.app.utils.BackupUtils
import com.docukal.app.utils.ExportSummary
import com.docukal.app.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface BackupStep {
    object None : BackupStep
    object ConfirmExport : BackupStep
    object ConfirmImport : BackupStep
    class Working(val title: String) : BackupStep
    class Done(val title: String, val message: String) : BackupStep
}

/**
 * "Export Backup" and "Import Backup" buttons for Settings, with a prompt before and
 * after each action. Export and import run off the main thread while a progress
 * dialog is shown.
 */
@Composable
fun BackupControls(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docs by vm.documents.collectAsState()
    val cats by vm.categories.collectAsState()
    val warranties by vm.warranties.collectAsState()
    val dates by vm.dates.collectAsState()
    val settings by vm.settings.collectAsState()
    var step by remember { mutableStateOf<BackupStep>(BackupStep.None) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) {
            step = BackupStep.None
        } else {
            step = BackupStep.Working("Exporting your backup\u2026")
            scope.launch {
                val summary = withContext(Dispatchers.IO) {
                    runCatching { BackupUtils.export(context, uri, docs, cats, warranties, dates, settings) }.getOrNull()
                }
                step = if (summary != null) {
                    BackupStep.Done("Backup exported successfully", exportMessage(summary))
                } else {
                    // Remove the empty / half-written file the picker created.
                    withContext(Dispatchers.IO) {
                        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                    }
                    BackupStep.Done(
                        "Export failed",
                        "The backup could not be saved. Please check that your phone has free storage and try again."
                    )
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            step = BackupStep.None
        } else {
            step = BackupStep.Working("Importing your backup\u2026")
            scope.launch {
                val existingDocs = docs
                val existingWarranties = warranties
                val existingDates = dates
                val loaded = withContext(Dispatchers.IO) { BackupUtils.read(context, uri) }
                val data = if (loaded != null && loaded.documents.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        BackupUtils.dropDuplicates(context, loaded, existingDocs, existingWarranties, existingDates)
                    }
                } else {
                    null
                }
                val immediate: BackupStep? = when {
                    loaded == null -> BackupStep.Done(
                        "Import failed",
                        "This file is not a valid DocuKal backup or could not be read. Nothing in your app was changed."
                    )
                    loaded.documents.isEmpty() -> BackupStep.Done(
                        "No documents in this backup",
                        "This backup file does not contain any documents, so nothing was imported."
                    )
                    data == null || (data.documents.isEmpty() && data.warranties.isEmpty() && data.dates.isEmpty()) ->
                        BackupStep.Done(
                            "Nothing new to import",
                            "All ${loaded.documents.size} document(s) in this backup already exist in your app, so nothing was imported."
                        )
                    else -> null
                }
                if (immediate != null) {
                    step = immediate
                } else if (data != null) {
                    vm.importData(data) { ok ->
                        step = if (ok) {
                            BackupStep.Done("Backup imported successfully", importMessage(data))
                        } else {
                            BackupStep.Done("Import failed", "The backup could not be added to your data. Please try again.")
                        }
                    }
                }
            }
        }
    }

    Button(
        {
            step = if (docs.isEmpty()) {
                BackupStep.Done(
                    "No documents to export",
                    "You don't have any documents saved yet. Add at least one document, then try exporting again."
                )
            } else {
                BackupStep.ConfirmExport
            }
        },
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Default.CloudUpload, null)
        Spacer(Modifier.width(8.dp))
        Text("Export Backup")
    }
    OutlinedButton({ step = BackupStep.ConfirmImport }, Modifier.fillMaxWidth()) {
        Icon(Icons.Default.CloudDownload, null)
        Spacer(Modifier.width(8.dp))
        Text("Import Backup")
    }

    when (val s = step) {
        is BackupStep.ConfirmExport -> AlertDialog(
            onDismissRequest = { step = BackupStep.None },
            title = { Text("Export backup?") },
            text = {
                Text(
                    "A backup file (.zip) will be created with:\n\n" +
                        "\u2022 Documents: ${docs.size} (with their attached images and PDFs)\n" +
                        "\u2022 Warranties: ${warranties.size}\n" +
                        "\u2022 Important dates: ${dates.size}\n\n" +
                        "You will choose where to save it on the next screen. Keep the file safe, because it contains your personal documents."
                )
            },
            confirmButton = {
                TextButton({
                    step = BackupStep.None
                    exportLauncher.launch("docukal-backup.zip")
                }) { Text("Export") }
            },
            dismissButton = { TextButton({ step = BackupStep.None }) { Text("Cancel") } }
        )

        is BackupStep.ConfirmImport -> AlertDialog(
            onDismissRequest = { step = BackupStep.None },
            title = { Text("Import backup?") },
            text = {
                Text(
                    "Choose a DocuKal backup (.zip) file. Its documents, attached files, warranties, important dates and categories will be added to your current data. Nothing in the app will be deleted.\n\n" +
                        "Items that already exist in the app are skipped, so nothing is imported twice."
                )
            },
            confirmButton = {
                TextButton({
                    step = BackupStep.None
                    importLauncher.launch(arrayOf("application/zip"))
                }) { Text("Choose file") }
            },
            dismissButton = { TextButton({ step = BackupStep.None }) { Text("Cancel") } }
        )

        is BackupStep.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text(s.title) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Please wait and do not close the app.")
                }
            },
            confirmButton = {}
        )

        is BackupStep.Done -> AlertDialog(
            onDismissRequest = { step = BackupStep.None },
            title = { Text(s.title) },
            text = { Text(s.message) },
            confirmButton = { TextButton({ step = BackupStep.None }) { Text("OK") } }
        )

        else -> Unit
    }
}

private fun exportMessage(s: ExportSummary): String {
    var text = "Your backup file was saved.\n\n" +
        "\u2022 Documents: ${s.documents}\n" +
        "\u2022 Attached files included: ${s.attachmentsIncluded}\n" +
        "\u2022 Warranties: ${s.warranties}\n" +
        "\u2022 Important dates: ${s.dates}"
    if (s.attachmentsMissing > 0) {
        text += "\n\n${s.attachmentsMissing} attached file(s) could not be included because the original file is no longer available on this phone."
    }
    return text
}

private fun importMessage(d: BackupData): String {
    var text = "Your backup was restored.\n\n" +
        "\u2022 Documents: ${d.documents.size}\n" +
        "\u2022 Attached files restored: ${d.attachmentsRestored}\n" +
        "\u2022 Warranties: ${d.warranties.size}\n" +
        "\u2022 Important dates: ${d.dates.size}"
    if (d.duplicatesSkipped > 0) {
        text += "\n\u2022 Already in your app (skipped): ${d.duplicatesSkipped}"
    }
    if (d.attachmentsNotIncluded > 0) {
        text += "\n\n${d.attachmentsNotIncluded} document(s) had no file stored in this backup (it was made with an older version). Edit those documents and attach the file again."
    }
    return text
}
