package com.veylo.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.veylo.app.data.sync.SyncEngine
import com.veylo.app.ui.AppViewModel
import com.veylo.app.ui.components.ButtonStyle
import com.veylo.app.ui.components.IconCircle
import com.veylo.app.ui.components.VButton
import com.veylo.app.ui.components.VCard
import com.veylo.app.ui.components.VTextField
import com.veylo.app.ui.navigation.Nav
import com.veylo.app.ui.theme.Palette
import com.veylo.app.ui.theme.Veylo

@Composable
fun JoinScreen(vm: AppViewModel, nav: Nav) {
    val s by vm.syncState.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var codeFor by remember { mutableStateOf<SyncEngine.FoundGroup?>(null) }
    var started by remember { mutableStateOf(false) }
    val startSearch = rememberNearbyPermission { started = true; vm.sync.search() }

    LaunchedEffect(Unit) { startSearch() }
    com.veylo.app.ui.components.DarkHeaderStatusBar()

    // Joined → open the group.
    LaunchedEffect(s.role, s.groupId) {
        if (started && s.role == SyncEngine.Role.MEMBER && s.groupId != null) nav.groupReplacing(s.groupId!!)
    }
    // Leaving without joining stops discovery.
    DisposableEffect(Unit) {
        onDispose {
            val st = vm.syncState.value
            if (st.role == SyncEngine.Role.SEARCHING && st.groupId == null) vm.sync.stop()
        }
    }

    fun scanQr() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(ctx, options).startScan()
            .addOnSuccessListener { b ->
                val parts = b.rawValue.orEmpty().split(":")
                if (parts.size == 3 && parts[0] == "veylo") {
                    vm.sync.useInvite(parts[1], parts[2])
                    Toast.makeText(ctx, "Invite scanned — connecting when the host is found", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "That's not a VEYLO invite", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { Toast.makeText(ctx, "Scanner unavailable: ${it.message}", Toast.LENGTH_SHORT).show() }
    }

    Column(Modifier.fillMaxSize().background(Palette.ForestDeep)) {
        Row(Modifier.statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, { nav.back() }, bg = androidx.compose.ui.graphics.Color(0x22FFFFFF), tint = androidx.compose.ui.graphics.Color.White)
            Text("Join nearby group", style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(start = 12.dp))
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                Radar(Modifier.fillMaxSize())
            }
            Text(
                when (s.role) {
                    SyncEngine.Role.CONNECTING -> s.message ?: "Connecting…"
                    SyncEngine.Role.SEARCHING -> if (s.found.isEmpty()) "Looking for nearby hosts…" else "Groups nearby"
                    SyncEngine.Role.MEMBER -> "Joined!"
                    else -> "Nearby search is off"
                },
                style = MaterialTheme.typography.headlineSmall, color = androidx.compose.ui.graphics.Color.White,
            )
            Text(
                "Ask the trip host to open the group and tap the QR icon to start hosting. Bluetooth must be on for both phones.",
                style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xAAFFFFFF),
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                s.found.forEach { f ->
                    VCard(Modifier.fillMaxWidth(), onClick = { codeFor = f }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Lime), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Groups, null, tint = Palette.ForestDeep)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.groupName, style = MaterialTheme.typography.titleMedium)
                                Text("Tap to join with code", style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted)
                            }
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Veylo.colors.muted)
                        }
                    }
                }
            }

            s.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it, color = Veylo.colors.oweFg, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Veylo.colors.oweBg).padding(10.dp)
                        .clickable { vm.sync.clearError() },
                )
            }
        }

        Column(Modifier.navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            VButton("Scan invite QR", { scanQr() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.QrCodeScanner)
            if (s.role == SyncEngine.Role.IDLE) {
                VButton("Search again", { startSearch() }, Modifier.fillMaxWidth(), style = ButtonStyle.Soft)
            }
        }
    }

    codeFor?.let { f ->
        var code by remember(f) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { codeFor = null },
            title = { Text("Join ${f.groupName}") },
            text = {
                VTextField(
                    code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) }, "6-character group code",
                    placeholder = "ABC123",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            },
            confirmButton = {
                TextButton({ vm.sync.connect(f.endpointId, code); codeFor = null }, enabled = code.length == 6) { Text("Join") }
            },
            dismissButton = { TextButton({ codeFor = null }) { Text("Cancel") } },
        )
    }
}
