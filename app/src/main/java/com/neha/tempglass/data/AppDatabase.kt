package com.neha.tempglass.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ModelEntity::class, CaseModelEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun modelDao(): ModelDao
    abstract fun caseModelDao(): CaseModelDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE models ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE models ADD COLUMN type TEXT NOT NULL DEFAULT 'Flat'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `case_models` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `isUserAdded` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_case_models_modelName` ON `case_models` (`modelName`)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE case_models ADD COLUMN brandSerialNumber INTEGER NOT NULL DEFAULT 0"
                )

                db.execSQL(
                    """
                    WITH ranked AS (
                        SELECT
                            id,
                            LOWER(TRIM(SUBSTR(modelName, 1, INSTR(modelName || ' ', ' ') - 1))) AS brand_key,
                            ROW_NUMBER() OVER (
                                PARTITION BY LOWER(TRIM(SUBSTR(modelName, 1, INSTR(modelName || ' ', ' ') - 1)))
                                ORDER BY id
                            ) AS rn
                        FROM case_models
                    )
                    UPDATE case_models
                    SET brandSerialNumber = (
                        SELECT ranked.rn
                        FROM ranked
                        WHERE ranked.id = case_models.id
                    )
                    WHERE brandSerialNumber = 0
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE models ADD COLUMN syncState TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("ALTER TABLE models ADD COLUMN updatedAtLocal INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE models ADD COLUMN updatedAtRemote INTEGER")
                db.execSQL("ALTER TABLE models ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE models ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE models ADD COLUMN needsHardDeleteRemote INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE case_models ADD COLUMN syncState TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("ALTER TABLE case_models ADD COLUMN updatedAtLocal INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE case_models ADD COLUMN updatedAtRemote INTEGER")
                db.execSQL("ALTER TABLE case_models ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE case_models ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE case_models ADD COLUMN needsHardDeleteRemote INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "temp_glass.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
