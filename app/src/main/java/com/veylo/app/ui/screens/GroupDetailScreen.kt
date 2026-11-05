package com.veylo.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veylo.app.data.local.SyncStatus
import com.veylo.app.data.sync.SyncEngine
import com.veylo.app.domain.Money
import com.veylo.app.domain.Transfer
import com.veylo.app.ui.AppViewModel
import com.veylo.app.ui.GroupSummary
import com.veylo.app.ui.components.Avatar
import com.veylo.app.ui.components.CategoryBadge
import com.veylo.app.ui.components.EmptyState
import com.veylo.app.ui.components.GroupCover
import com.veylo.app.ui.components.IconCircle
import com.veylo.app.ui.components.SegmentedTabs
import com.veylo.app.ui.components.SyncPill
import com.veylo.app.ui.components.VButton
import com.veylo.app.ui.components.VCard
import com.veylo.app.ui.components.VTextField
import com.veylo.app.ui.components.formatDate
import com.veylo.app.ui.navigation.Nav
import com.veylo.app.ui.theme.Palette
import com.veylo.app.ui.theme.Veylo

@Composable
fun GroupDetailScreen(vm: AppViewModel, nav: Nav, groupId: String) {
    val data by vm.data.collectAsStateWithLifecycle()
    val sync by vm.syncState.collectAsStateWithLifecycle()
    val d = data ?: return
    val g = d.group(groupId)
    var seen by remember { mutableStateOf(false) }
    if (g == null) {
        // Only leave if the group existed and was then removed (a just-created group may not be loaded yet).
        if (seen) androidx.compose.runtime.LaunchedEffect(Unit) { nav.back() }
        return
    }
    seen = true
    com.veylo.app.ui.components.DarkHeaderStatusBar()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var showSync by rememberSaveable { mutableStateOf(false) }
    var addMember by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var settleTarget by remember { mutableStateOf<Transfer?>(null) }
    val ctx = LocalContext.current
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.changeCover(groupId, uri)
    }
    val live = sync.groupId == groupId && (sync.role == SyncEngine.Role.HOST || sync.role == SyncEngine.Role.MEMBER)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(330.dp)) {
                    GroupCover(g.group, Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0f to Color(0x55000000), 0.35f to Color.Transparent, 1f to Color(0xCC0F2A1D))
                        )
                    )
                    Row(Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth()) {
                        IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, { nav.back() }, bg = Color(0x55000000), tint = Color.White)
                        Spacer(Modifier.weight(1f))
                        IconCircle(Icons.Rounded.QrCode2, { showSync = true }, bg = Color(0x55000000), tint = Color.White, contentDescription = "Invite")
                        Spacer(Modifier.width(8.dp))
                        Box {
                            IconCircle(Icons.Rounded.MoreVert, { menu = true }, bg = Color(0x55000000), tint = Color.White)
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("Nearby sync & invite") }, { menu = false; showSync = true }, leadingIcon = { Icon(Icons.Rounded.Bluetooth, null) })
                                DropdownMenuItem({ Text("Add member") }, { menu = false; addMember = true }, leadingIcon = { Icon(Icons.Rounded.Add, null) })
                                DropdownMenuItem({ Text("Change cover") }, {
                                    menu = false
                                    coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }, leadingIcon = { Icon(Icons.Rounded.Image, null) })
                                DropdownMenuItem({ Text("Leave group", color = Veylo.colors.oweFg) }, { menu = false; confirmLeave = true },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ExitToApp, null, tint = Veylo.colors.oweFg) })
                            }
                        }
                    }
                    Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 46.dp)) {
                        Text(g.group.name, style = MaterialTheme.typography.headlineLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).clickable { showSync = true }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val sub = buildString {
                                append("${g.members.size} members")
                                if (g.group.destination.isNotBlank()) append(" · ${g.group.destination}")
                                g.group.startDate?.let { append(" · ${formatDate(it)}") }
                            }
                            Text(sub, color = Color(0xDDFFFFFF), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(10.dp))
                            Icon(
                                if (live) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth, null,
                                tint = Palette.Lime, modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                when {
                                    live && sync.role == SyncEngine.Role.HOST -> "Hosting · ${sync.connectedCount}"
                                    live -> "Live"
                                    g.unsyncedCount > 0 -> "${g.unsyncedCount} to sync"
                                    g.group.shared -> "Online"
                                    g.hasOtherDevices -> "Synced"
                                    else -> "Offline"
                                },
                                color = Palette.Lime, style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            g.members.take(6).forEachIndexed { i, m ->
                                Avatar(m.name, Modifier.offset(x = (-8 * i).dp), size = 38.dp, ring = Color.White)
                            }
                            Box(
                                Modifier.offset(x = (-8 * minOf(g.members.size, 6)).dp).size(38.dp).clip(CircleShape)
                                    .background(Color.White).clickable { addMember = true },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Add, "Add member", tint = Palette.ForestDeep) }
                        }
                    }
                    // rounded "sheet lip"
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp)
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }
            item {
                SegmentedTabs(
                    listOf("Expenses", "Balances", "Settle Up"), tab, { tab = it },
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp),
                )
            }
            when (tab) {
                0 -> expensesTab(g, nav)
                1 -> balancesTab(g)
                else -> settleTab(g, onSettle = { settleTarget = it }, onPay = { t ->
                    val to = g.member(t.toId)
                    if (to?.upiId != null) launchUpi(ctx, to.upiId, to.name, t.amount, "VEYLO · ${g.group.name}")
                }, onUndo = { vm.deleteSettlement(it) })
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            VButton("Add Expense", { nav.addExpense(groupId) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        }
    }

    if (showSync) SyncSheet(vm, g) { showSync = false }

    if (addMember) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addMember = false },
            title = { Text("Add member") },
            text = {
                VTextField(name, { name = it.take(30) }, "Name", placeholder = "Priya",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            },
            confirmButton = {
                TextButton({ if (name.isNotBlank()) vm.addMember(groupId, name); addMember = false }, enabled = name.isNotBlank()) { Text("Add") }
            },
            dismissButton = { TextButton({ addMember = false }) { Text("Cancel") } },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave ${g.group.name}?") },
            text = { Text("This removes the group and its expenses from this phone. Other members keep their copies.") },
            confirmButton = {
                TextButton({ confirmLeave = false; vm.leaveGroup(groupId) }) { Text("Leave", color = Veylo.colors.oweFg) }
            },
            dismissButton = { TextButton({ confirmLeave = false }) { Text("Cancel") } },
        )
    }

    settleTarget?.let { t ->
        var amount by remember(t) { mutableStateOf(Money.plain(t.amount)) }
        val parsed = Money.parse(amount)
        AlertDialog(
            onDismissRequest = { settleTarget = null },
            title = { Text("Record payment") },
            text = {
                Column {
                    Text("${g.name(t.fromId)} paid ${g.name(t.toId)}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    VTextField(amount, { amount = it }, "Amount", prefix = Money.symbol(g.currency),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton({
                    if (parsed != null && parsed > 0) vm.settle(groupId, t.fromId, t.toId, parsed)
                    settleTarget = null
                }, enabled = parsed != null && parsed > 0) { Text("Mark as settled") }
            },
            dismissButton = { TextButton({ settleTarget = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- Expenses

private fun androidx.compose.foundation.lazy.LazyListScope.expensesTab(g: GroupSummary, nav: Nav) {
    item {
        VCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), color = Veylo.colors.limeTint) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Total Spent", style = MaterialTheme.typography.labelMedium, color = Veylo.colors.muted)
                    Text(Money.format(g.totalSpent, g.currency), style = MaterialTheme.typography.headlineMedium)
                    g.me?.let { me ->
                        val myShare = g.splits.values.flatten().filter { it.memberId == me.id }.sumOf { it.share }
                        Text("Your share ${Money.format(myShare, g.currency)}", style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted)
                    }
                }
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Lime), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.BarChart, null, tint = Palette.ForestDeep)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    if (g.expenses.isEmpty()) {
        item {
            EmptyState(Icons.Rounded.ReceiptLong, "No expenses yet", "Add the first bill — hotel, cab, chai, anything. VEYLO does the math.")
        }
    }
    items(g.expenses, key = { it.id }) { e ->
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Veylo.colors.card).clickable { nav.expense(e.id) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(e.category)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${g.name(e.payerId)} paid · ${formatDate(e.createdAt)}",
                    style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(e.amount, g.currency, decimals = false), style = MaterialTheme.typography.titleMedium)
                SyncPill(e.syncStatus, compact = true)
            }
        }
    }
}

// ---------------------------------------------------------------- Balances

private fun androidx.compose.foundation.lazy.LazyListScope.balancesTab(g: GroupSummary) {
    items(g.members, key = { it.id }) { m ->
        val bal = g.balances[m.id] ?: 0L
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Veylo.colors.card).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(m.name, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (m.id == g.me?.id) "${m.name} (you)" else m.name, style = MaterialTheme.typography.titleSmall)
                    if (m.deviceId != null && m.id != g.me?.id) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Bluetooth, "Linked phone", tint = Veylo.colors.muted, modifier = Modifier.size(13.dp))
                    }
                }
                Text("Paid ${Money.format(g.paidBy(m.id), g.currency)}", style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when {
                        bal > 0 -> "gets back"
                        bal < 0 -> "owes"
                        else -> "settled"
                    },
                    style = MaterialTheme.typography.labelSmall, color = Veylo.colors.muted,
                )
                Text(
                    Money.format(kotlin.math.abs(bal), g.currency),
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        bal > 0 -> Veylo.colors.owedFg
                        bal < 0 -> Veylo.colors.oweFg
                        else -> Veylo.colors.muted
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Settle up

private fun androidx.compose.foundation.lazy.LazyListScope.settleTab(
    g: GroupSummary,
    onSettle: (Transfer) -> Unit,
    onPay: (Transfer) -> Unit,
    onUndo: (String) -> Unit,
) {
    item {
        VCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), color = Veylo.colors.limeTint) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Palette.Lime), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = Palette.ForestDeep)
                }
                Spacer(Modifier.height(10.dp))
                Text("Minimum Settlements", style = MaterialTheme.typography.titleLarge)
                Text(
                    when (g.transfers.size) {
                        0 -> "Everyone is settled up 🎉"
                        1 -> "Clear all balances with just 1 payment"
                        else -> "Clear all balances with just ${g.transfers.size} payments"
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Veylo.colors.muted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    items(g.transfers, key = { "${it.fromId}>${it.toId}" }) { t ->
        val to = g.member(t.toId)
        val canUpi = t.fromId == g.me?.id && !to?.upiId.isNullOrBlank() && g.currency == "INR"
        VCard(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(g.member(t.fromId)?.name ?: "?", size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.name(t.fromId), style = MaterialTheme.typography.titleSmall)
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.padding(horizontal = 6.dp).size(14.dp), tint = Veylo.colors.muted)
                        Text(g.name(t.toId), style = MaterialTheme.typography.titleSmall)
                    }
                    Text(Money.format(t.amount, g.currency), style = MaterialTheme.typography.headlineSmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canUpi) {
                    VButton("Pay via UPI", { onPay(t) }, Modifier.weight(1f), style = com.veylo.app.ui.components.ButtonStyle.Soft, height = 44.dp)
                }
                VButton("Mark as Settled", { onSettle(t) }, Modifier.weight(1f), icon = Icons.Rounded.Check,
                    style = com.veylo.app.ui.components.ButtonStyle.Forest, height = 44.dp)
            }
        }
    }
    if (g.settlements.isNotEmpty()) {
        item {
            Text(
                "Settlement history", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
            )
        }
        items(g.settlements, key = { it.id }) { s ->
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Veylo.colors.card).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBadge(null, size = 38.dp, settlement = true)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${g.name(s.fromId)} paid ${g.name(s.toId)}", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDate(s.createdAt), style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted)
                        Spacer(Modifier.width(6.dp))
                        SyncPill(s.syncStatus, compact = true)
                    }
                }
                Text(Money.format(s.amount, g.currency), style = MaterialTheme.typography.titleSmall, color = Veylo.colors.owedFg)
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.Undo, "Undo", tint = Veylo.colors.muted,
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onUndo(s.id) }.padding(6.dp),
                )
            }
        }
    }
}
