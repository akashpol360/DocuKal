package com.docukal.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docukal.app.data.local.AppDatabase
import com.docukal.app.data.local.CategoryEntity
import com.docukal.app.data.local.DocumentEntity
import com.docukal.app.data.local.ImportantDateEntity
import com.docukal.app.data.local.SettingsEntity
import com.docukal.app.data.local.WarrantyEntity
import com.docukal.app.notifications.ReminderKind
import com.docukal.app.notifications.ReminderScheduler
import com.docukal.app.repository.AppRepository
import com.docukal.app.utils.BackupData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val DEFAULT_CATEGORIES = listOf(
    "Personal", "Vehicle", "Insurance", "Finance", "Education",
    "Home", "Shopping", "Medical", "Work", "Other"
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(AppDatabase.get(app))
    private val scheduler = ReminderScheduler(app)

    val documents = repo.documents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val warranties = repo.warranties.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val dates = repo.dates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            // Guarded by a persisted "categoriesSeeded" flag rather than the current
            // in-memory categories list: on cold start the StateFlow above still
            // holds its initial empty value for a moment before Room's first Flow
            // emission arrives, so checking categories.value here would reseed the
            // ten defaults (as duplicates) on every single launch.
            if (repo.categoryCount() == 0) {
                DEFAULT_CATEGORIES.forEach { repo.addCategory(CategoryEntity(name = it, isDefault = true)) }
            }
            if (settings.value == null) repo.saveSettings(SettingsEntity(categoriesSeeded = true))
        }
    }

    fun saveDocument(d: DocumentEntity, onDone: () -> Unit = {}) = viewModelScope.launch {
        val id = if (d.id == 0L) repo.addDocument(d) else {
            repo.updateDocument(d)
            d.id
        }
        scheduler.schedule(ReminderKind.DOCUMENT, id, d.name, d.expiryDate, d.reminderDays)
        onDone()
    }

    fun deleteDocument(d: DocumentEntity) = viewModelScope.launch {
        scheduler.cancel(ReminderKind.DOCUMENT, d.id)
        repo.deleteDocument(d)
    }

    fun toggleFavorite(d: DocumentEntity) = saveDocument(d.copy(favorite = !d.favorite, updatedAt = System.currentTimeMillis()))

    fun addWarranty(w: WarrantyEntity, onDone: () -> Unit = {}) = viewModelScope.launch {
        val id = repo.addWarranty(w)
        scheduler.schedule(ReminderKind.WARRANTY, id, w.productName, w.endDate, w.reminderDays)
        onDone()
    }

    fun deleteWarranty(w: WarrantyEntity) = viewModelScope.launch {
        scheduler.cancel(ReminderKind.WARRANTY, w.id)
        repo.deleteWarranty(w)
    }

    fun addDate(d: ImportantDateEntity, onDone: () -> Unit = {}) = viewModelScope.launch {
        val id = repo.addDate(d)
        scheduler.schedule(ReminderKind.IMPORTANT_DATE, id, d.title, d.date, d.reminderDays)
        onDone()
    }

    fun deleteDate(d: ImportantDateEntity) = viewModelScope.launch {
        scheduler.cancel(ReminderKind.IMPORTANT_DATE, d.id)
        repo.deleteDate(d)
    }

    fun addCategory(n: String) = viewModelScope.launch {
        if (n.isNotBlank() && categories.value.none { it.name.equals(n.trim(), true) }) {
            repo.addCategory(CategoryEntity(name = n.trim()))
        }
    }

    fun deleteCategory(c: CategoryEntity) = viewModelScope.launch {
        if (!c.isDefault) repo.deleteCategory(c)
    }

    fun saveSettings(s: SettingsEntity) = viewModelScope.launch { repo.saveSettings(s) }

    fun importData(x: BackupData) = viewModelScope.launch { repo.importData(x) }

    fun clearAll() = viewModelScope.launch { repo.clearAll() }
}
