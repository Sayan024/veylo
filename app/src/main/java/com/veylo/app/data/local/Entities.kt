package com.veylo.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

object SyncStatus {
    /** Only exists on this phone; the group has no other devices yet. */
    const val LOCAL = "LOCAL"
    /** Saved here, waiting for a connection to the group host. */
    const val PENDING = "PENDING"
    /** Sent, waiting for an acknowledgement. */
    const val SYNCING = "SYNCING"
    /** Acknowledged by the host / peers. */
    const val SYNCED = "SYNCED"
    /** Rejected or timed out. Will retry on next connection. */
    const val FAILED = "FAILED"
}

object GroupType {
    const val TRIP = "TRIP"
    const val OUTING = "OUTING"
}

@Serializable
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val destination: String,
    val currency: String,
    val coverUri: String? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val joinCode: String,
    val hostDeviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(tableName = "members", indices = [Index("groupId")])
data class MemberEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    /** Set once a real phone claims this member. Null = name-only member. */
    val deviceId: String? = null,
    val upiId: String? = null,
    val joinedAt: Long,
)

@Serializable
@Entity(tableName = "expenses", indices = [Index("groupId")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val title: String,
    val amount: Long,
    val payerId: String,
    val category: String,
    val splitMethod: String,
    val note: String = "",
    val receiptUri: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val createdByDevice: String,
    val deleted: Boolean = false,
    val syncStatus: String,
)

@Serializable
@Entity(tableName = "splits", primaryKeys = ["expenseId", "memberId"], indices = [Index("expenseId")])
data class SplitEntity(
    val expenseId: String,
    val memberId: String,
    val share: Long,
    /** Raw editor value (percent / weight / exact) so edits re-open the same way. */
    val input: Double = 0.0,
)

@Serializable
@Entity(tableName = "settlements", indices = [Index("groupId")])
data class SettlementEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val fromId: String,
    val toId: String,
    val amount: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val createdByDevice: String,
    val deleted: Boolean = false,
    val syncStatus: String,
)
