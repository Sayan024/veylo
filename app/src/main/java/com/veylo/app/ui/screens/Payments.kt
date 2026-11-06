package com.veylo.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.veylo.app.domain.Money
import com.veylo.app.domain.PayStatus
import com.veylo.app.domain.PaymentInfo
import com.veylo.app.domain.PaymentParser
import com.veylo.app.domain.Transfer
import com.veylo.app.ui.AppViewModel
import com.veylo.app.ui.GroupSummary
import com.veylo.app.ui.components.ButtonStyle
import com.veylo.app.ui.components.VButton
import com.veylo.app.ui.components.VTextField
import com.veylo.app.ui.theme.Veylo
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal

/** Builds the UPI deep link intent: upi://pay?pa=…&pn=…&am=…&cu=INR&tn=…&tr=… */
fun upiIntent(upiId: String, payeeName: String, amountMinor: Long, note: String, ref: String): Intent {
    val uri = Uri.parse("upi://pay").buildUpon()
        .appendQueryParameter("pa", upiId)
        .appendQueryParameter("pn", payeeName)
        .appendQueryParameter("am", BigDecimal.valueOf(amountMinor).movePointLeft(2).setScale(2).toPlainString())
        .appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", note.take(40))
        .appendQueryParameter("tr", ref.take(35))
        .build()
    return Intent(Intent.ACTION_VIEW, uri)
}

/** Reads what a UPI app handed back after a payment (keys differ between apps). */
fun upiResultFrom(data: Intent?): PaymentInfo? {
    val extras = data?.extras ?: return null
    extras.getString("response")?.let { return PaymentParser.parseUpiResponse(it) }
    // Some apps return separate extras instead of one "response" string.
    val joined = extras.keySet().joinToString("&") { k -> "$k=${extras.get(k)}" }
    return PaymentParser.parseUpiResponse(joined)?.takeIf { it.status != PayStatus.UNKNOWN || it.txnId != null }
}

/** Runs on-device text recognition on a screenshot and extracts payment details. */
suspend fun readPaymentScreenshot(context: Context, uri: Uri): PaymentInfo {
    val image = InputImage.fromFilePath(context, uri)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try {
        PaymentParser.parseScreenshotText(recognizer.process(image).await().text)
    } finally {
        recognizer.close()
    }
}

/**
 * Record a payment between two members. Pre-filled from the UPI app's response when available;
 * can read the transaction id, amount and status from a payment screenshot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentSheet(
    vm: AppViewModel,
    g: GroupSummary,
    transfer: Transfer,
    upiResult: PaymentInfo?,
    cameFromUpi: Boolean,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var method by remember { mutableStateOf(if (cameFromUpi) "upi" else "cash") }
    var amount by remember { mutableStateOf(Money.plain(upiResult?.amount ?: transfer.amount)) }
    var txnId by remember { mutableStateOf(upiResult?.txnId.orEmpty()) }
    var status by remember { mutableStateOf(upiResult?.status?.takeIf { it != PayStatus.UNKNOWN } ?: PayStatus.SUCCESS) }
    var proof by remember { mutableStateOf<Uri?>(null) }
    var reading by remember { mutableStateOf(false) }
    var readNote by remember {
        mutableStateOf(
            when {
                !cameFromUpi -> null
                upiResult == null -> "Your UPI app didn't send the result back (common for Google Pay). Check the payment, or read it from a screenshot."
                else -> "Read from your UPI app: ${upiResult.status.label}" + (upiResult.txnId?.let { " · Txn $it" } ?: "")
            }
        )
    }
    val parsed = Money.parse(amount)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        proof = uri
        reading = true
        scope.launch {
            val info = runCatching { readPaymentScreenshot(ctx, uri) }.getOrNull()
            reading = false
            if (info == null) {
                readNote = "Couldn't read that image. Fill in the details yourself."
                return@launch
            }
            method = "upi"
            info.txnId?.let { txnId = it }
            info.amount?.let { amount = Money.plain(it) }
            if (info.status != PayStatus.UNKNOWN) status = info.status
            readNote = buildString {
                append("From screenshot: ")
                append(listOfNotNull(info.status.takeIf { it != PayStatus.UNKNOWN }?.label, info.amount?.let { Money.format(it, g.currency) }, info.txnId?.let { "Txn $it" }).joinToString(" · ").ifBlank { "nothing recognised" })
                if (info.amount != null && info.amount != transfer.amount) append("\nNote: ${Money.format(transfer.amount, g.currency)} was owed.")
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp).verticalScroll(rememberScrollState())) {
            Text("Record payment", style = MaterialTheme.typography.headlineSmall)
            Text("${g.name(transfer.fromId)} → ${g.name(transfer.toId)}", style = MaterialTheme.typography.bodyLarge, color = Veylo.colors.muted)
            Spacer(Modifier.height(14.dp))

            readNote?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Veylo.colors.cardAlt).padding(12.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            Text("Paid with", style = MaterialTheme.typography.labelMedium, color = Veylo.colors.muted)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("upi" to "UPI", "cash" to "Cash", "other" to "Other").forEach { (k, label) ->
                    Pill(label, method == k) { method = k }
                }
            }
            Spacer(Modifier.height(14.dp))
            VTextField(amount, { amount = it }, "Amount", prefix = Money.symbol(g.currency), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))

            if (method == "upi") {
                Spacer(Modifier.height(12.dp))
                VTextField(
                    txnId, { txnId = it.filter { c -> c.isLetterOrDigit() }.take(35) }, "UPI transaction ID / UTR (optional)",
                    placeholder = "12-digit reference", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                Spacer(Modifier.height(12.dp))
                Text("Status", style = MaterialTheme.typography.labelMedium, color = Veylo.colors.muted)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(PayStatus.SUCCESS, status) { status = it }
                    StatusPill(PayStatus.PENDING, status) { status = it }
                    StatusPill(PayStatus.FAILED, status) { status = it }
                }
                Spacer(Modifier.height(12.dp))
                VButton(
                    if (reading) "Reading screenshot…" else if (proof == null) "Read from payment screenshot" else "Use a different screenshot",
                    { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    Modifier.fillMaxWidth(), icon = Icons.Rounded.DocumentScanner, style = ButtonStyle.Soft, enabled = !reading,
                )
                proof?.let {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp))) {
                        AsyncImage(it, "Payment screenshot", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (status == PayStatus.PENDING) {
                    Spacer(Modifier.height(8.dp))
                    Text("Pending payments don't change balances until someone taps \"Mark received\".", style = MaterialTheme.typography.bodySmall, color = Veylo.colors.muted)
                }
            }

            Spacer(Modifier.height(18.dp))
            val failed = method == "upi" && status == PayStatus.FAILED
            VButton(
                if (failed) "Payment failed — nothing to record" else if (method == "upi" && status == PayStatus.PENDING) "Save as pending" else "Mark as settled",
                {
                    vm.settle(
                        g.group.id, transfer.fromId, transfer.toId, parsed!!,
                        method = method, txnId = txnId.takeIf { method == "upi" }, payStatus = if (method == "upi") status.key else "success",
                        proof = proof.takeIf { method == "upi" },
                    )
                    onDismiss()
                },
                Modifier.fillMaxWidth(), style = ButtonStyle.Forest, enabled = !failed && parsed != null && parsed > 0 && !reading,
            )
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) com.veylo.app.ui.theme.Palette.Forest else Veylo.colors.card)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
private fun StatusPill(s: PayStatus, current: PayStatus, onPick: (PayStatus) -> Unit) {
    val (icon, color) = when (s) {
        PayStatus.SUCCESS -> Icons.Rounded.CheckCircle to Veylo.colors.owedFg
        PayStatus.PENDING -> Icons.Rounded.Schedule to Veylo.colors.warn
        else -> Icons.Rounded.ErrorOutline to Veylo.colors.oweFg
    }
    val sel = s == current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (sel) color.copy(alpha = 0.16f) else Veylo.colors.card)
            .clickable { onPick(s) }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(s.label, style = MaterialTheme.typography.labelLarge, color = if (sel) color else MaterialTheme.colorScheme.onSurface)
    }
}
