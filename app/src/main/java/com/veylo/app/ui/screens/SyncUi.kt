package com.veylo.app.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veylo.app.data.sync.SyncEngine
import com.veylo.app.ui.AppViewModel
import com.veylo.app.ui.GroupSummary
import com.veylo.app.ui.components.Avatar
import com.veylo.app.ui.components.ButtonStyle
import com.veylo.app.ui.components.QrImage
import com.veylo.app.ui.components.VButton
import com.veylo.app.ui.components.VCard
import com.veylo.app.ui.theme.Palette
import com.veylo.app.ui.theme.Veylo
import java.math.BigDecimal
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Returns a function that requests Nearby permissions, then runs [onGranted]. */
@Composable
fun rememberNearbyPermission(onGranted: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val latest by rememberUpdatedState(onGranted)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasNearbyPermissions(ctx)) latest()
        else Toast.makeText(ctx, "Nearby permissions are needed to sync with friends", Toast.LENGTH_LONG).show()
    }
    return {
        if (hasNearbyPermissions(ctx)) latest() else launcher.launch(SyncEngine.requiredPermissions())
    }
}

fun hasNearbyPermissions(ctx: Context): Boolean {
    fun ok(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val required = SyncEngine.requiredPermissions().filter {
        // On Android 12+ location is only a fallback for older Play services; don't block on it.
        Build.VERSION.SDK_INT < 31 || (it != Manifest.permission.ACCESS_FINE_LOCATION && it != Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    return required.all(::ok)
}

fun inviteText(g: GroupSummary) = "veylo:${g.group.id.take(8)}:${g.group.joinCode}"

fun launchUpi(ctx: Context, upiId: String, payeeName: String, amountMinor: Long, note: String) {
    val uri = Uri.parse("upi://pay").buildUpon()
        .appendQueryParameter("pa", upiId)
        .appendQueryParameter("pn", payeeName)
        .appendQueryParameter("am", BigDecimal.valueOf(amountMinor).movePointLeft(2).setScale(2).toPlainString())
        .appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", note.take(40))
        .build()
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "No UPI app found on this phone", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun Radar(modifier: Modifier = Modifier, color: Color = Palette.Lime) {
    val t = rememberInfiniteTransition(label = "radar")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "p")
    Canvas(modifier) {
        for (k in 0..2) {
            val f = (p + k / 3f) % 1f
            drawCircle(color, radius = size.minDimension / 2 * f, alpha = (1f - f) * 0.5f)
        }
        drawCircle(color, radius = size.minDimension * 0.08f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSheet(vm: AppViewModel, g: GroupSummary, onDismiss: () -> Unit) {
    val s by vm.syncState.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val isHostPhone = g.group.hostDeviceId == profile.deviceId
    val activeHere = s.groupId == g.group.id && s.role != SyncEngine.Role.IDLE
    val startHosting = rememberNearbyPermission { vm.sync.host(g.group.id) }
    val reconnect = rememberNearbyPermission { vm.sync.search(g.group.id) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Invite & sync", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(14.dp))
            CloudCard(vm, g)
            Spacer(Modifier.height(20.dp))
            Text("Nearby (Bluetooth)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Phones sync directly over Bluetooth & Wi-Fi. No internet needed.",
                style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))

            if (isHostPhone) {
                if (activeHere && s.role == SyncEngine.Role.HOST) {
                    Box(Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(14.dp)) {
                        QrImage(inviteText(g), Modifier.size(200.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Group code", style = MaterialTheme.typography.labelMedium, color = Veylo.colors.muted)
                    Text(g.group.joinCode, style = MaterialTheme.typography.displaySmall, letterSpacing = 8.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Friends: open VEYLO → + → Join a nearby group, then scan this QR or type the code.",
                        style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    PeersCard(s)
                    Spacer(Modifier.height(14.dp))
                    VButton("Stop hosting", { vm.sync.stop() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Stop, style = ButtonStyle.Soft)
                } else {
                    Radar(Modifier.size(120.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("You're the host of this group", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your phone keeps the master ledger. Start hosting so friends nearby can join and receive every expense instantly.",
                        style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    VButton("Start hosting & show invite", startHosting, Modifier.fillMaxWidth(), icon = Icons.Rounded.WifiTethering)
                }
            } else {
                val hostName = g.members.firstOrNull { it.deviceId == g.group.hostDeviceId }?.name ?: "the host"
                when {
                    activeHere && s.role == SyncEngine.Role.MEMBER -> {
                        Icon(Icons.Rounded.BluetoothConnected, null, tint = Veylo.colors.owedFg, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Connected to $hostName", style = MaterialTheme.typography.titleMedium)
                        Text("New expenses sync instantly.", style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted)
                        Spacer(Modifier.height(18.dp))
                        VButton("Disconnect", { vm.sync.stop() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Stop, style = ButtonStyle.Soft)
                    }
                    activeHere -> {
                        Radar(Modifier.size(120.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(s.message ?: "Searching…", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Text("Keep $hostName's phone nearby with hosting on.", style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted)
                        Spacer(Modifier.height(18.dp))
                        VButton("Cancel", { vm.sync.stop() }, Modifier.fillMaxWidth(), style = ButtonStyle.Soft)
                    }
                    else -> {
                        Icon(Icons.Rounded.Bluetooth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("$hostName hosts this group", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ask $hostName to start hosting, then reconnect. Anything you added offline will sync automatically.",
                            style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(18.dp))
                        VButton("Reconnect to host", reconnect, Modifier.fillMaxWidth(), icon = Icons.Rounded.Bluetooth)
                    }
                }
            }

            s.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it, color = Veylo.colors.oweFg, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Veylo.colors.oweBg).padding(10.dp),
                )
            }
        }
    }
}

/** Online (internet) sharing — the same groups the VEYLO website uses. */
@Composable
private fun CloudCard(vm: AppViewModel, g: GroupSummary) {
    val ctx = LocalContext.current
    val cloud by vm.cloudState.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    if (!cloud.enabled) {
        Text(
            "Online sync isn't set up in this build.",
            style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted,
        )
        return
    }
    VCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Cloud, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Online", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val status = when {
                !g.group.shared -> "Not shared yet"
                !cloud.online -> "Offline · ${g.unsyncedCount} waiting"
                cloud.syncing -> "Syncing…"
                g.unsyncedCount > 0 -> "${g.unsyncedCount} to upload"
                else -> "Up to date"
            }
            Text(status, style = MaterialTheme.typography.labelMedium, color = Veylo.colors.muted)
        }
        Spacer(Modifier.height(8.dp))
        if (!g.group.shared) {
            Text(
                "Share this group online so friends can join from anywhere — in this app or on the VEYLO website.",
                style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted,
            )
            Spacer(Modifier.height(12.dp))
            VButton(
                if (busy) "Uploading…" else "Share online",
                {
                    busy = true
                    vm.shareOnline(g.group.id) { r ->
                        busy = false
                        r.exceptionOrNull()?.let { Toast.makeText(ctx, it.message ?: "Couldn't share", Toast.LENGTH_LONG).show() }
                    }
                },
                Modifier.fillMaxWidth(), icon = Icons.Rounded.CloudUpload, enabled = !busy,
            )
        } else {
            val link = vm.cloud.inviteLink(g.group.joinCode)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(10.dp)) {
                    QrImage(link, Modifier.size(170.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("Invite code", style = MaterialTheme.typography.labelMedium, color = Veylo.colors.muted)
                Text(g.group.joinCode, style = MaterialTheme.typography.headlineMedium, letterSpacing = 6.sp)
                Text(
                    "Works in the app (+ → Join) and on the website.",
                    style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VButton("Sync now", { vm.syncCloudNow() }, Modifier.weight(1f), icon = Icons.Rounded.Sync, style = ButtonStyle.Soft, height = 46.dp)
                    VButton("Share link", {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Join \"${g.group.name}\" on VEYLO: $link (code ${g.group.joinCode})")
                        }
                        ctx.startActivity(Intent.createChooser(send, "Share invite"))
                    }, Modifier.weight(1f), icon = Icons.Rounded.Share, height = 46.dp)
                }
            }
        }
        cloud.error?.takeIf { g.group.shared && cloud.online }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Veylo.colors.oweFg)
        }
    }
}

@Composable
private fun PeersCard(s: SyncEngine.State) {
    VCard(Modifier.fillMaxWidth()) {
        Text("Connected phones (${s.connectedCount})", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        if (s.peers.isEmpty()) {
            Text("Waiting for friends to join…", style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.peers.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.name, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        if (p.verified) "Synced" else "Verifying…",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (p.verified) Veylo.colors.owedFg else Veylo.colors.warn,
                    )
                }
            }
        }
    }
}
