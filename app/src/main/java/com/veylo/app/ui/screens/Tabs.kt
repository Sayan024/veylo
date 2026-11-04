package com.veylo.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veylo.app.data.local.GroupType
import com.veylo.app.data.sync.SyncEngine
import com.veylo.app.domain.Money
import com.veylo.app.ui.ActivityItem
import com.veylo.app.ui.AppViewModel
import com.veylo.app.ui.GroupSummary
import com.veylo.app.ui.components.Avatar
import com.veylo.app.ui.components.AvatarStack
import com.veylo.app.ui.components.ButtonStyle
import com.veylo.app.ui.components.CategoryBadge
import com.veylo.app.ui.components.EmptyState
import com.veylo.app.ui.components.GroupCover
import com.veylo.app.ui.components.SectionHeader
import com.veylo.app.ui.components.SegmentedTabs
import com.veylo.app.ui.components.SyncPill
import com.veylo.app.ui.components.VButton
import com.veylo.app.ui.components.VCard
import com.veylo.app.ui.components.VTextField
import com.veylo.app.ui.components.dayLabel
import com.veylo.app.ui.components.formatDate
import com.veylo.app.ui.components.formatTime
import com.veylo.app.ui.components.greeting
import com.veylo.app.ui.navigation.Nav
import com.veylo.app.ui.theme.Palette
import com.veylo.app.ui.theme.Veylo

private val listPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp)

// =============================================================== HOME

@Composable
fun HomeTab(vm: AppViewModel, nav: Nav, openTab: (Int) -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val sync by vm.syncState.collectAsStateWithLifecycle()
    val d = data ?: return

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${greeting()}, ${profile.name.substringBefore(' ')} 👋", style = MaterialTheme.typography.headlineSmall)
                    Text("Ready for your next adventure?", style = MaterialTheme.typography.bodyMedium, color = Veylo.colors.muted)
                }
                Box(Modifier.clip(CircleShape).clickable { openTab(3) }) { Avatar(profile.name, size = 46.dp) }
            }
        }

        item { BalanceHero(d.net, d.mainCurrency, sync, onSync = { nav.join() }) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("You owe", Money.format(d.youOwe, d.mainCurrency), Icons.Rounded.CallMade,
                    Veylo.colors.oweBg, Veylo.colors.oweFg, Modifier.weight(1f))
                MiniStat("Owed to you", Money.format(d.owedToYou, d.mainCurrency), Icons.Rounded.CallReceived,
                    Veylo.colors.owedBg, Veylo.colors.owedFg, Modifier.weight(1f))
            }
        }

        if (sync.role != SyncEngine.Role.IDLE) {
            item { NearbyStatusCard(sync, d.group(sync.groupId)?.group?.name) { sync.groupId?.let(nav::group) ?: nav.join() } }
        }

        if (d.groups.isEmpty()) {
            item {
                VCard {
                    EmptyState(
                        Icons.Rounded.Landscape, "Plan your first trip",
                        "Create a trip or a quick outing, add friends, and VEYLO keeps everyone's balance in sync.",
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            VButton("Create Trip", { nav.create(GroupType.TRIP) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Landscape)
                            VButton("Start Outing", { nav.create(GroupType.OUTING) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.LocalCafe, style = ButtonStyle.Soft)
                            VButton("Join nearby group", { nav.join() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Bluetooth, style = ButtonStyle.Soft)
                        }
                    }
                }
            }
        } else {
            item { SectionHeader("Your Groups", Modifier.padding(top = 6.dp), "See all") { openTab(1) } }
            items(d.groups.take(3), key = { it.group.id }) { g ->
                GroupRow(g, live = sync.groupId == g.group.id && sync.role in setOf(SyncEngine.Role.HOST, SyncEngine.Role.MEMBER)) {
                    nav.group(g.group.id)
                }
            }
            if (d.activity.isNotEmpty()) {
                item { SectionHeader("Recent Activity", Modifier.padding(top = 6.dp), "View all") { openTab(2) } }
                items(d.activity.take(5), key = { "a" + it.id }) { a ->
                    ActivityRow(a) { if (a.isSettlement) nav.group(a.groupId) else nav.expense(a.id) }
                }
            }
        }
    }
}

@Composable
private fun BalanceHero(net: Long, currency: String, sync: SyncEngine.State, onSync: () -> Unit) {
    val label = when {
        net > 0 -> "You are owed"
        net < 0 -> "You owe overall"
        else -> "All settled up"
    }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Palette.Forest, Palette.ForestDeep)))
            .padding(22.dp),
    ) {
        Column {
            Text("Your Balance", color = Veylo.colors.onHeroMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                Money.format(kotlin.math.abs(net), currency),
                color = Color.White, style = MaterialTheme.typography.displaySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(label, color = if (net >= 0) Palette.Lime else Color(0xFFFFB4A6), style = MaterialTheme.typography.labelLarge)
        }
        val connected = sync.role == SyncEngine.Role.HOST || sync.role == SyncEngine.Role.MEMBER
        Box(
            Modifier.align(Alignment.TopEnd).size(48.dp).clip(RoundedCornerShape(16.dp))
                .background(Palette.Lime).clickable(onClick = onSync),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (connected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,
                "Nearby sync", tint = Palette.ForestDeep,
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, fg: Color, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(fg.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.85f))
            Text(value, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun NearbyStatusCard(s: SyncEngine.State, groupName: String?, onClick: () -> Unit) {
    val (icon, title) = when (s.role) {
        SyncEngine.Role.HOST -> Icons.Rounded.BluetoothConnected to "Hosting ${groupName ?: "group"} · ${s.connectedCount} connected"
        SyncEngine.Role.MEMBER -> Icons.Rounded.BluetoothConnected to "Synced with ${groupName ?: "host"}"
        SyncEngine.Role.CONNECTING -> Icons.Rounded.BluetoothSearching to "Connecting…"
        else -> Icons.Rounded.BluetoothSearching to "Searching nearby…"
    }
    VCard(color = Veylo.colors.limeTint, padding = PaddingValues(14.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                s.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted) }
            }
        }
    }
}

@Composable
fun GroupRow(g: GroupSummary, live: Boolean, onClick: () -> Unit) {
    VCard(padding = PaddingValues(12.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GroupCover(g.group, Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(g.group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val bal = g.myBalance
                val balText = when {
                    bal > 0 -> "you get ${Money.format(bal, g.currency)}"
                    bal < 0 -> "you owe ${Money.format(-bal, g.currency)}"
                    else -> "settled"
                }
                Text(
                    "${g.members.size} members · $balText",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        bal > 0 -> Veylo.colors.owedFg
                        bal < 0 -> Veylo.colors.oweFg
                        else -> Veylo.colors.muted
                    },
                )
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Veylo.colors.limeTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        live -> Icons.Rounded.BluetoothConnected
                        g.unsyncedCount > 0 -> Icons.Rounded.BluetoothSearching
                        else -> Icons.Rounded.CheckCircle
                    },
                    null, tint = if (g.unsyncedCount > 0 && !live) Veylo.colors.warn else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun ActivityRow(a: ActivityItem, showGroup: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Veylo.colors.card)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(a.category, settlement = a.isSettlement)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (showGroup) a.subtitle else "${a.subtitle.substringBefore(" · ")} · ${formatTime(a.time)}",
                style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Money.format(a.amount, a.currency), style = MaterialTheme.typography.titleSmall,
                color = if (a.isSettlement) Veylo.colors.owedFg else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            SyncPill(a.syncStatus, compact = true)
        }
    }
}

// =============================================================== GROUPS

@Composable
fun GroupsTab(vm: AppViewModel, nav: Nav) {
    val data by vm.data.collectAsStateWithLifecycle()
    val sync by vm.syncState.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val all = data?.groups ?: return
    val shown = when (filter) {
        1 -> all.filter { it.group.type == GroupType.TRIP }
        2 -> all.filter { it.group.type == GroupType.OUTING }
        else -> all
    }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Groups", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                VButton("Join", { nav.join() }, icon = Icons.Rounded.Bluetooth, style = ButtonStyle.Soft, height = 42.dp)
            }
        }
        item { SegmentedTabs(listOf("All", "Trips", "Outings"), filter, { filter = it }) }
        if (shown.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.Groups, "No groups yet", "Tap + to create a trip or start an outing.") {
                    VButton("Create Trip", { nav.create(GroupType.TRIP) })
                }
            }
        }
        items(shown, key = { it.group.id }) { g ->
            val live = sync.groupId == g.group.id && sync.role in setOf(SyncEngine.Role.HOST, SyncEngine.Role.MEMBER)
            Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Veylo.colors.card).clickable { nav.group(g.group.id) }) {
                Box(Modifier.fillMaxWidth().height(150.dp)) {
                    GroupCover(g.group, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA0F2A1D)))))
                    Row(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                        Text(
                            if (g.group.type == GroupType.TRIP) "TRIP" else "OUTING",
                            style = MaterialTheme.typography.labelSmall, color = Palette.ForestDeep,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.Lime).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                        if (live) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "● LIVE", style = MaterialTheme.typography.labelSmall, color = Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x66000000)).padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text(g.group.name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        if (g.group.destination.isNotBlank()) {
                            Text(g.group.destination, style = MaterialTheme.typography.bodySmall, color = Color(0xCCFFFFFF))
                        }
                    }
                }
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarStack(g.members.map { it.name }, size = 30.dp)
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total spent", style = MaterialTheme.typography.labelSmall, color = Veylo.colors.muted)
                        Text(Money.format(g.totalSpent, g.currency), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

// =============================================================== ACTIVITY

@Composable
fun ActivityTab(vm: AppViewModel, nav: Nav) {
    val data by vm.data.collectAsStateWithLifecycle()
    val items = data?.activity ?: return
    val grouped = remember(items) { items.groupBy { dayLabel(it.time) } }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Activity", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        }
        if (items.isEmpty()) {
            item { EmptyState(Icons.Rounded.Timeline, "Nothing yet", "Expenses and settlements from all your groups show up here.") }
        }
        grouped.forEach { (day, list) ->
            item(key = "h$day") {
                Text(day, style = MaterialTheme.typography.labelMedium, color = Veylo.colors.muted, modifier = Modifier.padding(top = 8.dp))
            }
            items(list, key = { "act" + it.id }) { a ->
                ActivityRow(a) { if (a.isSettlement) nav.group(a.groupId) else nav.expense(a.id) }
            }
        }
    }
}

// =============================================================== PROFILE

@Composable
fun ProfileTab(vm: AppViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    var name by remember(profile.name) { mutableStateOf(profile.name) }
    var upi by remember(profile.upiId) { mutableStateOf(profile.upiId) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dirty = name.trim() != profile.name || upi.trim() != profile.upiId

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(profile.name.ifBlank { "?" }, size = 88.dp)
                Spacer(Modifier.height(12.dp))
                Text(profile.name, style = MaterialTheme.typography.headlineMedium)
                Text("Device ${profile.deviceId.take(8).uppercase()}", style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted)
            }
        }
        item {
            val d = data
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Groups", "${d?.groups?.size ?: 0}", Modifier.weight(1f))
                StatTile("Expenses", "${d?.groups?.sumOf { it.expenses.size } ?: 0}", Modifier.weight(1f))
                StatTile("Pending sync", "${d?.groups?.sumOf { it.unsyncedCount } ?: 0}", Modifier.weight(1f))
            }
        }
        item {
            VCard {
                Text("Profile", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                VTextField(name, { name = it.take(30) }, "Display name")
                Spacer(Modifier.height(12.dp))
                VTextField(upi, { upi = it.trim() }, "UPI ID (optional)", placeholder = "name@bank")
                Text(
                    "Shared with group members so they can pay you via UPI. Never stores payment credentials.",
                    style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted, modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(14.dp))
                VButton("Save", { vm.updateProfile(name, upi) }, Modifier.fillMaxWidth(), enabled = dirty && name.isNotBlank(), style = ButtonStyle.Forest)
            }
        }
        item {
            VCard {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                val opts = listOf("system", "light", "dark")
                SegmentedTabs(listOf("System", "Light", "Dark"), opts.indexOf(profile.theme).coerceAtLeast(0), { vm.setTheme(opts[it]) })
            }
        }
        item {
            VCard {
                Text("Privacy & data", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Everything stays on this phone. Groups sync directly between nearby phones over Bluetooth / Wi-Fi — no servers, no login.",
                    style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted,
                )
                Spacer(Modifier.height(14.dp))
                VButton("Delete all data", { confirmDelete = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.DeleteForever, style = ButtonStyle.Soft)
            }
        }
        item {
            Text(
                "VEYLO 0.1.0 · Together, expenses made effortless.",
                style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all data?") },
            text = { Text("All groups, expenses and settlements are removed from this phone. Other members keep their copies.") },
            confirmButton = { TextButton({ vm.deleteAllData(); confirmDelete = false }) { Text("Delete", color = Veylo.colors.oweFg) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    VCard(modifier, padding = PaddingValues(14.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Veylo.colors.muted)
    }
}
