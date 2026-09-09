package com.docukal.app.repository

import com.docukal.app.data.local.AppDatabase
import com.docukal.app.data.local.CategoryEntity
import com.docukal.app.data.local.DocumentEntity
import com.docukal.app.data.local.ImportantDateEntity
import com.docukal.app.data.local.SettingsEntity
import com.docukal.app.data.local.WarrantyEntity
import com.docukal.app.utils.BackupData
import kotlinx.coroutines.flow.Flow

class AppRepository(private val db: AppDatabase) {

    val documents: Flow<List<DocumentEntity>> = db.documents().all()
    val categories: Flow<List<CategoryEntity>> = db.categories().all()
    val warranties: Flow<List<WarrantyEntity>> = db.warranties().all()
    val dates: Flow<List<ImportantDateEntity>> = db.dates().all()
    val settings: Flow<SettingsEntity?> = db.settings().get()

    suspend fun categoryCount(): Int = db.categories().count()

    suspend fun addDocument(x: DocumentEntity) = db.documents().insert(x)
    suspend fun updateDocument(x: DocumentEntity) = db.documents().update(x)
    suspend fun deleteDocument(x: DocumentEntity) = db.documents().delete(x)

    suspend fun addWarranty(x: WarrantyEntity) = db.warranties().insert(x)
    suspend fun updateWarranty(x: WarrantyEntity) = db.warranties().update(x)
    suspend fun deleteWarranty(x: WarrantyEntity) = db.warranties().delete(x)

    suspend fun addDate(x: ImportantDateEntity) = db.dates().insert(x)
    suspend fun deleteDate(x: ImportantDateEntity) = db.dates().delete(x)

    suspend fun addCategory(x: CategoryEntity) = db.categories().insert(x)
    suspend fun updateCategory(x: CategoryEntity) = db.categories().update(x)
    suspend fun deleteCategory(x: CategoryEntity) = db.categories().delete(x)

    suspend fun saveSettings(x: SettingsEntity) = db.settings().save(x)

    suspend fun importData(x: BackupData) {
        // Fetch existing category names once so repeat imports of the same backup
        // don't keep creating duplicate categories.
        val existingCategoryNames = db.categories().listOnce().map { it.name.lowercase() }.toMutableSet()
        x.categories.forEach { incoming ->
            if (existingCategoryNames.add(incoming.name.lowercase())) {
                db.categories().insert(incoming.copy(id = 0))
            }
        }
        x.documents.forEach { db.documents().insert(it.copy(id = 0)) }
        x.warranties.forEach { db.warranties().insert(it.copy(id = 0)) }
        x.dates.forEach { db.dates().insert(it.copy(id = 0)) }
        x.settings?.let { db.settings().save(it) }
    }

    suspend fun clearAll() {
        db.documents().clear()
        db.warranties().clear()
        db.dates().clear()
    }
}
