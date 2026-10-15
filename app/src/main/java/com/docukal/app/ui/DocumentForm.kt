package com.docukal.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.docukal.app.DateField
import com.docukal.app.ReminderSelector
import com.docukal.app.Title
import com.docukal.app.data.local.DocumentEntity
import com.docukal.app.showSaveAdIfNeeded
import com.docukal.app.utils.AttachmentUtils
import com.docukal.app.utils.DocKind
import com.docukal.app.utils.DocumentAutoFill
import com.docukal.app.utils.DocumentDetails
import com.docukal.app.utils.DocumentValidator
import com.docukal.app.utils.DuplicateChecker
import com.docukal.app.utils.NumberState
import com.docukal.app.utils.startOfDay
import com.docukal.app.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DAY_MS = 86_400_000L
private val VALID_GREEN = Color(0xFF2E7D32)

/** Add Document screen (same name and signature as before, so navigation is unchanged). */
@Composable
fun DocumentEditor(vm: MainViewModel, premium: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    var showSavedDialog by remember { mutableStateOf(false) }

    DocumentFormContent(
        vm = vm,
        initial = null,
        onSave = { entity ->
            vm.saveDocument(entity) {
                showSaveAdIfNeeded(context, premium) { showSavedDialog = true }
            }
        },
        onCancel = onDone
    )

    if (showSavedDialog) {
        AlertDialog(
            onDismissRequest = { showSavedDialog = false; onDone() },
            title = { Text("Document saved") },
            text = { Text("Your document has been saved successfully.") },
            confirmButton = { TextButton({ showSavedDialog = false; onDone() }) { Text("OK") } }
        )
    }
}

/** Edit Document screen (same name and signature as before). */
@Composable
fun DocumentEditForm(vm: MainViewModel, doc: DocumentEntity, onDone: () -> Unit) {
    DocumentFormContent(
        vm = vm,
        initial = doc,
        onSave = { entity -> vm.saveDocument(entity) { onDone() } },
        onCancel = onDone
    )
}

@Composable
private fun DocumentFormContent(
    vm: MainViewModel,
    initial: DocumentEntity?,
    onSave: (DocumentEntity) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories by vm.categories.collectAsState()
    val allDocs by vm.documents.collectAsState()
    val isEdit = initial != null

    var attachment by remember { mutableStateOf(initial?.attachmentUri?.let { Uri.parse(it) }) }
    var kind by remember {
        mutableStateOf(if (initial != null) DocKind.detect(initial.name, initial.type, initial.number) else DocKind.OTHER)
    }
    var customType by remember {
        mutableStateOf(if (initial != null && DocKind.fromLabel(initial.type) == null) initial.type else "")
    }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var number by remember { mutableStateOf(initial?.number ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: categories.firstOrNull()?.name ?: "Other") }
    var categoryTouched by remember { mutableStateOf(isEdit) }
    var issue by remember { mutableStateOf(initial?.issueDate) }
    var expiry by remember { mutableStateOf(initial?.expiryDate) }
    var reminderDays by remember {
        mutableStateOf(initial?.reminderDays ?: (vm.settings.value?.defaultReminder ?: 30))
    }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    var fileKind by remember { mutableStateOf<DocKind?>(null) }
    var fileMessage by remember { mutableStateOf<String?>(null) }
    var fileMessageIsError by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingWarnings by remember { mutableStateOf<List<String>?>(null) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var duplicateMessage by remember { mutableStateOf<String?>(null) }

    val numberCheck = DocumentValidator.checkNumber(kind, number)

    fun suggestCategory(suggested: String?) {
        if (categoryTouched || suggested == null) return
        categories.firstOrNull { it.name.equals(suggested, ignoreCase = true) }?.let { category = it.name }
    }

    fun selectKind(newKind: DocKind) {
        val autoNamed = name.isBlank() || DocKind.entries.any { it.label == name }
        kind = newKind
        if (autoNamed) name = if (newKind == DocKind.OTHER) "" else newKind.label
        suggestCategory(newKind.suggestedCategory)
    }

    fun applyDetails(details: DocumentDetails) {
        fileKind = details.kind
        val detected = details.kind ?: DocKind.OTHER
        if (kind == DocKind.OTHER && detected != DocKind.OTHER) kind = detected
        details.number?.let { if (number.isBlank()) number = it }
        if (!isEdit) {
            if (name.isBlank()) name = if (kind != DocKind.OTHER) kind.label else (details.name ?: "")
            details.notes?.let { if (notes.isBlank()) notes = it }
        }
        suggestCategory(kind.suggestedCategory ?: details.category)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Keeps the read permission after the app restarts. Some providers refuse
            // persistable grants, which must not crash the screen.
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch {
                var details: DocumentDetails? = null
                val check = withContext(Dispatchers.IO) {
                    val result = AttachmentUtils.check(context, uri)
                    if (result.ok) DocumentAutoFill.analyse(context, uri) { details = it }
                    result
                }
                if (!check.ok) {
                    fileMessage = check.error
                    fileMessageIsError = true
                } else {
                    attachment = uri
                    fileMessage = check.warning
                    fileMessageIsError = false
                    details?.let { applyDetails(it) }
                }
            }
        }
    }

    fun collectIssues(): Pair<List<String>, List<String>> {
        val errs = mutableListOf<String>()
        val warns = mutableListOf<String>()
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) errs += "Document name is required"
        else if (trimmedName.length > 100) errs += "Document name is too long (maximum 100 characters)"

        if (kind.numberRequired && number.isBlank()) errs += "${kind.label} number is required"
        if (number.isNotBlank() && numberCheck.state == NumberState.INVALID) errs += numberCheck.message

        val today = startOfDay(System.currentTimeMillis())
        val i = issue
        val e = expiry
        if (i != null && startOfDay(i) >= today + DAY_MS) errs += "Issue date cannot be in the future"
        if (i != null && e != null && startOfDay(e) < startOfDay(i)) errs += "Expiry date cannot be before the issue date"

        val fk = fileKind
        if (fk != null && fk != DocKind.OTHER && kind != DocKind.OTHER && fk != kind) {
            warns += "The file name looks like a ${fk.label}, but you selected ${kind.label}."
        }
        if (e != null && startOfDay(e) < today) warns += "This document has already expired."
        if (kind.numberRequired && attachment == null) {
            warns += "No document file is attached. You can add the image or PDF now, or later by editing."
        }
        return errs to warns
    }

    fun buildEntity(): DocumentEntity {
        val typeText = if (kind != DocKind.OTHER) kind.label else customType.trim().ifBlank { DocKind.OTHER.label }
        val formattedNumber = DocumentValidator.formatNumber(kind, number)
        val uriText = attachment?.toString()
        val base = initial
        val entity = if (base != null) {
            base.copy(
                name = name.trim(), type = typeText, category = category, number = formattedNumber,
                issueDate = issue, expiryDate = expiry, reminderDays = reminderDays, notes = notes,
                attachmentUri = uriText, updatedAt = System.currentTimeMillis()
            )
        } else {
            DocumentEntity(
                name = name.trim(), type = typeText, category = category, number = formattedNumber,
                issueDate = issue, expiryDate = expiry, reminderDays = reminderDays, notes = notes,
                attachmentUri = uriText
            )
        }
        return entity
    }

    fun commit() {
        if (saving) return
        saving = true
        onSave(buildEntity())
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Title(
            if (isEdit) "Edit Document" else "Add Document",
            "Upload your document first, then confirm its details"
        )

        // 1. Upload comes first, like DigiLocker.
        UploadSection(
            attachment = attachment,
            message = fileMessage,
            messageIsError = fileMessageIsError,
            onPick = { picker.launch(arrayOf("image/*", "application/pdf", "text/*")) },
            onRemove = { attachment = null; fileMessage = null; fileKind = null }
        )

        // 2. Document type with icon, then details.
        DocTypePicker(kind) { selectKind(it) }
        if (kind == DocKind.OTHER) {
            OutlinedTextField(
                customType, { customType = it.take(60) }, Modifier.fillMaxWidth(),
                label = { Text("Describe the document type (optional)") }, singleLine = true
            )
        }

        OutlinedTextField(
            name, { name = it }, Modifier.fillMaxWidth(),
            label = { Text("Document name *") }, singleLine = true
        )

        OutlinedTextField(
            value = number,
            onValueChange = { number = it.take(40) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (kind.numberRequired) "${kind.numberLabel} *" else kind.numberLabel) },
            singleLine = true,
            isError = numberCheck.state == NumberState.INVALID,
            keyboardOptions = when (kind) {
                DocKind.AADHAAR -> KeyboardOptions(keyboardType = KeyboardType.Number)
                DocKind.PAN, DocKind.PASSPORT, DocKind.VOTER_ID, DocKind.DRIVING_LICENCE ->
                    KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                else -> KeyboardOptions.Default
            },
            supportingText = {
                if (numberCheck.state == NumberState.VALID) {
                    Text("\u2713 ${numberCheck.message} (checked offline, not verified with the issuer)", color = VALID_GREEN)
                } else if (numberCheck.state == NumberState.INVALID) {
                    Text(numberCheck.message)
                } else if (kind.numberRequired) {
                    Text("Required for ${kind.label}")
                }
            }
        )

        // 3. Category: now available on both Add and Edit.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Category") },
                    leadingIcon = { Icon(Icons.Default.Category, null) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                    readOnly = true,
                    singleLine = true
                )
                // Transparent layer so a tap anywhere on the field opens the picker.
                Box(Modifier.matchParentSize().clickable { showCategoryManager = true })
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { showCategoryManager = true }) { Icon(Icons.Default.Settings, "Manage categories") }
        }

        DateField("Issue date", issue, onClear = { issue = null }) { issue = it }
        DateField("Expiry date", expiry, onClear = { expiry = null }) { expiry = it }

        ReminderSelector(reminderDays) { reminderDays = it }
        if (reminderDays > 0 && expiry == null) {
            Text(
                "Set an expiry date to receive this reminder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes") }, minLines = 3)

        if (errors.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                errors.forEach { Text("\u2022 $it", color = MaterialTheme.colorScheme.error) }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val (errs, warns) = collectIssues()
                    errors = errs
                    if (errs.isEmpty()) {
                        checking = true
                        scope.launch {
                            val candidate = buildEntity()
                            val others = allDocs
                            val match = withContext(Dispatchers.IO) {
                                DuplicateChecker.findDocumentDuplicate(context, candidate, others, initial)
                            }
                            checking = false
                            if (match != null) {
                                duplicateMessage = DuplicateChecker.describe(match)
                            } else if (warns.isNotEmpty()) {
                                pendingWarnings = warns
                            } else {
                                commit()
                            }
                        }
                    }
                },
                enabled = !saving && !checking,
                modifier = Modifier.weight(1f)
            ) { Text(if (isEdit) "Save changes" else "Save Document") }
            OutlinedButton(onCancel, Modifier.weight(1f)) { Text("Cancel") }
        }
    }

    duplicateMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { duplicateMessage = null },
            title = { Text("Document already exists") },
            text = { Text(message) },
            confirmButton = { TextButton({ duplicateMessage = null }) { Text("OK") } }
        )
    }

    pendingWarnings?.let { warns ->
        AlertDialog(
            onDismissRequest = { pendingWarnings = null },
            title = { Text("Please check before saving") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    warns.forEach { Text("\u2022 $it") }
                }
            },
            confirmButton = { TextButton({ pendingWarnings = null; commit() }) { Text("Save anyway") } },
            dismissButton = { TextButton({ pendingWarnings = null }) { Text("Review") } }
        )
    }

    if (showCategoryManager) {
        AlertDialog(
            onDismissRequest = { showCategoryManager = false },
            title = { Text("Manage Categories") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Choose a category for this document",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    categories.forEach { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.name,
                                Modifier.weight(1f).clickable {
                                    category = item.name
                                    categoryTouched = true
                                    showCategoryManager = false
                                }.padding(vertical = 8.dp),
                                fontWeight = if (item.name == category) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (item.name == category) MaterialTheme.colorScheme.primary else Color.Unspecified
                            )
                            if (!item.isDefault) {
                                IconButton(onClick = { vm.deleteCategory(item) }) {
                                    Icon(Icons.Default.Delete, "Delete category", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    var newCategory by remember { mutableStateOf("") }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            newCategory, { newCategory = it }, Modifier.weight(1f),
                            label = { Text("New category") }, singleLine = true
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(onClick = {
                            if (newCategory.isNotBlank()) {
                                vm.addCategory(newCategory.trim())
                                newCategory = ""
                            }
                        }) { Icon(Icons.Default.Add, "Add category", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            },
            confirmButton = { TextButton({ showCategoryManager = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun UploadSection(
    attachment: Uri?,
    message: String?,
    messageIsError: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        if (attachment == null) {
            Column(
                Modifier.fillMaxWidth().clickable(onClick = onPick).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.UploadFile, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Upload document", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Choose an image or PDF (up to 20 MB)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val fileName = remember(attachment) {
                AttachmentUtils.displayName(context, attachment) ?: attachment.lastPathSegment ?: "Attached file"
            }
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AttachmentPreview(attachment, Modifier.clickable { AttachmentUtils.open(context, attachment) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(fileName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onPick, Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) { Text("Replace", maxLines = 1, softWrap = false) }
                    OutlinedButton(
                        { AttachmentUtils.open(context, attachment) }, Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) { Text("Open", maxLines = 1, softWrap = false) }
                }
                TextButton(onRemove, Modifier.align(Alignment.End)) { Text("Remove file", maxLines = 1, softWrap = false) }
            }
        }
    }
}
