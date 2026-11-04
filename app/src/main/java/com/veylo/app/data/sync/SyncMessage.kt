package com.veylo.app.data.sync

import com.veylo.app.data.local.ExpenseEntity
import com.veylo.app.data.local.GroupEntity
import com.veylo.app.data.local.MemberEntity
import com.veylo.app.data.local.SettlementEntity
import com.veylo.app.data.local.SplitEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire protocol between phones. Every entity carries a UUID, so re-sending is idempotent. */
@Serializable
sealed interface SyncMessage

/** Member → host: "I want to join group [groupTag] with code [code]". */
@Serializable @SerialName("hello")
data class Hello(
    val groupTag: String,
    val code: String,
    val deviceId: String,
    val name: String,
    val upiId: String?,
) : SyncMessage

@Serializable @SerialName("reject")
data class Reject(val reason: String) : SyncMessage

/** Host → member: snapshot finished; you are [memberId]. */
@Serializable @SerialName("welcome")
data class Welcome(val groupId: String, val memberId: String) : SyncMessage

@Serializable @SerialName("group")
data class GroupMsg(val group: GroupEntity) : SyncMessage

@Serializable @SerialName("member")
data class MemberMsg(val member: MemberEntity) : SyncMessage

@Serializable @SerialName("expense")
data class ExpenseMsg(val expense: ExpenseEntity, val splits: List<SplitEntity>) : SyncMessage

@Serializable @SerialName("settlement")
data class SettlementMsg(val settlement: SettlementEntity) : SyncMessage

/** Receiver has stored version [updatedAt] of [entityId]. */
@Serializable @SerialName("ack")
data class Ack(val entityId: String, val updatedAt: Long) : SyncMessage

/** Host refused an event (failed validation). */
@Serializable @SerialName("nack")
data class Nack(val entityId: String, val reason: String) : SyncMessage
