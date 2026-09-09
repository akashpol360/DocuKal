package com.docukal.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        DocumentEntity::class,
        WarrantyEntity::class,
        ImportantDateEntity::class,
        SettingsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun documents(): DocumentDao
    abstract fun warranties(): WarrantyDao
    abstract fun dates(): ImportantDateDao
    abstract fun settings(): SettingsDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** Adds warranty reminders and a one-shot "categories already seeded" flag. */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE warranties ADD COLUMN reminderDays INTEGER NOT NULL DEFAULT 30")
                db.execSQL("ALTER TABLE settings ADD COLUMN categoriesSeeded INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "my_documents.db"
            ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
        }
    }
}
