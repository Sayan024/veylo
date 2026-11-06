package com.veylo.app.data.sync

import android.Manifest
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.veylo.app.data.LocalChange
import com.veylo.app.data.ProfileStore
import com.veylo.app.data.local.MemberEntity
import com.veylo.app.data.local.SyncStatus
import com.veylo.app.data.local.VeyloDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Offline group sync over Google Nearby Connections (Bluetooth / BLE / Wi-Fi).
 *
 * Topology: one host phone per group (star). The host holds the authoritative ledger,
 * validates every event, acknowledges it and relays it to all other members.
 * Every phone keeps a full local copy in Room, so nothing is lost while disconnected.
 *
 * An item is only marked SYNCED after an [Ack] for that exact version arrives.
 */
class SyncEngine(
    context: Context,
    private val dao: VeyloDao,
    private val profiles: ProfileStore,
    changes: SharedFlow<LocalChange>,
    private val scope: CoroutineScope,
    /** Lets cloud sync upload things that arrived over Bluetooth. (kind, groupId, entityId) */
    private val onReceived: (String, String, String) -> Unit = { _, _, _ -> },
) {
    enum class Role { IDLE, HOST, SEARCHING, CONNECTING, MEMBER }

    data class Peer(val endpointId: String, val name: String, val verified: Boolean = false, val memberId: String? = null)
    data class FoundGroup(val endpointId: String, val groupName: String, val tag: String)

    data class State(
        val role: Role = Role.IDLE,
        val groupId: String? = null,
        val peers: List<Peer> = emptyList(),
        val found: List<FoundGroup> = emptyList(),
        val message: String? = null,
        val error: String? = null,
    ) {
        val connectedCount get() = peers.count { it.verified }
    }

    private val client = Nearby.getConnectionsClient(context)
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "t"; encodeDefaults = true }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** entityId → endpoints we are still waiting on for an Ack. */
    private val awaiting = ConcurrentHashMap<String, MutableSet<String>>()

    @Volatile private var hostEndpoint: String? = null
    @Volatile private var targetTag: String? = null
    @Volatile private var pendingCode: String? = null
    @Volatile private var userStopped = true

    /** All work runs through one queue, in order, so snapshots arrive consistently. */
    private sealed interface Work {
        data class In(val endpoint: String, val msg: SyncMessage) : Work
        data class Local(val change: LocalChange) : Work
        data class Gone(val endpoint: String) : Work
        data class Timeout(val entityId: String) : Work
    }
    private val work = Channel<Work>(Channel.UNLIMITED)

    init {
        scope.launch {
            dao.resetSyncingExpenses()
            dao.resetSyncingSettlements()
        }
        scope.launch {
            for (w in work) {
                try {
                    process(w)
                } catch (e: Exception) {
                    Log.e(TAG, "sync work failed", e)
                }
            }
        }
        scope.launch { changes.collect { work.send(Work.Local(it)) } }
    }

    // ======================= Public controls =======================

    /** Make this phone visible as the host of [groupId]. */
    fun host(groupId: String) {
        scope.launch {
            val g = dao.group(groupId) ?: return@launch
            stopInternal()
            userStopped = false
            _state.value = State(role = Role.HOST, groupId = groupId, message = "Starting…")
            val name = "${g.name.take(24)}#${g.id.take(8)}"
            client.startAdvertising(
                name, SERVICE_ID, lifecycle,
                AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build(),
            ).addOnSuccessListener {
                _state.update { it.copy(message = "Visible to nearby phones") }
            }.addOnFailureListener { e ->
                _state.update { it.copy(role = Role.IDLE, message = null, error = friendly(e)) }
            }
        }
    }

    /** Look for nearby hosts. Pass [rejoinGroupId] to reconnect to a group you already joined. */
    fun search(rejoinGroupId: String? = null) {
        scope.launch {
            stopInternal()
            userStopped = false
            if (rejoinGroupId != null) {
                val g = dao.group(rejoinGroupId)
                targetTag = g?.id?.take(8)
                pendingCode = g?.joinCode
            } else {
                targetTag = null
                pendingCode = null
            }
            _state.value = State(role = Role.SEARCHING, groupId = rejoinGroupId, message = "Looking for nearby groups…")
            startDiscovery()
        }
    }

    /** From a scanned QR invite: remember tag + code, connect as soon as the host is seen. */
    fun useInvite(tag: String, code: String) {
        targetTag = tag
        pendingCode = code.uppercase().trim()
        _state.value.found.firstOrNull { it.tag == tag }?.let { connect(it.endpointId, code) }
    }

    fun connect(endpointId: String, code: String) {
        val found = _state.value.found.firstOrNull { it.endpointId == endpointId }
        if (found != null) targetTag = found.tag
        pendingCode = code.uppercase().trim()
        _state.update { it.copy(role = Role.CONNECTING, message = "Connecting to ${found?.groupName ?: "host"}…", error = null) }
        client.requestConnection(profiles.current.name.ifBlank { "VEYLO user" }, endpointId, lifecycle)
            .addOnFailureListener { e ->
                _state.update { it.copy(role = Role.SEARCHING, message = "Looking for nearby groups…", error = friendly(e)) }
            }
    }

    fun stop() {
        scope.launch {
            userStopped = true
            stopInternal()
            _state.value = State()
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ======================= Nearby callbacks =======================

    private fun startDiscovery() {
        client.startDiscovery(
            SERVICE_ID, discovery,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build(),
        ).addOnFailureListener { e ->
            _state.update { it.copy(role = Role.IDLE, message = null, error = friendly(e)) }
        }
    }

    private suspend fun stopInternal() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        hostEndpoint = null
        awaiting.clear()
        dao.resetSyncingExpenses()
        dao.resetSyncingSettlements()
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            val raw = info.endpointName
            val found = FoundGroup(endpointId, raw.substringBeforeLast('#'), raw.substringAfterLast('#', ""))
            _state.update { s -> s.copy(found = s.found.filter { it.endpointId != endpointId } + found) }
            val code = pendingCode
            if (found.tag == targetTag && code != null && _state.value.role == Role.SEARCHING) {
                connect(endpointId, code)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _state.update { s -> s.copy(found = s.found.filter { it.endpointId != endpointId }) }
        }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            client.acceptConnection(endpointId, payloads)
            if (_state.value.role == Role.HOST) {
                _state.update { s ->
                    s.copy(peers = s.peers.filter { it.endpointId != endpointId } + Peer(endpointId, info.endpointName))
                }
            }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val ok = resolution.status.isSuccess
            if (_state.value.role == Role.HOST) {
                if (!ok) _state.update { s -> s.copy(peers = s.peers.filter { it.endpointId != endpointId }) }
                return // wait for Hello
            }
            if (ok) {
                hostEndpoint = endpointId
                client.stopDiscovery()
                _state.update { it.copy(message = "Verifying group code…") }
                val p = profiles.current
                send(endpointId, Hello(targetTag ?: "", pendingCode ?: "", p.deviceId, p.name.ifBlank { "Friend" }, p.upiId.ifBlank { null }))
            } else {
                _state.update { it.copy(role = Role.SEARCHING, message = "Looking for nearby groups…", error = "Connection was not accepted") }
            }
        }

        override fun onDisconnected(endpointId: String) {
            work.trySend(Work.Gone(endpointId))
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val msg = try {
                json.decodeFromString(SyncMessage.serializer(), bytes.decodeToString())
            } catch (e: Exception) {
                Log.w(TAG, "bad payload", e); return
            }
            work.trySend(Work.In(endpointId, msg))
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    // ======================= Event processing =======================

    private suspend fun process(w: Work) {
        when (w) {
            is Work.In -> handle(w.endpoint, w.msg)
            is Work.Local -> onLocalChange(w.change)
            is Work.Gone -> onPeerGone(w.endpoint)
            is Work.Timeout -> onTimeout(w.entityId)
        }
    }

    private fun isVerified(endpoint: String) =
        _state.value.peers.any { it.endpointId == endpoint && it.verified }

    private suspend fun handle(ep: String, msg: SyncMessage) {
        val role = _state.value.role
        val isHost = role == Role.HOST
        // Host ignores everything from a phone that has not passed the code check.
        if (isHost && msg !is Hello && !isVerified(ep)) return

        when (msg) {
            is Hello -> if (isHost) hostOnHello(ep, msg)

            is Reject -> {
                userStopped = true
                pendingCode = null
                client.disconnectFromEndpoint(ep)
                hostEndpoint = null
                _state.update { it.copy(role = Role.SEARCHING, message = "Looking for nearby groups…", error = msg.reason) }
                startDiscovery()
            }

            is Welcome -> {
                userStopped = false
                _state.update { it.copy(role = Role.MEMBER, groupId = msg.groupId, message = "Connected to host", error = null) }
                dao.promoteLocalExpenses(msg.groupId)
                dao.promoteLocalSettlements(msg.groupId)
                // Push everything the host may be missing (offline edits).
                dao.membersOf(msg.groupId).forEach { send(ep, MemberMsg(it)) }
                pushUnsynced(ep, msg.groupId)
            }

            is GroupMsg -> if (!isHost) {
                val local = dao.group(msg.group.id)
                // Cloud membership is per phone: never take it from another device.
                dao.upsertGroup(
                    msg.group.copy(
                        coverUri = local?.coverUri, shared = local?.shared ?: false, cloudToken = local?.cloudToken,
                        myMemberId = local?.myMemberId, lastPulledAt = local?.lastPulledAt,
                    )
                )
            }

            is MemberMsg -> {
                if (isHost && msg.member.groupId != _state.value.groupId) return
                val existing = dao.member(msg.member.id)
                // Never let someone else overwrite an existing member's device link.
                if (existing?.deviceId != null && existing.deviceId != msg.member.deviceId) return
                dao.upsertMember(msg.member)
                onReceived("member", msg.member.groupId, msg.member.id)
                if (isHost) relay(ep) { send(it, msg) }
            }

            is ExpenseMsg -> applyExpense(ep, msg, isHost)
            is SettlementMsg -> applySettlement(ep, msg, isHost)
            is Ack -> onAck(ep, msg)

            is Nack -> {
                awaiting.remove(msg.entityId)
                dao.setExpenseStatus(msg.entityId, SyncStatus.FAILED)
                dao.setSettlementStatus(msg.entityId, SyncStatus.FAILED)
                _state.update { it.copy(error = "Host rejected an item: ${msg.reason}") }
            }
        }
    }

    private suspend fun hostOnHello(ep: String, hello: Hello) {
        val gid = _state.value.groupId ?: return
        val g = dao.group(gid) ?: return
        if (hello.groupTag != g.id.take(8) || !hello.code.equals(g.joinCode, ignoreCase = true)) {
            send(ep, Reject("Wrong group code"))
            scope.launch { delay(800); client.disconnectFromEndpoint(ep) }
            _state.update { s -> s.copy(peers = s.peers.filter { it.endpointId != ep }) }
            return
        }

        // Link to an existing member (same phone, or a name-only member with the same name).
        val members = dao.membersOf(gid)
        val existing = members.firstOrNull { it.deviceId == hello.deviceId }
            ?: members.firstOrNull {
                (it.deviceId == null || it.deviceId == "linked") && it.name.trim().equals(hello.name.trim(), ignoreCase = true)
            }
        val member = existing?.copy(deviceId = hello.deviceId, upiId = hello.upiId ?: existing.upiId)
            ?: MemberEntity(UUID.randomUUID().toString(), gid, hello.name.trim(), hello.deviceId, hello.upiId, System.currentTimeMillis())
        dao.upsertMember(member)

        _state.update { s ->
            s.copy(
                peers = s.peers.map { if (it.endpointId == ep) it.copy(name = member.name, verified = true, memberId = member.id) else it },
                message = "${member.name} joined",
            )
        }

        dao.promoteLocalExpenses(gid)
        dao.promoteLocalSettlements(gid)

        // Initial ledger snapshot (tombstones included so deletions propagate).
        send(ep, GroupMsg(g.copy(coverUri = null, cloudToken = null, myMemberId = null, lastPulledAt = null)))
        dao.membersOf(gid).forEach { send(ep, MemberMsg(it)) }
        dao.allExpensesOf(gid).forEach { e ->
            sendTracked(ep, e.id, ExpenseMsg(e.copy(receiptUri = null), dao.splitsOf(e.id)))
        }
        dao.allSettlementsOf(gid).forEach { s -> sendTracked(ep, s.id, SettlementMsg(s.copy(proofUri = null))) }
        send(ep, Welcome(gid, member.id))

        relay(ep) { send(it, MemberMsg(member)) }
    }

    private suspend fun applyExpense(ep: String, msg: ExpenseMsg, isHost: Boolean) {
        val e = msg.expense
        val splits = msg.splits.filter { it.expenseId == e.id }
        if (isHost) {
            val problem = validateExpense(msg)
            if (problem != null) { send(ep, Nack(e.id, problem)); return }
        } else if (dao.group(e.groupId) == null) return

        val local = dao.expense(e.id)
        if (local != null && local.updatedAt > e.updatedAt) {
            // We hold a newer version: acknowledge theirs, then share ours.
            send(ep, Ack(e.id, e.updatedAt))
            if (isHost) sendTracked(ep, local.id, ExpenseMsg(local.copy(receiptUri = null), dao.splitsOf(local.id)))
            return
        }
        dao.saveExpense(e.copy(syncStatus = SyncStatus.SYNCED, receiptUri = local?.receiptUri), splits)
        onReceived("expense", e.groupId, e.id)
        send(ep, Ack(e.id, e.updatedAt))
        if (isHost) relay(ep) { sendTracked(it, e.id, ExpenseMsg(e.copy(receiptUri = null), splits)) }
    }

    private suspend fun applySettlement(ep: String, msg: SettlementMsg, isHost: Boolean) {
        val s = msg.settlement
        if (isHost) {
            val ids = dao.membersOf(s.groupId).map { it.id }.toSet()
            val problem = when {
                s.groupId != _state.value.groupId -> "Unknown group"
                s.amount <= 0 -> "Invalid amount"
                s.fromId !in ids || s.toId !in ids -> "Unknown member"
                else -> null
            }
            if (problem != null) { send(ep, Nack(s.id, problem)); return }
        } else if (dao.group(s.groupId) == null) return

        val local = dao.settlement(s.id)
        if (local != null && local.updatedAt > s.updatedAt) {
            send(ep, Ack(s.id, s.updatedAt))
            if (isHost) sendTracked(ep, local.id, SettlementMsg(local.copy(proofUri = null)))
            return
        }
        dao.upsertSettlement(s.copy(syncStatus = SyncStatus.SYNCED, proofUri = local?.proofUri))
        onReceived("settlement", s.groupId, s.id)
        send(ep, Ack(s.id, s.updatedAt))
        if (isHost) relay(ep) { sendTracked(it, s.id, SettlementMsg(s.copy(proofUri = null))) }
    }

    private suspend fun validateExpense(msg: ExpenseMsg): String? {
        val e = msg.expense
        if (e.groupId != _state.value.groupId) return "Unknown group"
        if (e.deleted) return null
        val ids = dao.membersOf(e.groupId).map { it.id }.toSet()
        return when {
            e.amount <= 0 -> "Invalid amount"
            e.title.isBlank() -> "Missing title"
            e.payerId !in ids -> "Unknown payer"
            msg.splits.any { it.memberId !in ids || it.share < 0 } -> "Unknown participant"
            msg.splits.sumOf { it.share } != e.amount -> "Shares don't add up to the total"
            else -> null
        }
    }

    private suspend fun onAck(ep: String, ack: Ack) {
        val set = awaiting[ack.entityId]
        set?.remove(ep)
        if (set == null || set.isEmpty()) {
            awaiting.remove(ack.entityId)
            dao.expense(ack.entityId)?.let {
                if (it.updatedAt == ack.updatedAt) dao.setExpenseStatus(it.id, SyncStatus.SYNCED)
            }
            dao.settlement(ack.entityId)?.let {
                if (it.updatedAt == ack.updatedAt) dao.setSettlementStatus(it.id, SyncStatus.SYNCED)
            }
        }
    }

    private suspend fun onLocalChange(c: LocalChange) {
        val s = _state.value
        if (s.groupId != c.groupId) return
        val targets = when (s.role) {
            Role.HOST -> s.peers.filter { it.verified }.map { it.endpointId }
            Role.MEMBER -> listOfNotNull(hostEndpoint)
            else -> emptyList()
        }
        targets.forEach { sendChange(it, c) }
    }

    private suspend fun sendChange(ep: String, c: LocalChange) {
        when (c) {
            is LocalChange.Expense -> dao.expense(c.id)?.let {
                sendTracked(ep, it.id, ExpenseMsg(it.copy(receiptUri = null), dao.splitsOf(it.id)))
            }
            is LocalChange.Settlement -> dao.settlement(c.id)?.let { sendTracked(ep, it.id, SettlementMsg(it.copy(proofUri = null))) }
            is LocalChange.Member -> dao.member(c.id)?.let { send(ep, MemberMsg(it)) }
        }
    }

    private suspend fun pushUnsynced(ep: String, groupId: String) {
        dao.unsyncedExpenses(groupId).forEach {
            sendTracked(ep, it.id, ExpenseMsg(it.copy(receiptUri = null), dao.splitsOf(it.id)))
        }
        dao.unsyncedSettlements(groupId).forEach { sendTracked(ep, it.id, SettlementMsg(it.copy(proofUri = null))) }
    }

    private suspend fun onPeerGone(ep: String) {
        // Anything still waiting on this phone goes back to PENDING (retried on reconnect).
        awaiting.entries.filter { ep in it.value }.forEach { (id, set) ->
            set.remove(ep)
            dao.expense(id)?.let { if (it.syncStatus == SyncStatus.SYNCING) dao.setExpenseStatus(id, SyncStatus.PENDING) }
            dao.settlement(id)?.let { if (it.syncStatus == SyncStatus.SYNCING) dao.setSettlementStatus(id, SyncStatus.PENDING) }
            if (set.isEmpty()) awaiting.remove(id)
        }
        if (_state.value.role == Role.HOST) {
            val name = _state.value.peers.firstOrNull { it.endpointId == ep }?.name
            _state.update { s ->
                s.copy(peers = s.peers.filter { it.endpointId != ep }, message = name?.let { "$it disconnected" } ?: s.message)
            }
        } else if (ep == hostEndpoint) {
            hostEndpoint = null
            if (!userStopped) {
                _state.update { it.copy(role = Role.SEARCHING, message = "Host out of range — reconnecting…") }
                startDiscovery() // auto-reconnects when the host is seen again
            }
        }
    }

    private suspend fun onTimeout(entityId: String) {
        val set = awaiting[entityId] ?: return
        if (set.isEmpty()) return
        awaiting.remove(entityId)
        dao.expense(entityId)?.let { if (it.syncStatus == SyncStatus.SYNCING) dao.setExpenseStatus(entityId, SyncStatus.FAILED) }
        dao.settlement(entityId)?.let { if (it.syncStatus == SyncStatus.SYNCING) dao.setSettlementStatus(entityId, SyncStatus.FAILED) }
    }

    // ======================= Sending =======================

    private inline fun relay(except: String, block: (String) -> Unit) {
        _state.value.peers.filter { it.verified && it.endpointId != except }.forEach { block(it.endpointId) }
    }

    private suspend fun sendTracked(ep: String, entityId: String, msg: SyncMessage) {
        awaiting.getOrPut(entityId) { ConcurrentHashMap.newKeySet() }.add(ep)
        dao.expense(entityId)?.let { if (it.syncStatus != SyncStatus.SYNCED) dao.setExpenseStatus(entityId, SyncStatus.SYNCING) }
        dao.settlement(entityId)?.let { if (it.syncStatus != SyncStatus.SYNCED) dao.setSettlementStatus(entityId, SyncStatus.SYNCING) }
        send(ep, msg)
        scope.launch { delay(ACK_TIMEOUT_MS); work.send(Work.Timeout(entityId)) }
    }

    private fun send(ep: String, msg: SyncMessage) {
        val bytes = json.encodeToString(SyncMessage.serializer(), msg).encodeToByteArray()
        client.sendPayload(ep, Payload.fromBytes(bytes))
            .addOnFailureListener { Log.w(TAG, "send failed", it) }
    }

    private fun friendly(e: Exception): String {
        val m = e.message ?: return "Nearby connection error"
        return when {
            "8001" in m || "ALREADY" in m -> "Already active — try again"
            "8034" in m || "8032" in m || "BLUETOOTH" in m.uppercase() -> "Turn on Bluetooth and try again"
            "MISSING_PERMISSION" in m || "8030" in m -> "Nearby permissions are missing"
            "17" in m && "API" in m -> "Google Play services is required for nearby sync"
            else -> "Nearby error: $m"
        }
    }

    companion object {
        private const val TAG = "VeyloSync"
        const val SERVICE_ID = "com.veylo.app.SYNC"
        private const val ACK_TIMEOUT_MS = 15_000L

        fun requiredPermissions(): Array<String> = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            Build.VERSION.SDK_INT >= 31 -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}
