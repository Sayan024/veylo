package com.veylo.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VeyloDao {
    // ---- Observed streams (drive the UI) ----
    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun groups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM members ORDER BY joinedAt ASC")
    fun members(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM expenses WHERE deleted = 0 ORDER BY createdAt DESC")
    fun expenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM splits")
    fun splits(): Flow<List<SplitEntity>>

    @Query("SELECT * FROM settlements WHERE deleted = 0 ORDER BY createdAt DESC")
    fun settlements(): Flow<List<SettlementEntity>>

    // ---- One-shot reads ----
    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun group(id: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE substr(id, 1, 8) = :tag LIMIT 1")
    suspend fun groupByTag(tag: String): GroupEntity?

    @Query("SELECT * FROM members WHERE groupId = :groupId")
    suspend fun membersOf(groupId: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun member(id: String): MemberEntity?

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun expense(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE groupId = :groupId")
    suspend fun allExpensesOf(groupId: String): List<ExpenseEntity>

    @Query("SELECT * FROM splits WHERE expenseId = :expenseId")
    suspend fun splitsOf(expenseId: String): List<SplitEntity>

    @Query("SELECT * FROM settlements WHERE id = :id")
    suspend fun settlement(id: String): SettlementEntity?

    @Query("SELECT * FROM settlements WHERE groupId = :groupId")
    suspend fun allSettlementsOf(groupId: String): List<SettlementEntity>

    @Query("SELECT * FROM expenses WHERE groupId = :groupId AND syncStatus != 'SYNCED'")
    suspend fun unsyncedExpenses(groupId: String): List<ExpenseEntity>

    @Query("SELECT * FROM settlements WHERE groupId = :groupId AND syncStatus != 'SYNCED'")
    suspend fun unsyncedSettlements(groupId: String): List<SettlementEntity>

    // ---- Writes ----
    @Upsert suspend fun upsertGroup(group: GroupEntity)
    @Upsert suspend fun upsertMember(member: MemberEntity)
    @Upsert suspend fun upsertExpenseRow(expense: ExpenseEntity)
    @Upsert suspend fun upsertSplits(splits: List<SplitEntity>)
    @Upsert suspend fun upsertSettlement(settlement: SettlementEntity)

    @Query("DELETE FROM splits WHERE expenseId = :expenseId")
    suspend fun deleteSplitsOf(expenseId: String)

    @Transaction
    suspend fun saveExpense(expense: ExpenseEntity, splits: List<SplitEntity>) {
        upsertExpenseRow(expense)
        deleteSplitsOf(expense.id)
        upsertSplits(splits)
    }

    @Query("UPDATE expenses SET syncStatus = :status WHERE id = :id")
    suspend fun setExpenseStatus(id: String, status: String)

    @Query("UPDATE settlements SET syncStatus = :status WHERE id = :id")
    suspend fun setSettlementStatus(id: String, status: String)

    @Query("UPDATE expenses SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun resetSyncingExpenses()

    @Query("UPDATE settlements SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun resetSyncingSettlements()

    @Query("UPDATE expenses SET syncStatus = 'PENDING' WHERE groupId = :groupId AND syncStatus = 'LOCAL'")
    suspend fun promoteLocalExpenses(groupId: String)

    @Query("UPDATE settlements SET syncStatus = 'PENDING' WHERE groupId = :groupId AND syncStatus = 'LOCAL'")
    suspend fun promoteLocalSettlements(groupId: String)

    @Query("DELETE FROM splits WHERE expenseId IN (SELECT id FROM expenses WHERE groupId = :groupId)")
    suspend fun deleteSplitsOfGroup(groupId: String)
    @Query("DELETE FROM expenses WHERE groupId = :groupId") suspend fun deleteExpensesOfGroup(groupId: String)
    @Query("DELETE FROM settlements WHERE groupId = :groupId") suspend fun deleteSettlementsOfGroup(groupId: String)
    @Query("DELETE FROM members WHERE groupId = :groupId") suspend fun deleteMembersOfGroup(groupId: String)
    @Query("DELETE FROM groups WHERE id = :groupId") suspend fun deleteGroupRow(groupId: String)

    @Transaction
    suspend fun deleteGroup(groupId: String) {
        deleteSplitsOfGroup(groupId)
        deleteExpensesOfGroup(groupId)
        deleteSettlementsOfGroup(groupId)
        deleteMembersOfGroup(groupId)
        deleteGroupRow(groupId)
    }
}
