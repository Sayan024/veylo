package com.veylo.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.veylo.app.data.local.GroupType
import com.veylo.app.domain.Money
import com.veylo.app.domain.SplitCalculator
import com.veylo.app.domain.SplitInput
import com.veylo.app.domain.SplitMethod
import com.veylo.app.domain.SplitResult
import com.veylo.app.ui.AppViewModel
import com.veylo.app.ui.components.Avatar
import com.veylo.app.ui.components.EmptyState
import com.veylo.app.ui.components.IconCircle
import com.veylo.app.ui.components.VButton
import com.veylo.app.ui.components.VCard
import com.veylo.app.ui.components.VTextField
import com.veylo.app.ui.components.categories
import com.veylo.app.ui.navigation.Nav
import com.veylo.app.ui.theme.Palette
import com.veylo.app.ui.theme.Veylo
import kotlinx.coroutines.launch

@Composable
fun AddExpenseScreen(vm: AppViewModel, nav: Nav, initialGroupId: String?, expenseId: String?) {
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    val scope = rememberCoroutineScope()

    var groupId by rememberSaveable { mutableStateOf(initialGroupId ?: d.groups.firstOrNull()?.group?.id) }
    val g = d.group(groupId)

    var title by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var payerId by rememberSaveable(groupId) { mutableStateOf(g?.me?.id ?: g?.members?.firstOrNull()?.id) }
    var category by rememberSaveable { mutableStateOf("FOOD") }
    var method by rememberSaveable { mutableStateOf(SplitMethod.EQUAL) }
    var receipt by rememberSaveable { mutableStateOf<String?>(null) }
    val included = remember(groupId) { mutableStateMapOf<String, Boolean>() }
    val values = remember(groupId) { mutableStateMapOf<String, String>() }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var payerMenu by remember { mutableStateOf(false) }
    var methodMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // Prefill when editing.
    LaunchedEffect(expenseId, d) {
        if (loaded || expenseId == null) return@LaunchedEffect
        val eg = d.groups.firstOrNull { s -> s.expenses.any { it.id == expenseId } } ?: return@LaunchedEffect
        val e = eg.expenses.first { it.id == expenseId }
        groupId = eg.group.id
        title = e.title
        amountText = Money.plain(e.amount)
        payerId = e.payerId
        category = e.category
        method = SplitMethod.of(e.splitMethod)
        receipt = e.receiptUri
        val splits = eg.splits[e.id].orEmpty()
        eg.members.forEach { m ->
            val s = splits.firstOrNull { it.memberId == m.id }
            included[m.id] = s != null
            if (s != null) values[m.id] = when (method) {
                SplitMethod.EXACT -> Money.plain(s.share)
                SplitMethod.EQUAL -> ""
                else -> s.input.toBigDecimal().stripTrailingZeros().toPlainString()
            }
        }
        loaded = true
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { receipt = vm.importImage(uri) }
    }

    if (g == null) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(20.dp)) {
            IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, { nav.back() })
            Spacer(Modifier.weight(1f))
            EmptyState(Icons.Rounded.LocalCafe, "Create a group first", "Expenses live inside a trip or an outing.") {
                VButton("Start Outing", { nav.back(); nav.create(GroupType.OUTING) })
            }
            Spacer(Modifier.weight(1f))
        }
        return
    }

    val members = g.members
    fun isIncluded(id: String) = included[id] ?: true
    val amount = Money.parse(amountText) ?: 0L
    val inputs = members.map { m ->
        val raw = values[m.id].orEmpty()
        val v = when (method) {
            SplitMethod.EXACT -> (Money.parse(raw) ?: 0L).toDouble()
            SplitMethod.EQUAL -> 0.0
            SplitMethod.SHARES -> raw.toDoubleOrNull() ?: if (raw.isBlank()) 1.0 else -1.0
            SplitMethod.PERCENT -> raw.toDoubleOrNull() ?: if (raw.isBlank()) 0.0 else -1.0
        }
        SplitInput(m.id, isIncluded(m.id), v)
    }
    val result = SplitCalculator.calculate(amount, method, inputs, g.currency)
    val shares = (result as? SplitResult.Ok)?.shares.orEmpty()
    val canSave = title.isNotBlank() && amount > 0 && payerId != null && result is SplitResult.Ok && !saving

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, { nav.back() })
            Text(
                if (expenseId != null) "Edit Expense" else "Add Expense",
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            IconCircle(Icons.Rounded.CameraAlt, {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, contentDescription = "Attach receipt")
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Group picker (when launched from the + menu)
            if (initialGroupId == null && expenseId == null && d.groups.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.groups.forEach { s ->
                        val sel = s.group.id == groupId
                        Text(
                            s.group.name, style = MaterialTheme.typography.labelLarge,
                            color = if (sel) Palette.ForestDeep else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) Palette.Lime else Veylo.colors.card)
                                .clickable { groupId = s.group.id }.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }

            // Big amount
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Amount", style = MaterialTheme.typography.labelMedium, color = Veylo.colors.muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Money.symbol(g.currency), style = MaterialTheme.typography.displayMedium, color = Veylo.colors.muted)
                    BasicTextField(
                        value = amountText,
                        onValueChange = { t -> if (t.length <= 12 && t.all { it.isDigit() || it == '.' || it == ',' }) amountText = t },
                        textStyle = MaterialTheme.typography.displayMedium.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.width(220.dp),
                        decorationBox = { inner ->
                            Box {
                                if (amountText.isEmpty()) Text("0", style = MaterialTheme.typography.displayMedium, color = Veylo.colors.divider)
                                inner()
                            }
                        },
                    )
                }
            }

            VTextField(
                title, { title = it.take(50) }, "Title", placeholder = "Café bill",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            // Category chips
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { c ->
                    val sel = c.key == category
                    Row(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (sel) c.tint.copy(alpha = 0.18f) else Veylo.colors.card)
                            .clickable { category = c.key }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(c.icon, null, tint = c.tint, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(c.label, style = MaterialTheme.typography.labelMedium, color = if (sel) c.tint else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Paid by
            Column {
                Text("Paid by", style = MaterialTheme.typography.labelMedium, color = Veylo.colors.muted)
                Spacer(Modifier.height(6.dp))
                Box {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Veylo.colors.card)
                            .clickable { payerMenu = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val payer = members.firstOrNull { it.id == payerId }
                        Avatar(payer?.name ?: "?", size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(payer?.let { g.name(it.id) } ?: "Select", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Veylo.colors.muted)
                    }
                    DropdownMenu(payerMenu, { payerMenu = false }) {
                        members.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(g.name(m.id)) },
                                leadingIcon = { Avatar(m.name, size = 28.dp) },
                                onClick = { payerId = m.id; payerMenu = false },
                            )
                        }
                    }
                }
            }

            // Split
            VCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Split between", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Box {
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(Veylo.colors.cardAlt)
                                .clickable { methodMenu = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(method.label, style = MaterialTheme.typography.labelMedium)
                            Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(methodMenu, { methodMenu = false }) {
                            SplitMethod.entries.forEach { sm ->
                                DropdownMenuItem({ Text(sm.label) }, { method = sm; methodMenu = false })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                members.forEach { m ->
                    val inc = isIncluded(m.id)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(m.name, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name(m.id), style = MaterialTheme.typography.bodyLarge)
                            if (inc && shares[m.id] != null) {
                                Text(Money.format(shares.getValue(m.id), g.currency), style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted)
                            }
                        }
                        if (inc && method != SplitMethod.EQUAL) {
                            SmallNumberField(
                                values[m.id].orEmpty(), { values[m.id] = it },
                                placeholder = when (method) { SplitMethod.SHARES -> "1"; SplitMethod.PERCENT -> "%"; else -> "0" },
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Checkbox(
                            checked = inc, onCheckedChange = { included[m.id] = it },
                            colors = CheckboxDefaults.colors(checkedColor = Palette.Forest, checkmarkColor = Palette.Lime),
                        )
                    }
                }
                if (amount > 0 && result is SplitResult.Error) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        result.message, style = MaterialTheme.typography.bodySmall, color = Veylo.colors.oweFg,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Veylo.colors.oweBg).padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            receipt?.let { r ->
                Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp))) {
                    AsyncImage(r, "Receipt", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    IconCircle(Icons.Rounded.Close, { receipt = null }, Modifier.align(Alignment.TopEnd).padding(8.dp), size = 34.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            VButton(
                if (expenseId != null) "Save changes" else "Add Expense",
                onClick = {
                    val pid = payerId ?: return@VButton
                    saving = true
                    val inputMap = inputs.filter { it.included }.associate { it.memberId to it.value }
                    vm.saveExpense(expenseId, g.group.id, title, amount, pid, category, method.name, receipt, shares, inputMap) {
                        nav.back()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave,
            )
        }
    }
}

@Composable
private fun SmallNumberField(value: String, onChange: (String) -> Unit, placeholder: String) {
    BasicTextField(
        value = value,
        onValueChange = { t -> if (t.length <= 9 && t.all { it.isDigit() || it == '.' }) onChange(t) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.width(84.dp).clip(RoundedCornerShape(10.dp)).background(Veylo.colors.cardAlt).padding(horizontal = 10.dp, vertical = 8.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterEnd) {
                if (value.isEmpty()) Text(placeholder, color = Veylo.colors.muted, style = MaterialTheme.typography.bodyLarge)
                inner()
            }
        },
    )
}
