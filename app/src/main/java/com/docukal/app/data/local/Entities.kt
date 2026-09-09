package com.docukal.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isDefault: Boolean = false
)

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String = "",
    val category: String,
    val description: String = "",
    val number: String = "",
    val issueDate: Long? = null,
    val expiryDate: Long? = null,
    val reminderDays: Int = 30,
    val notes: String = "",
    val attachmentUri: String? = null,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "warranties")
data class WarrantyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productName: String,
    val brand: String = "",
    val modelNumber: String = "",
    val serialNumber: String = "",
    val purchaseDate: Long? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val price: Double? = null,
    val seller: String = "",
    val notes: String = "",
    val invoiceUri: String? = null,
    val favorite: Boolean = false,
    // Added in schema v2: without this, warranty expiries could never schedule a
    // local reminder the way documents do.
    val reminderDays: Int = 30,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "important_dates")
data class ImportantDateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: Long,
    val description: String = "",
    val reminderDays: Int = 1,
    val notes: String = ""
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val theme: String = "System",
    val defaultReminder: Int = 30,
    val notifications: Boolean = true,
    val appLock: Boolean = false,
    // Added in schema v2: lets the app tell "categories were never seeded" apart
    // from "categories were seeded and the user deleted all of them".
    val categoriesSeeded: Boolean = false
)
