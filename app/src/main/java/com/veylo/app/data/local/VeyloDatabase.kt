package com.veylo.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        GroupEntity::class,
        MemberEntity::class,
        ExpenseEntity::class,
        SplitEntity::class,
        SettlementEntity::class,
        OutboxEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class VeyloDatabase : RoomDatabase() {
    abstract fun dao(): VeyloDao

    companion object {
        /** v2 → v3: trip styles and payment details. Keeps all existing data. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `theme` TEXT NOT NULL DEFAULT 'vacation'")
                db.execSQL("UPDATE `groups` SET `theme` = 'cafe' WHERE `type` = 'OUTING'")
                db.execSQL("ALTER TABLE `settlements` ADD COLUMN `method` TEXT NOT NULL DEFAULT 'cash'")
                db.execSQL("ALTER TABLE `settlements` ADD COLUMN `txnId` TEXT")
                db.execSQL("ALTER TABLE `settlements` ADD COLUMN `payStatus` TEXT NOT NULL DEFAULT 'success'")
                db.execSQL("ALTER TABLE `settlements` ADD COLUMN `proofUri` TEXT")
            }
        }

        /** v1 → v2: cloud sync fields. Keeps all existing data. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `shared` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `cloudToken` TEXT")
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `myMemberId` TEXT")
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `lastPulledAt` TEXT")
                db.execSQL("ALTER TABLE `members` ADD COLUMN `revision` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `revision` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `settlements` ADD COLUMN `revision` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `outbox` (`id` TEXT NOT NULL, `groupId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`entityId` TEXT NOT NULL, `baseRevision` INTEGER NOT NULL, `at` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }
    }
}
