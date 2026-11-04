package com.veylo.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        GroupEntity::class,
        MemberEntity::class,
        ExpenseEntity::class,
        SplitEntity::class,
        SettlementEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class VeyloDatabase : RoomDatabase() {
    abstract fun dao(): VeyloDao
}
