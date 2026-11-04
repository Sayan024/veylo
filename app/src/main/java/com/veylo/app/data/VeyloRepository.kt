package com.veylo.app.data

import android.content.Context
import android.net.Uri
import com.veylo.app.data.local.ExpenseEntity
import com.veylo.app.data.local.GroupEntity
import com.veylo.app.data.local.MemberEntity
import com.veylo.app.data.local.SettlementEntity
import com.veylo.app.data.local.SplitEntity
import com.veylo.app.data.local.SyncStatus
import com.veylo.app.data.local.VeyloDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Something changed on this phone that other phones should hear about. */
sealed interface LocalChange {
    val groupId: String
    data class Expense(override val groupId: String, val id: String) : LocalChange
    data class Settlement(override val groupId: String, val id: String) : LocalChange
    data class Member(override val groupId: String, val id: String) : LocalChange
}

class VeyloRepository(
    private val context: Context,
    val dao: VeyloDao,
    private val profiles: ProfileStore,
) {
    private val _changes = MutableSharedFlow<LocalChange>(extraBufferCapacity = 64)
    val changes: SharedFlow<LocalChange> = _changes

    private val me get() = profiles.current

    // ---------------- Groups & members ----------------

    suspend fun createGroup(
        name: String,
        type: String,
        destination: String,
        currency: String,
        coverUri: String?,
        startDate: Long?,
        endDate: Long?,
        otherMembers: List<String>,
    ): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.upsertGroup(
            GroupEntity(
                id = id, name = name.trim(), type = type, destination = destination.trim(),
                currency = currency, coverUri = coverUri, startDate = startDate, endDate = endDate,
                joinCode = newJoinCode(), hostDeviceId = me.deviceId, createdAt = now, updatedAt = now,
            )
        )
        dao.upsertMember(
            MemberEntity(UUID.randomUUID().toString(), id, me.name.ifBlank { "You" }, me.deviceId, me.upiId.ifBlank { null }, now)
        )
        otherMembers.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEachIndexed { i, n ->
            dao.upsertMember(MemberEntity(UUID.randomUUID().toString(), id, n, null, null, now + i + 1))
        }
        return id
    }

    suspend fun updateGroupCover(groupId: String, coverUri: String?) {
        val g = dao.group(groupId) ?: return
        dao.upsertGroup(g.copy(coverUri = coverUri))
    }

    suspend fun addMember(groupId: String, name: String) {
        val m = MemberEntity(UUID.randomUUID().toString(), groupId, name.trim(), null, null, System.currentTimeMillis())
        dao.upsertMember(m)
        _changes.emit(LocalChange.Member(groupId, m.id))
    }

    /** Removes the group from this phone only. */
    suspend fun leaveGroup(groupId: String) = dao.deleteGroup(groupId)

    // ---------------- Expenses ----------------

    suspend fun saveExpense(
        existingId: String?,
        groupId: String,
        title: String,
        amount: Long,
        payerId: String,
        category: String,
        splitMethod: String,
        receiptUri: String?,
        shares: Map<String, Long>,
        inputs: Map<String, Double>,
    ): String {
        val now = System.currentTimeMillis()
        val old = existingId?.let { dao.expense(it) }
        val id = old?.id ?: UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            id = id, groupId = groupId, title = title.trim(), amount = amount, payerId = payerId,
            category = category, splitMethod = splitMethod, receiptUri = receiptUri,
            createdAt = old?.createdAt ?: now, updatedAt = now,
            createdByDevice = old?.createdByDevice ?: me.deviceId,
            syncStatus = initialStatus(groupId),
        )
        // Saved locally first, then broadcast.
        dao.saveExpense(expense, shares.map { (m, s) -> SplitEntity(id, m, s, inputs[m] ?: 0.0) })
        _changes.emit(LocalChange.Expense(groupId, id))
        return id
    }

    suspend fun deleteExpense(id: String) {
        val e = dao.expense(id) ?: return
        dao.upsertExpenseRow(
            e.copy(deleted = true, updatedAt = System.currentTimeMillis(), syncStatus = initialStatus(e.groupId))
        )
        _changes.emit(LocalChange.Expense(e.groupId, id))
    }

    // ---------------- Settlements ----------------

    suspend fun recordSettlement(groupId: String, fromId: String, toId: String, amount: Long) {
        val now = System.currentTimeMillis()
        val s = SettlementEntity(
            UUID.randomUUID().toString(), groupId, fromId, toId, amount, now, now,
            me.deviceId, syncStatus = initialStatus(groupId),
        )
        dao.upsertSettlement(s)
        _changes.emit(LocalChange.Settlement(groupId, s.id))
    }

    suspend fun deleteSettlement(id: String) {
        val s = dao.settlement(id) ?: return
        dao.upsertSettlement(
            s.copy(deleted = true, updatedAt = System.currentTimeMillis(), syncStatus = initialStatus(s.groupId))
        )
        _changes.emit(LocalChange.Settlement(s.groupId, id))
    }

    // ---------------- Images ----------------

    /** Copies a picked image into app storage so it survives restarts. */
    suspend fun importImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: return@withContext null
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            null
        }
    }

    // ---------------- Helpers ----------------

    /** LOCAL if no other phone is in the group yet; otherwise PENDING until acknowledged. */
    private suspend fun initialStatus(groupId: String): String {
        val others = dao.membersOf(groupId).any { it.deviceId != null && it.deviceId != me.deviceId }
        val g = dao.group(groupId)
        val joined = g != null && g.hostDeviceId != me.deviceId
        return if (others || joined) SyncStatus.PENDING else SyncStatus.LOCAL
    }

    private fun newJoinCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = java.security.SecureRandom()
        return (1..6).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }
}
