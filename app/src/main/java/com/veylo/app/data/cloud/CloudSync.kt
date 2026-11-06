package com.veylo.app.data.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.veylo.app.data.LocalChange
import com.veylo.app.data.ProfileStore
import com.veylo.app.data.local.ExpenseEntity
import com.veylo.app.data.local.GroupEntity
import com.veylo.app.data.local.GroupType
import com.veylo.app.data.local.MemberEntity
import com.veylo.app.data.local.OutboxEntity
import com.veylo.app.data.local.SettlementEntity
import com.veylo.app.data.local.SplitEntity
import com.veylo.app.data.local.SyncStatus
import com.veylo.app.data.local.VeyloDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Internet sync through VEYLO Cloud (Supabase) — the same groups the web app uses.
 *
 * Works next to Bluetooth sync: every local change (and every change received over
 * Bluetooth) is queued in the `outbox` table and uploaded when online. Each upload
 * carries the revision it was based on; the server rejects stale edits and we adopt
 * the server copy. Then we pull everything changed since the last pull.
 */
class CloudSync(
    context: Context,
    private val dao: VeyloDao,
    private val profiles: ProfileStore,
    private val api: CloudApi,
    changes: SharedFlow<LocalChange>,
    private val scope: CoroutineScope,
    /** Public web address used in invite links / QR codes. */
    val webUrl: String,
) {
    data class State(
        val enabled: Boolean,
        val online: Boolean = true,
        val syncing: Boolean = false,
        val lastSyncAt: Long? = null,
        val error: String? = null,
    )

    data class InvitePreview(
        val groupId: String,
        val name: String,
        val type: String,
        val destination: String,
        val currency: String,
        val members: List<InviteMember>,
    )
    data class InviteMember(val id: String, val name: String, val claimed: Boolean)

    private val _state = MutableStateFlow(State(enabled = api.enabled))
    val state: StateFlow<State> = _state
    val enabled get() = api.enabled

    private val mutex = Mutex()
    private var scheduled: Job? = null

    init {
        if (api.enabled) {
            scope.launch { changes.collect { onLocalChange(it) } }
            watchConnectivity(context)
            scope.launch {
                while (true) {
                    delay(15_000)
                    val foreground = withContext(Dispatchers.Main) {
                        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    }
                    if (foreground) syncNow()
                }
            }
            schedule(1_500)
        }
    }

    fun inviteLink(code: String) = "${webUrl.trimEnd('/')}/join?code=$code"

    // ================================================================= Queue

    private suspend fun enqueue(groupId: String, kind: String, entityId: String, baseRevision: Int) {
        if (!api.enabled) return
        val g = dao.group(groupId) ?: return
        if (!g.shared && kind != "group") return // not a cloud group (yet)
        if (dao.outboxFor(kind, entityId) != null) return // queued op reads the latest copy when it runs
        dao.upsertOutbox(OutboxEntity(UUID.randomUUID().toString(), groupId, kind, entityId, baseRevision, System.currentTimeMillis()))
    }

    private suspend fun onLocalChange(c: LocalChange) {
        when (c) {
            is LocalChange.Expense -> dao.expense(c.id)?.let { enqueue(c.groupId, "expense", it.id, it.revision) }
            is LocalChange.Settlement -> dao.settlement(c.id)?.let { enqueue(c.groupId, "settlement", it.id, it.revision) }
            is LocalChange.Member -> dao.member(c.id)?.let { enqueue(c.groupId, "member", it.id, it.revision) }
        }
        schedule()
    }

    /** Called by Bluetooth sync after it stores something from another phone, so it reaches the cloud too. */
    fun onReceivedOverBluetooth(kind: String, groupId: String, entityId: String) {
        if (!api.enabled) return
        scope.launch {
            val rev = when (kind) {
                "expense" -> dao.expense(entityId)?.revision
                "settlement" -> dao.settlement(entityId)?.revision
                else -> dao.member(entityId)?.revision
            } ?: return@launch
            enqueue(groupId, kind, entityId, rev)
            schedule()
        }
    }

    fun schedule(ms: Long = 700) {
        if (!api.enabled) return
        scheduled?.cancel()
        scheduled = scope.launch {
            delay(ms)
            syncNow()
        }
    }

    // ================================================================= Public actions

    /** Uploads a local group so others can join it online. Returns the invite code. */
    suspend fun shareGroup(groupId: String): String {
        enqueue(groupId, "group", groupId, 0)
        syncNow(throwErrors = true)
        return dao.group(groupId)?.takeIf { it.shared }?.joinCode ?: throw CloudException("Couldn't upload — check your internet", true)
    }

    suspend fun preview(code: String): InvitePreview {
        val r = api.rpc("preview_invite", buildJsonObject { put("p_code", code.uppercase().trim()) }).jsonObject
        return InvitePreview(
            groupId = r.str("group_id")!!, name = r.str("name") ?: "", type = r.str("type") ?: "trip",
            destination = r.str("destination") ?: "", currency = r.str("currency") ?: "INR",
            members = r["members"]?.jsonArray.orEmpty().map {
                val o = it.jsonObject
                InviteMember(o.str("id")!!, o.str("name") ?: "", o["claimed"]?.jsonPrimitive?.booleanOrNull ?: false)
            },
        )
    }

    /** Joins an online group (optionally claiming a listed member). Returns the local group id. */
    suspend fun join(code: String, claimMemberId: String?): String {
        val p = profiles.current
        val r = api.rpc("join_group", buildJsonObject {
            put("p_code", code.uppercase().trim())
            put("p_name", p.name.ifBlank { "Friend" })
            put("p_device", p.deviceId)
            put("p_upi", p.upiId.ifBlank { null })
            put("p_member_id", claimMemberId)
        }).jsonObject
        val gid = r.str("group_id")!!
        val token = r.str("member_token")!!
        val myId = r.str("member_id")!!
        val existing = dao.group(gid)
        val now = System.currentTimeMillis()
        val g = existing?.copy(shared = true, cloudToken = token, myMemberId = myId, joinCode = code.uppercase())
            ?: GroupEntity(
                id = gid, name = "…", type = GroupType.TRIP, destination = "", currency = "INR",
                joinCode = code.uppercase(), hostDeviceId = "", createdAt = now, updatedAt = now,
                shared = true, cloudToken = token, myMemberId = myId,
            )
        dao.upsertGroup(g)
        mutex.withLock { pull(g, full = true) }
        // Make sure my member row carries this phone's id locally (the server hides device ids).
        dao.member(myId)?.let { dao.upsertMember(it.copy(deviceId = p.deviceId)) }
        schedule(300)
        return gid
    }

    // ================================================================= Sync loop

    suspend fun syncNow(throwErrors: Boolean = false) {
        if (!api.enabled) return
        mutex.withLock {
            _state.update { it.copy(syncing = true) }
            try {
                val touched = mutableSetOf<String>()
                push(touched)
                for (g in dao.sharedGroups()) {
                    try {
                        pull(g)
                    } catch (e: CloudException) {
                        if (e.network) throw e
                        Log.w(TAG, "pull ${g.id} refused: ${e.message}")
                    }
                }
                touched.forEach { api.ping(it) }
                _state.update { it.copy(syncing = false, lastSyncAt = System.currentTimeMillis(), error = null) }
            } catch (e: CloudException) {
                _state.update { it.copy(syncing = false, error = e.message) }
                if (throwErrors) throw e
            } catch (e: Exception) {
                Log.e(TAG, "sync failed", e)
                _state.update { it.copy(syncing = false, error = e.message) }
                if (throwErrors) throw CloudException(e.message ?: "Sync failed", false)
            }
        }
    }

    private suspend fun push(touched: MutableSet<String>) {
        val blocked = mutableSetOf<String>()
        for (op in dao.outbox()) {
            if (op.groupId in blocked) continue
            val g = dao.group(op.groupId)
            if (g == null) {
                dao.deleteOutbox(op.id); continue
            }
            if (op.kind != "group" && (!g.shared || g.cloudToken == null)) {
                blocked += op.groupId; continue
            }
            try {
                pushOne(op, g)
                dao.deleteOutbox(op.id)
                touched += op.groupId
            } catch (e: CloudException) {
                if (e.network) {
                    dao.upsertOutbox(op.copy(attempts = op.attempts + 1))
                    throw e // offline: stop and retry later
                }
                // Refused by the server (validation/permission). Keep the data, mark it failed.
                Log.w(TAG, "push ${op.kind} ${op.entityId} refused: ${e.message}")
                when (op.kind) {
                    "expense" -> dao.setExpenseStatus(op.entityId, SyncStatus.FAILED)
                    "settlement" -> dao.setSettlementStatus(op.entityId, SyncStatus.FAILED)
                }
                dao.deleteOutbox(op.id)
                if (op.kind == "group") blocked += op.groupId
            }
        }
    }

    private suspend fun pushOne(op: OutboxEntity, g: GroupEntity) {
        val me = profiles.current
        when (op.kind) {
            "group" -> {
                if (g.shared) return
                val members = dao.membersOf(g.id)
                val r = api.rpc("create_group", buildJsonObject {
                    put("p_group", buildJsonObject {
                        put("id", g.id); put("name", g.name); put("type", g.type.lowercase())
                        put("destination", g.destination); put("currency", g.currency)
                        put("start_date", g.startDate?.let { isoDate(it) }); put("end_date", g.endDate?.let { isoDate(it) })
                        put("theme", g.theme)
                    })
                    put("p_members", buildJsonArray { members.forEach { add(memberJson(it)) } })
                    put("p_device", me.deviceId)
                }).jsonObject
                val myId = r.str("member_id")!!
                dao.upsertGroup(g.copy(shared = true, cloudToken = r.str("member_token"), joinCode = r.str("invite_code")!!, myMemberId = myId))
                members.forEach { dao.upsertMember(it.copy(revision = 1)) }
                // Upload everything that already exists in this group.
                dao.allExpensesOf(g.id).filter { !it.deleted }.forEach { enqueue(g.id, "expense", it.id, 0) }
                dao.allSettlementsOf(g.id).filter { !it.deleted }.forEach { enqueue(g.id, "settlement", it.id, 0) }
            }

            "member" -> {
                val m = dao.member(op.entityId) ?: return
                val r = api.rpc("push_member", buildJsonObject {
                    put("p_token", g.cloudToken); put("p_member", memberJson(m)); put("p_base_revision", op.baseRevision)
                }).jsonObject
                if (r.str("status") == "ok") dao.upsertMember(m.copy(revision = r.int("revision") ?: m.revision))
                else r["server"]?.jsonObject?.let { applyMember(g, it) }
            }

            "expense" -> {
                val e = dao.expense(op.entityId) ?: return
                val splits = dao.splitsOf(e.id)
                if (e.syncStatus != SyncStatus.SYNCED) dao.setExpenseStatus(e.id, SyncStatus.SYNCING)
                val r = api.rpc("push_expense", buildJsonObject {
                    put("p_token", g.cloudToken); put("p_expense", expenseJson(e, splits, g)); put("p_base_revision", op.baseRevision)
                }).jsonObject
                val latest = dao.expense(e.id)
                if (r.str("status") == "ok") {
                    val rev = r.int("revision") ?: e.revision
                    val editedMeanwhile = latest != null && latest.updatedAt != e.updatedAt
                    latest?.let { dao.upsertExpenseRow(it.copy(revision = rev, syncStatus = if (editedMeanwhile) SyncStatus.PENDING else SyncStatus.SYNCED)) }
                    if (editedMeanwhile) {
                        dao.deleteOutbox(op.id)
                        dao.upsertOutbox(OutboxEntity(UUID.randomUUID().toString(), g.id, "expense", e.id, rev, System.currentTimeMillis()))
                    }
                } else {
                    r["server"]?.jsonObject?.let { applyExpense(g, it, force = true) }
                }
            }

            "settlement" -> {
                val s = dao.settlement(op.entityId) ?: return
                val r = api.rpc("push_settlement", buildJsonObject {
                    put("p_token", g.cloudToken); put("p_settlement", settlementJson(s, g)); put("p_base_revision", op.baseRevision)
                }).jsonObject
                if (r.str("status") == "ok") {
                    dao.settlement(s.id)?.let { dao.upsertSettlement(it.copy(revision = r.int("revision") ?: it.revision, syncStatus = SyncStatus.SYNCED)) }
                } else {
                    r["server"]?.jsonObject?.let { applySettlement(it, force = true) }
                }
            }
        }
    }

    private suspend fun pull(g: GroupEntity, full: Boolean = false) {
        val token = g.cloudToken ?: return
        val since = if (full || g.lastPulledAt == null) null
        else runCatching { OffsetDateTime.parse(g.lastPulledAt).minusSeconds(10).toString() }.getOrNull()
        val r = api.rpc("pull_group", buildJsonObject { put("p_token", token); put("p_since", since) }).jsonObject

        r["group"]?.jsonObject?.let { sg ->
            val cur = dao.group(g.id) ?: g
            dao.upsertGroup(
                cur.copy(
                    name = sg.str("name") ?: cur.name,
                    type = (sg.str("type") ?: "trip").uppercase(),
                    destination = sg.str("destination") ?: "",
                    currency = sg.str("currency") ?: cur.currency,
                    startDate = sg.str("start_date")?.let { dateMillis(it) },
                    endDate = sg.str("end_date")?.let { dateMillis(it) },
                    joinCode = sg.str("invite_code") ?: cur.joinCode,
                    // Take the server's style only for a group we just joined; keep local choices otherwise.
                    theme = if (cur.name == "…") sg.str("theme") ?: cur.theme else cur.theme,
                    lastPulledAt = r.str("now"),
                )
            )
        }
        r["members"]?.jsonArray.orEmpty().forEach { applyMember(g, it.jsonObject) }
        r["expenses"]?.jsonArray.orEmpty().forEach { applyExpense(g, it.jsonObject) }
        r["settlements"]?.jsonArray.orEmpty().forEach { applySettlement(it.jsonObject) }
    }

    // ================================================================= Merge

    private suspend fun applyMember(g: GroupEntity, o: JsonObject) {
        val id = o.str("id") ?: return
        val rev = o.int("revision") ?: 1
        val local = dao.member(id)
        if (local != null && dao.outboxFor("member", id) != null && local.revision >= rev) return
        val serverDevice = o.str("device_id") // "linked" or null — real ids are hidden by the server
        dao.upsertMember(
            MemberEntity(
                id = id, groupId = g.id, name = o.str("name") ?: local?.name ?: "",
                deviceId = local?.deviceId ?: serverDevice,
                upiId = o.str("upi_id"),
                joinedAt = o.str("joined_at")?.let { millis(it) } ?: local?.joinedAt ?: System.currentTimeMillis(),
                revision = rev,
            )
        )
    }

    private suspend fun applyExpense(g: GroupEntity, o: JsonObject, force: Boolean = false) {
        val id = o.str("id") ?: return
        val rev = o.int("revision") ?: 1
        val local = dao.expense(id)
        if (!force && local != null) {
            if (dao.outboxFor("expense", id) != null && local.revision >= rev) return // our pending edit wins until pushed
            if (local.revision == rev && local.syncStatus == SyncStatus.SYNCED) return
        }
        val e = ExpenseEntity(
            id = id, groupId = g.id, title = o.str("title") ?: "", amount = o.long("amount") ?: 0,
            payerId = o.str("payer_id") ?: return, category = (o.str("category") ?: "other").uppercase(),
            splitMethod = (o.str("split_method") ?: "equal").uppercase(), note = o.str("notes") ?: "",
            receiptUri = local?.receiptUri,
            createdAt = o.str("created_at")?.let { millis(it) } ?: local?.createdAt ?: System.currentTimeMillis(),
            updatedAt = o.str("updated_at")?.let { millis(it) } ?: System.currentTimeMillis(),
            createdByDevice = local?.createdByDevice ?: "cloud",
            deleted = o["deleted"]?.jsonPrimitive?.booleanOrNull ?: false,
            syncStatus = SyncStatus.SYNCED, revision = rev,
        )
        val splits = o["shares"]?.jsonArray.orEmpty().map {
            val s = it.jsonObject
            SplitEntity(id, s.str("member_id")!!, s.long("share") ?: 0, s["input"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0)
        }
        dao.saveExpense(e, splits)
    }

    private suspend fun applySettlement(o: JsonObject, force: Boolean = false) {
        val id = o.str("id") ?: return
        val rev = o.int("revision") ?: 1
        val local = dao.settlement(id)
        if (!force && local != null) {
            if (dao.outboxFor("settlement", id) != null && local.revision >= rev) return
            if (local.revision == rev && local.syncStatus == SyncStatus.SYNCED) return
        }
        dao.upsertSettlement(
            SettlementEntity(
                id = id, groupId = o.str("group_id") ?: return, fromId = o.str("from_id")!!, toId = o.str("to_id")!!,
                amount = o.long("amount") ?: 0,
                createdAt = o.str("created_at")?.let { millis(it) } ?: System.currentTimeMillis(),
                updatedAt = o.str("updated_at")?.let { millis(it) } ?: System.currentTimeMillis(),
                createdByDevice = local?.createdByDevice ?: "cloud",
                deleted = o["deleted"]?.jsonPrimitive?.booleanOrNull ?: false,
                syncStatus = SyncStatus.SYNCED, revision = rev,
                method = o.str("method") ?: "cash", txnId = o.str("txn_id"), payStatus = o.str("pay_status") ?: "success",
                proofUri = local?.proofUri,
            )
        )
    }

    // ================================================================= JSON mapping

    private fun memberJson(m: MemberEntity) = buildJsonObject {
        put("id", m.id); put("name", m.name)
        // Only real device ids are sent; "linked" is a server placeholder.
        put("device_id", m.deviceId?.takeIf { it != "linked" }); put("upi_id", m.upiId)
    }

    private suspend fun expenseJson(e: ExpenseEntity, splits: List<SplitEntity>, g: GroupEntity) = buildJsonObject {
        put("id", e.id); put("group_id", e.groupId); put("title", e.title); put("amount", e.amount)
        put("currency", g.currency); put("payer_id", e.payerId); put("category", e.category.lowercase())
        put("date", isoDate(e.createdAt)); put("notes", e.note); put("split_method", e.splitMethod.lowercase())
        put("shares", buildJsonArray {
            splits.forEach { s -> add(buildJsonObject { put("member_id", s.memberId); put("share", s.share); put("input", s.input) }) }
        })
        put("created_by", myMemberId(g)); put("created_at", Instant.ofEpochMilli(e.createdAt).toString()); put("deleted", e.deleted)
    }

    private suspend fun settlementJson(s: SettlementEntity, g: GroupEntity) = buildJsonObject {
        put("id", s.id); put("group_id", s.groupId); put("from_id", s.fromId); put("to_id", s.toId)
        put("amount", s.amount); put("created_by", myMemberId(g)); put("created_at", Instant.ofEpochMilli(s.createdAt).toString())
        put("deleted", s.deleted)
        put("method", s.method); put("txn_id", s.txnId); put("pay_status", s.payStatus)
    }

    private suspend fun myMemberId(g: GroupEntity): String? =
        g.myMemberId ?: dao.membersOf(g.id).firstOrNull { it.deviceId == profiles.current.deviceId }?.id

    // ================================================================= Connectivity

    private fun watchConnectivity(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val online = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _state.update { it.copy(online = online) }
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _state.update { it.copy(online = true) }
                schedule(500)
            }
            override fun onLost(network: Network) {
                _state.update { it.copy(online = false) }
            }
        })
    }

    companion object {
        private const val TAG = "VeyloCloud"
        private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
        private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
        private fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() }

        fun isoDate(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        fun dateMillis(d: String): Long? = runCatching { LocalDate.parse(d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
        fun millis(ts: String): Long? = runCatching { OffsetDateTime.parse(ts).toInstant().toEpochMilli() }.getOrNull()
    }
}
