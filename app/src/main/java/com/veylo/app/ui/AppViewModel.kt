package com.veylo.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veylo.app.VeyloApp
import com.veylo.app.data.Profile
import com.veylo.app.data.local.ExpenseEntity
import com.veylo.app.data.local.GroupEntity
import com.veylo.app.data.local.MemberEntity
import com.veylo.app.data.local.SettlementEntity
import com.veylo.app.data.local.SplitEntity
import com.veylo.app.data.local.SyncStatus
import com.veylo.app.domain.LedgerExpense
import com.veylo.app.domain.LedgerPayment
import com.veylo.app.domain.SettlementCalculator
import com.veylo.app.domain.Transfer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything a screen needs to know about one group, pre-computed. */
data class GroupSummary(
    val group: GroupEntity,
    val members: List<MemberEntity>,
    val expenses: List<ExpenseEntity>,
    val splits: Map<String, List<SplitEntity>>,
    val settlements: List<SettlementEntity>,
    val balances: Map<String, Long>,
    val transfers: List<Transfer>,
    val me: MemberEntity?,
) {
    val currency get() = group.currency
    val totalSpent: Long get() = expenses.sumOf { it.amount }
    val myBalance: Long get() = me?.let { balances[it.id] } ?: 0L
    val unsyncedCount: Int get() =
        expenses.count { it.syncStatus != SyncStatus.SYNCED && it.syncStatus != SyncStatus.LOCAL } +
            settlements.count { it.syncStatus != SyncStatus.SYNCED && it.syncStatus != SyncStatus.LOCAL }
    val hasOtherDevices: Boolean get() = members.any { it.deviceId != null && it.id != me?.id }

    fun member(id: String) = members.firstOrNull { it.id == id }
    fun name(id: String): String = if (id == me?.id) "You" else member(id)?.name ?: "Someone"
    fun paidBy(id: String) = expenses.filter { it.payerId == id }.sumOf { it.amount }
}

data class ActivityItem(
    val id: String,
    val groupId: String,
    val groupName: String,
    val title: String,
    val subtitle: String,
    val amount: Long,
    val currency: String,
    val category: String?,
    val isSettlement: Boolean,
    val time: Long,
    val syncStatus: String,
)

data class AppData(
    val groups: List<GroupSummary>,
    val activity: List<ActivityItem>,
) {
    fun group(id: String?) = groups.firstOrNull { it.group.id == id }
    val mainCurrency: String get() =
        groups.groupingBy { it.currency }.eachCount().maxByOrNull { it.value }?.key ?: "INR"
    val youOwe: Long get() = groups.filter { it.currency == mainCurrency }.sumOf { minOf(it.myBalance, 0L) } * -1
    val owedToYou: Long get() = groups.filter { it.currency == mainCurrency }.sumOf { maxOf(it.myBalance, 0L) }
    val net: Long get() = owedToYou - youOwe
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as VeyloApp).container
    val repo = c.repository
    val sync = c.sync
    val profile: StateFlow<Profile> = c.profiles.profile
    val syncState = sync.state
    val cloud = c.cloud
    val cloudState = cloud.state

    private val dao = repo.dao

    val data: StateFlow<AppData?> = combine(
        combine(dao.groups(), dao.members(), dao.expenses()) { g, m, e -> Triple(g, m, e) },
        dao.splits(),
        dao.settlements(),
        profile,
    ) { (groups, members, expenses), splits, settlements, profile ->
        build(groups, members, expenses, splits, settlements, profile)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun build(
        groups: List<GroupEntity>,
        members: List<MemberEntity>,
        expenses: List<ExpenseEntity>,
        splits: List<SplitEntity>,
        settlements: List<SettlementEntity>,
        profile: Profile,
    ): AppData {
        val splitsByExpense = splits.groupBy { it.expenseId }
        val summaries = groups.map { g ->
            val gm = members.filter { it.groupId == g.id }
            val ge = expenses.filter { it.groupId == g.id }
            val gs = settlements.filter { it.groupId == g.id }
            val balances = SettlementCalculator.balances(
                gm.map { it.id },
                ge.map { e -> LedgerExpense(e.payerId, e.amount, splitsByExpense[e.id].orEmpty().associate { it.memberId to it.share }) },
                // Pending UPI payments don't count until confirmed.
                gs.filter { it.payStatus == "success" }.map { LedgerPayment(it.fromId, it.toId, it.amount) },
            )
            GroupSummary(
                group = g, members = gm, expenses = ge,
                splits = ge.associate { it.id to splitsByExpense[it.id].orEmpty() },
                settlements = gs, balances = balances,
                transfers = SettlementCalculator.minimize(balances),
                me = gm.firstOrNull { it.id == g.myMemberId } ?: gm.firstOrNull { it.deviceId == profile.deviceId },
            )
        }
        val activity = summaries.flatMap { s ->
            s.expenses.map { e ->
                ActivityItem(
                    e.id, s.group.id, s.group.name, e.title,
                    "${s.name(e.payerId)} paid · ${s.group.name}", e.amount, s.currency,
                    e.category, false, e.createdAt, e.syncStatus,
                )
            } + s.settlements.map { st ->
                ActivityItem(
                    st.id, s.group.id, s.group.name, "${s.name(st.fromId)} → ${s.name(st.toId)}",
                    "Settlement · ${s.group.name}", st.amount, s.currency, null, true, st.createdAt, st.syncStatus,
                )
            }
        }.sortedByDescending { it.time }
        return AppData(summaries, activity)
    }

    // ---------------- Actions ----------------

    fun finishOnboarding(name: String) = c.profiles.update { it.copy(name = name.trim(), onboarded = true) }
    fun updateProfile(name: String, upi: String) = c.profiles.update { it.copy(name = name.trim(), upiId = upi.trim()) }
    fun setTheme(theme: String) = c.profiles.update { it.copy(theme = theme) }

    fun createGroup(
        name: String, type: String, destination: String, currency: String, cover: Uri?,
        start: Long?, end: Long?, members: List<String>, theme: String, onDone: (String) -> Unit,
    ) = viewModelScope.launch {
        val coverUri = cover?.let { repo.importImage(it) }
        val id = repo.createGroup(name, type, destination, currency, coverUri, start, end, members, theme)
        onDone(id)
    }

    fun changeCover(groupId: String, uri: Uri) = viewModelScope.launch {
        repo.updateGroupCover(groupId, repo.importImage(uri))
    }

    fun addMember(groupId: String, name: String) = viewModelScope.launch { repo.addMember(groupId, name) }

    fun leaveGroup(groupId: String) = viewModelScope.launch {
        if (syncState.value.groupId == groupId) sync.stop()
        repo.leaveGroup(groupId)
    }

    fun saveExpense(
        existingId: String?, groupId: String, title: String, amount: Long, payerId: String,
        category: String, method: String, receipt: String?, shares: Map<String, Long>,
        inputs: Map<String, Double>, onDone: () -> Unit,
    ) = viewModelScope.launch {
        repo.saveExpense(existingId, groupId, title, amount, payerId, category, method, receipt, shares, inputs)
        onDone()
    }

    suspend fun importImage(uri: Uri): String? = repo.importImage(uri)

    fun deleteExpense(id: String) = viewModelScope.launch { repo.deleteExpense(id) }

    fun settle(
        groupId: String, from: String, to: String, amount: Long,
        method: String = "cash", txnId: String? = null, payStatus: String = "success", proof: Uri? = null,
    ) = viewModelScope.launch {
        val proofUri = proof?.let { repo.importImage(it) }
        repo.recordSettlement(groupId, from, to, amount, method, txnId, payStatus, proofUri)
    }

    fun confirmSettlement(id: String) = viewModelScope.launch { repo.confirmSettlement(id) }
    fun setTheme(groupId: String, theme: String) = viewModelScope.launch { repo.setTheme(groupId, theme) }

    fun deleteSettlement(id: String) = viewModelScope.launch { repo.deleteSettlement(id) }

    // ---------------- Cloud ----------------

    fun shareOnline(groupId: String, onResult: (Result<String>) -> Unit) = viewModelScope.launch {
        onResult(runCatching { cloud.shareGroup(groupId) })
    }

    fun previewInvite(code: String, onResult: (Result<com.veylo.app.data.cloud.CloudSync.InvitePreview>) -> Unit) = viewModelScope.launch {
        onResult(runCatching { cloud.preview(code) })
    }

    fun joinOnline(code: String, memberId: String?, onResult: (Result<String>) -> Unit) = viewModelScope.launch {
        onResult(runCatching { cloud.join(code, memberId) })
    }

    fun syncCloudNow() = viewModelScope.launch { cloud.syncNow() }

    fun deleteAllData() = viewModelScope.launch {
        sync.stop()
        data.value?.groups?.forEach { repo.leaveGroup(it.group.id) }
    }
}
