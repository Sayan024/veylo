package com.veylo.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.veylo.app.data.local.GroupType
import com.veylo.app.domain.Money
import com.veylo.app.ui.AppViewModel
import com.veylo.app.ui.components.Avatar
import com.veylo.app.ui.components.IconCircle
import com.veylo.app.ui.components.MountainArt
import com.veylo.app.ui.components.VButton
import com.veylo.app.ui.components.VCard
import com.veylo.app.ui.components.VTextField
import com.veylo.app.ui.components.formatDate
import com.veylo.app.ui.navigation.Nav
import com.veylo.app.ui.theme.Palette
import com.veylo.app.ui.theme.Veylo
import com.veylo.app.domain.TripTheme
import com.veylo.app.ui.components.TripArt
import com.veylo.app.ui.components.accent
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateGroupScreen(vm: AppViewModel, nav: Nav, type: String) {
    val isTrip = type == GroupType.TRIP
    val profile by vm.profile.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("INR") }
    var theme by rememberSaveable { mutableStateOf(if (isTrip) TripTheme.VACATION else TripTheme.CAFE) }
    var styleTouched by rememberSaveable { mutableStateOf(false) }
    var cover by remember { mutableStateOf<Uri?>(null) }
    var start by rememberSaveable { mutableStateOf<Long?>(null) }
    var end by rememberSaveable { mutableStateOf<Long?>(null) }
    var memberInput by rememberSaveable { mutableStateOf("") }
    val members = remember { mutableStateListOf<String>() }
    var showDates by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) cover = uri }
    com.veylo.app.ui.components.DarkHeaderStatusBar()
    LaunchedEffect(destination, name) {
        if (!styleTouched) theme = TripTheme.detect(destination, name, !isTrip)
    }

    fun addMember() {
        val n = memberInput.trim()
        if (n.isNotEmpty() && members.none { it.equals(n, true) } && !n.equals(profile.name, true)) members.add(n)
        memberInput = ""
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Cover
            Box(Modifier.fillMaxWidth().height(230.dp).clickable {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                if (cover != null) {
                    AsyncImage(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    TripArt(theme, name.ifBlank { type }, Modifier.fillMaxSize())
                }
                Row(Modifier.statusBarsPadding().padding(16.dp)) {
                    IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, { nav.back() }, bg = Color(0x66000000), tint = Color.White)
                }
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(16.dp).clip(RoundedCornerShape(50))
                        .background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (cover == null) "Add cover photo" else "Change photo", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }

            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (isTrip) "Create Trip" else "Start Outing", style = MaterialTheme.typography.headlineLarge)
                VTextField(
                    name, { name = it.take(40) }, if (isTrip) "Trip name" else "Outing name",
                    placeholder = if (isTrip) "Darjeeling Trip" else "Café Weekend",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                VTextField(
                    destination, { destination = it.take(40) }, if (isTrip) "Destination" else "Place",
                    placeholder = if (isTrip) "Darjeeling, West Bengal" else "Blue Tokai, Park Street",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )

                if (isTrip) {
                    Column {
                        Text("Dates", style = MaterialTheme.typography.labelMedium, color = Veylo.colors.muted)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Veylo.colors.card)
                                .clickable { showDates = true }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (start != null) "${formatDate(start!!)}  →  ${end?.let { formatDate(it) } ?: "…"}" else "Select start & end date",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (start != null) MaterialTheme.colorScheme.onSurface else Veylo.colors.muted,
                            )
                        }
                    }
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Trip style", style = MaterialTheme.typography.labelMedium, color = Veylo.colors.muted, modifier = Modifier.weight(1f))
                        if (!styleTouched && (destination.isNotBlank() || name.isNotBlank())) {
                            Text("Auto-detected", style = MaterialTheme.typography.labelSmall, color = theme.accent())
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TripTheme.entries.forEach { t ->
                            val sel = t == theme
                            Text(
                                "${t.emoji}  ${t.label}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clip(RoundedCornerShape(50))
                                    .background(if (sel) t.accent() else Veylo.colors.card)
                                    .clickable { theme = t; styleTouched = true }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                }

                Column {
                    Text("Currency", style = MaterialTheme.typography.labelMedium, color = Veylo.colors.muted)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Money.currencies.forEach { c ->
                            val sel = c == currency
                            Text(
                                "${Money.symbol(c).trim()}  $c",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (sel) Palette.ForestDeep else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clip(RoundedCornerShape(50))
                                    .background(if (sel) Palette.Lime else Veylo.colors.card)
                                    .clickable { currency = c }.padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                }

                VCard {
                    Text("Participants", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add friends by name now. When they join over Bluetooth with the same name, they're linked automatically.",
                        style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    MemberChipRow(profile.name.ifBlank { "You" } + " (you)", null)
                    members.forEach { m -> MemberChipRow(m) { members.remove(m) } }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        VTextField(
                            memberInput, { memberInput = it.take(30) }, "Add a friend",
                            Modifier.weight(1f), placeholder = "Rahul",
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconCircle(Icons.Rounded.Add, { addMember() }, bg = Palette.Lime, tint = Palette.ForestDeep, size = 54.dp)
                    }
                }
                Text(
                    "Tip: you can also invite people later via QR code or nearby Bluetooth.",
                    style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted,
                )
            }
        }

        Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            VButton(
                if (isTrip) "Create Trip" else "Start Outing",
                onClick = {
                    if (memberInput.isNotBlank()) addMember()
                    saving = true
                    vm.createGroup(name, type, destination, currency, cover, start, end, members.toList(), theme.key) { id ->
                        nav.groupReplacing(id)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && !saving,
            )
        }
    }

    if (showDates) {
        val state = rememberDateRangePickerState(initialSelectedStartDateMillis = start, initialSelectedEndDateMillis = end)
        DatePickerDialog(
            onDismissRequest = { showDates = false },
            confirmButton = {
                TextButton({
                    start = state.selectedStartDateMillis
                    end = state.selectedEndDateMillis
                    showDates = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton({ showDates = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state, Modifier.height(480.dp), title = null, headline = null, showModeToggle = false)
        }
    }
}

@Composable
private fun MemberChipRow(name: String, onRemove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name.removeSuffix(" (you)"), size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (onRemove != null) {
            Icon(
                Icons.Rounded.Close, "Remove", tint = Veylo.colors.muted,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(50)).clickable(onClick = onRemove).padding(4.dp),
            )
        }
    }
}
