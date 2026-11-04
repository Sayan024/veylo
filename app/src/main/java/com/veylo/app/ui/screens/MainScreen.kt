package com.veylo.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.veylo.app.data.local.GroupType
import com.veylo.app.ui.AppViewModel
import com.veylo.app.ui.navigation.Nav
import com.veylo.app.ui.theme.Palette
import com.veylo.app.ui.theme.Veylo

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Home", Icons.Rounded.Home),
    Tab("Groups", Icons.Rounded.Groups),
    Tab("Activity", Icons.Rounded.Timeline),
    Tab("Profile", Icons.Rounded.Person),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: AppViewModel, nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showActions by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { BottomBar(tab) { tab = it } },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showActions = true },
                containerColor = Palette.Lime,
                contentColor = Palette.ForestDeep,
                shape = CircleShape,
                modifier = Modifier.size(60.dp),
            ) { Icon(Icons.Rounded.Add, "New", modifier = Modifier.size(28.dp)) }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tabs") { t ->
                when (t) {
                    0 -> HomeTab(vm, nav, openTab = { tab = it })
                    1 -> GroupsTab(vm, nav)
                    2 -> ActivityTab(vm, nav)
                    else -> ProfileTab(vm)
                }
            }
        }
    }

    if (showActions) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            sheetState = sheet,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("Quick actions", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                val close = { showActions = false }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile("Add Expense", Icons.Rounded.ReceiptLong, Modifier.weight(1f)) { close(); nav.addExpense() }
                    ActionTile("Split Bill", Icons.Rounded.CallSplit, Modifier.weight(1f)) { close(); nav.addExpense() }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile("Create Trip", Icons.Rounded.Landscape, Modifier.weight(1f)) { close(); nav.create(GroupType.TRIP) }
                    ActionTile("Start Outing", Icons.Rounded.LocalCafe, Modifier.weight(1f)) { close(); nav.create(GroupType.OUTING) }
                }
                Spacer(Modifier.height(12.dp))
                ActionTile("Join a nearby group", Icons.Rounded.Bluetooth, Modifier.fillMaxWidth(), highlight = true) {
                    close(); nav.join()
                }
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, modifier: Modifier, highlight: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (highlight) Palette.Forest else Veylo.colors.card)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(if (highlight) Palette.Lime else Veylo.colors.limeTint),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Palette.Forest, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(
            label, style = MaterialTheme.typography.titleSmall,
            color = if (highlight) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Veylo.colors.card)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        tabs.forEachIndexed { i, t ->
            val sel = i == selected
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).clickable { onSelect(i) }.padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (sel) Veylo.colors.limeTint else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Icon(
                        t.icon, t.label,
                        tint = if (sel) MaterialTheme.colorScheme.primary else Veylo.colors.muted,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    t.label, style = MaterialTheme.typography.labelSmall,
                    color = if (sel) MaterialTheme.colorScheme.onSurface else Veylo.colors.muted,
                )
            }
        }
    }
}
