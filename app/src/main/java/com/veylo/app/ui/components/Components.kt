package com.veylo.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.veylo.app.data.local.GroupEntity
import com.veylo.app.data.local.GroupType
import com.veylo.app.data.local.SyncStatus
import com.veylo.app.ui.theme.Palette
import com.veylo.app.ui.theme.Veylo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ------------------------------------------------------------------ Buttons

enum class ButtonStyle { Lime, Forest, Soft }

@Composable
fun VButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: ButtonStyle = ButtonStyle.Lime,
    enabled: Boolean = true,
    height: Dp = 56.dp,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    val (bg, fg) = when (style) {
        ButtonStyle.Lime -> Veylo.colors.lime to Veylo.colors.onLime
        ButtonStyle.Forest -> Palette.Forest to Color.White
        ButtonStyle.Soft -> Veylo.colors.cardAlt to MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .scale(scale)
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .clickable(interaction, null, enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg.copy(alpha = if (enabled) 1f else 0.6f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg.copy(alpha = if (enabled) 1f else 0.6f), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun IconCircle(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = Veylo.colors.card,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 44.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.48f)) }
}

// ------------------------------------------------------------------ Cards & layout

@Composable
fun VCard(
    modifier: Modifier = Modifier,
    color: Color = Veylo.colors.card,
    padding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                "$action  →",
                style = MaterialTheme.typography.labelMedium,
                color = Veylo.colors.muted,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp),
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Veylo.colors.limeTint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Veylo.colors.muted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Veylo.colors.cardAlt).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) Palette.Forest else Color.Transparent, label = "tab")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(bg)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelMedium,
                    color = if (sel) Color.White else Veylo.colors.muted,
                )
            }
        }
    }
}

@Composable
fun VTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefix: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    isError: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Veylo.colors.muted)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder?.let { { Text(it, color = Veylo.colors.muted.copy(alpha = 0.7f)) } },
            prefix = prefix?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            isError = isError,
            trailingIcon = trailing,
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Veylo.colors.card,
                unfocusedContainerColor = Veylo.colors.card,
                unfocusedBorderColor = Veylo.colors.divider,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

// ------------------------------------------------------------------ Avatars

private val avatarColors = listOf(
    Color(0xFF2B5A40), Color(0xFFB4683B), Color(0xFF4A6FA5), Color(0xFF8A4F7D),
    Color(0xFF3F7F74), Color(0xFFA0522D), Color(0xFF5B6B2F), Color(0xFF7A5C3E),
)

fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp, ring: Color? = null) {
    val color = avatarColors[abs(name.lowercase().hashCode()) % avatarColors.size]
    Box(
        modifier
            .size(size)
            .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(name), color = Color.White, fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38f).sp, fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        )
    }
}

@Composable
fun AvatarStack(names: List<String>, size: Dp = 32.dp, max: Int = 4, ring: Color = Veylo.colors.card) {
    Row {
        names.take(max).forEachIndexed { i, n ->
            Avatar(n, Modifier.offset(x = (-10 * i).dp), size = size, ring = ring)
        }
        if (names.size > max) {
            Box(
                Modifier.offset(x = (-10 * max).dp).size(size).border(2.dp, ring, CircleShape).clip(CircleShape)
                    .background(Veylo.colors.cardAlt),
                contentAlignment = Alignment.Center,
            ) { Text("+${names.size - max}", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

// ------------------------------------------------------------------ Brand art

@Composable
fun VeyloLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.2f
        drawLine(Palette.LimeDeep, Offset(w * 0.18f, w * 0.22f), Offset(w * 0.46f, w * 0.78f), stroke, StrokeCap.Round)
        drawLine(Palette.Lime, Offset(w * 0.46f, w * 0.78f), Offset(w * 0.84f, w * 0.16f), stroke, StrokeCap.Round)
    }
}

/** Layered mountain illustration used for trips without a photo. Deterministic per seed. */
@Composable
fun MountainArt(seed: String, modifier: Modifier = Modifier, outing: Boolean = false) {
    val h = abs(seed.hashCode())
    // Trips: misty green hills. Outings: soft golden-hour hills that still sit in the forest palette.
    val sky = if (outing) listOf(Color(0xFFF4E6CC), Color(0xFFDCE8C4)) else listOf(Color(0xFFB9D3BE), Color(0xFFE6EFDD))
    val layers = if (outing) listOf(Color(0xFF9DBB7A), Color(0xFF4F7A52), Palette.Forest)
    else listOf(Color(0xFF6E9A7B), Color(0xFF2B5A40), Palette.ForestDeep)
    Canvas(modifier) {
        val w = size.width
        val ht = size.height
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(sky))
        // Sun sits low and left so it never hides behind header buttons.
        drawCircle(
            if (outing) Color(0xFFFFD27A) else Color(0xFFFBFDF3),
            radius = minOf(w, ht) * 0.11f,
            center = Offset(w * (0.30f + (h % 25) / 100f), ht * 0.42f),
            alpha = 0.95f,
        )
        layers.forEachIndexed { i, c ->
            val base = ht * (0.48f + i * 0.17f)
            val p = Path().apply {
                moveTo(0f, ht)
                lineTo(0f, base)
                val peaks = 3 + i
                for (k in 0..peaks) {
                    val x = w * k / peaks
                    val jitter = ((h shr (k + i * 3)) and 0x1F) / 31f
                    val peakY = base - ht * (0.12f + 0.16f * jitter) / (i * 0.4f + 1f)
                    val midX = x - w / peaks / 2f
                    if (k > 0) quadraticTo(midX, peakY - ht * 0.05f, x, base - ht * 0.03f * jitter)
                }
                lineTo(w, ht)
                close()
            }
            drawPath(p, c)
        }
    }
}

@Composable
fun GroupCover(group: GroupEntity, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (group.coverUri != null) {
            AsyncImage(
                model = group.coverUri, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        } else {
            TripArt(com.veylo.app.domain.TripTheme.of(group.theme), group.id, Modifier.fillMaxSize())
        }
    }
}

// ------------------------------------------------------------------ Status & categories

data class StatusInfo(val label: String, val icon: ImageVector, val color: @Composable () -> Color)

fun statusInfo(status: String): StatusInfo = when (status) {
    SyncStatus.SYNCED -> StatusInfo("Synced", Icons.Rounded.CheckCircle) { Veylo.colors.owedFg }
    SyncStatus.SYNCING -> StatusInfo("Syncing", Icons.Rounded.Sync) { Veylo.colors.warn }
    SyncStatus.PENDING -> StatusInfo("Pending sync", Icons.Rounded.Schedule) { Veylo.colors.warn }
    SyncStatus.FAILED -> StatusInfo("Sync failed", Icons.Rounded.ErrorOutline) { Veylo.colors.oweFg }
    else -> StatusInfo("On this phone", Icons.Rounded.PhoneAndroid) { Veylo.colors.muted }
}

@Composable
fun SyncPill(status: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val info = statusInfo(status)
    val c = info.color()
    if (compact) {
        Icon(info.icon, info.label, tint = c, modifier = modifier.size(14.dp))
        return
    }
    Row(
        modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(info.icon, null, tint = c, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(info.label, style = MaterialTheme.typography.labelSmall, color = c)
    }
}

data class Category(val key: String, val label: String, val icon: ImageVector, val tint: Color)

val categories = listOf(
    Category("FOOD", "Food", Icons.Rounded.Restaurant, Color(0xFFE07A3F)),
    Category("CAFE", "Café", Icons.Rounded.LocalCafe, Color(0xFF8B5E3C)),
    Category("STAY", "Stay", Icons.Rounded.Hotel, Color(0xFFD45C7A)),
    Category("TRAVEL", "Travel", Icons.Rounded.DirectionsCar, Color(0xFF2F8F5B)),
    Category("SHOPPING", "Shopping", Icons.Rounded.ShoppingBag, Color(0xFF5B6FD4)),
    Category("FUN", "Fun", Icons.Rounded.Celebration, Color(0xFFB0659E)),
    Category("OTHER", "Other", Icons.Rounded.Receipt, Color(0xFF6E766F)),
)

fun category(key: String?) = categories.firstOrNull { it.key == key } ?: categories.last()

@Composable
fun CategoryBadge(key: String?, size: Dp = 44.dp, settlement: Boolean = false) {
    val c = if (settlement) Category("SETTLE", "Settled", Icons.Rounded.CheckCircle, Veylo.colors.owedFg) else category(key)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(c.tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(c.icon, c.label, tint = c.tint, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun OfflineBadge(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color(0x33000000)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CloudOff, null, tint = Color.White, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Use white status-bar icons while this screen (with a dark photo header) is shown. */
@Composable
fun DarkHeaderStatusBar() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val ctl = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val before = ctl?.isAppearanceLightStatusBars ?: false
        ctl?.isAppearanceLightStatusBars = false
        onDispose { ctl?.isAppearanceLightStatusBars = before }
    }
}

// ------------------------------------------------------------------ Misc helpers

fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

fun formatDate(t: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(t))
fun formatDateTime(t: Long): String = SimpleDateFormat("d MMM yyyy · h:mm a", Locale.getDefault()).format(Date(t))
fun formatTime(t: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t))

fun dayLabel(t: Long): String {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val sameYear = now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
    val diff = now.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && diff == 0 -> "Today"
        sameYear && diff == 1 -> "Yesterday"
        else -> SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date(t))
    }
}

fun qrBitmap(text: String, px: Int = 600): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(px * px) { i -> if (matrix[i % px, i / px]) 0xFF0F2A1D.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
fun QrImage(text: String, modifier: Modifier = Modifier) {
    val bmp = remember(text) { qrBitmap(text) }
    Image(bmp, "Invite QR code", modifier)
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun RingProgress(progress: Float, modifier: Modifier = Modifier, color: Color = Palette.Lime) {
    val track = Veylo.colors.divider
    Canvas(modifier) {
        val s = Stroke(width = size.width * 0.14f, cap = StrokeCap.Round)
        drawArc(track, -90f, 360f, false, style = s)
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, style = s)
    }
}
