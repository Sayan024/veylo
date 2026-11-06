package com.veylo.app.data.local

import androidx.room.ColumnInfo
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
    /** True once the group exists in VEYLO Cloud (Supabase). */
    @ColumnInfo(defaultValue = "0") val shared: Boolean = false,
    /** Secret proving this phone's cloud membership. Never sent over Bluetooth. */
    val cloudToken: String? = null,
    /** This phone's member id in the group (cloud-joined groups). */
    val myMemberId: String? = null,
    /** Server time of the last successful cloud pull. */
    val lastPulledAt: String? = null,
    /** Trip style: hills | beach | business | vacation | cafe. Drives cover art and colours. */
    val theme: String = "vacation",
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
    /** Last revision accepted by the cloud (0 = never uploaded). */
    @ColumnInfo(defaultValue = "0") val revision: Int = 0,
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
    @ColumnInfo(defaultValue = "0") val revision: Int = 0,
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
    @ColumnInfo(defaultValue = "0") val revision: Int = 0,
    /** cash | upi | other */
    val method: String = "cash",
    /** UPI transaction / bank reference (UTR), if known. */
    val txnId: String? = null,
    /** success | pending — only successful payments count towards balances. */
    val payStatus: String = "success",
    /** Local-only payment screenshot. Never synced. */
    val proofUri: String? = null,
)

/** Queued cloud upload. Read the latest local copy of the entity when it runs. */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    /** group | member | expense | settlement */
    val kind: String,
    val entityId: String,
    /** Cloud revision the local edit was based on (conflict detection). */
    val baseRevision: Int,
    val at: Long,
    val attempts: Int = 0,
)
