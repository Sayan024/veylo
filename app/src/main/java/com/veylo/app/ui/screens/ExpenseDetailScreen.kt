package com.veylo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.veylo.app.data.local.SyncStatus
import com.veylo.app.domain.Money
import com.veylo.app.domain.SplitMethod
import com.veylo.app.ui.AppViewModel
import com.veylo.app.ui.components.Avatar
import com.veylo.app.ui.components.ButtonStyle
import com.veylo.app.ui.components.CategoryBadge
import com.veylo.app.ui.components.IconCircle
import com.veylo.app.ui.components.SyncPill
import com.veylo.app.ui.components.VButton
import com.veylo.app.ui.components.VCard
import com.veylo.app.ui.components.category
import com.veylo.app.ui.components.formatDateTime
import com.veylo.app.ui.navigation.Nav
import com.veylo.app.ui.theme.Veylo

@Composable
fun ExpenseDetailScreen(vm: AppViewModel, nav: Nav, expenseId: String) {
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    val g = d.groups.firstOrNull { s -> s.expenses.any { it.id == expenseId } }
    val e = g?.expenses?.firstOrNull { it.id == expenseId }
    var seen by remember { mutableStateOf(false) }
    if (g == null || e == null) {
        if (seen) LaunchedEffect(Unit) { nav.back() }
        return
    }
    seen = true
    val splits = g.splits[e.id].orEmpty()
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, { nav.back() })
            Text(g.group.name, style = MaterialTheme.typography.titleMedium, color = Veylo.colors.muted, modifier = Modifier.weight(1f).padding(start = 12.dp))
            IconCircle(Icons.Rounded.Edit, { nav.editExpense(g.group.id, e.id) }, contentDescription = "Edit")
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CategoryBadge(e.category, size = 64.dp)
                Spacer(Modifier.height(12.dp))
                Text(e.title, style = MaterialTheme.typography.headlineSmall)
                Text(Money.format(e.amount, g.currency), style = MaterialTheme.typography.displayMedium)
                Text(
                    "${category(e.category).label} · ${formatDateTime(e.createdAt)}",
                    style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted,
                )
                Spacer(Modifier.height(10.dp))
                SyncPill(e.syncStatus)
                Text(
                    when (e.syncStatus) {
                        SyncStatus.SYNCED -> "Confirmed by the group host."
                        SyncStatus.SYNCING -> "Sent — waiting for confirmation."
                        SyncStatus.PENDING -> "Saved on this phone. Syncs when the host is nearby."
                        SyncStatus.FAILED -> "Not confirmed. Will retry on next connection."
                        else -> "Only on this phone — nobody else has joined yet."
                    },
                    style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted, modifier = Modifier.padding(top = 4.dp),
                )
            }

            VCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(g.member(e.payerId)?.name ?: "?", size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Paid by", style = MaterialTheme.typography.labelSmall, color = Veylo.colors.muted)
                        Text(g.name(e.payerId), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(Money.format(e.amount, g.currency), style = MaterialTheme.typography.titleMedium)
                }
            }

            VCard(Modifier.fillMaxWidth()) {
                Row {
                    Text("Split ${SplitMethod.of(e.splitMethod).label.lowercase()}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${splits.size} people", style = MaterialTheme.typography.labelMedium, color = Veylo.colors.muted)
                }
                Spacer(Modifier.height(8.dp))
                splits.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = Veylo.colors.divider)
                    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(g.member(s.memberId)?.name ?: "?", size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name(s.memberId), style = MaterialTheme.typography.bodyLarge)
                            val owes = if (s.memberId == e.payerId) "paid for the group" else "owes ${g.name(e.payerId)}"
                            Text(owes, style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted)
                        }
                        Text(Money.format(s.share, g.currency), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            e.receiptUri?.let { r ->
                Text("Receipt", style = MaterialTheme.typography.titleMedium)
                Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(22.dp))) {
                    AsyncImage(r, "Receipt", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }

            Text(
                "Last updated ${formatDateTime(e.updatedAt)}" + if (e.updatedAt != e.createdAt) " · edited" else "",
                style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted,
            )
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VButton("Delete", { confirmDelete = true }, Modifier.weight(1f), icon = Icons.Rounded.Delete, style = ButtonStyle.Soft)
            VButton("Edit", { nav.editExpense(g.group.id, e.id) }, Modifier.weight(1f), icon = Icons.Rounded.Edit, style = ButtonStyle.Forest)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete expense?") },
            text = { Text("\"${e.title}\" will be removed for everyone in the group once synced.") },
            confirmButton = {
                TextButton({ confirmDelete = false; vm.deleteExpense(e.id) }) { Text("Delete", color = Veylo.colors.oweFg) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
