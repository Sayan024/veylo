package com.veylo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.veylo.app.domain.TripTheme
import kotlin.math.abs

/** Accent colour for a trip style (header chip, totals card, history icons). */
fun TripTheme.accent(): Color = when (this) {
    TripTheme.HILLS -> Color(0xFF2F7A4A)
    TripTheme.BEACH -> Color(0xFF0E9AA7)
    TripTheme.BUSINESS -> Color(0xFF3D5A9E)
    TripTheme.VACATION -> Color(0xFFE07A3F)
    TripTheme.CAFE -> Color(0xFF8B5E3C)
}

/** Cover illustration for a trip without a photo. Deterministic per seed. */
@Composable
fun TripArt(theme: TripTheme, seed: String, modifier: Modifier = Modifier) {
    when (theme) {
        TripTheme.HILLS -> MountainArt(seed, modifier)
        TripTheme.CAFE -> MountainArt(seed, modifier, outing = true)
        TripTheme.BEACH -> Canvas(modifier) { drawBeach(abs(seed.hashCode())) }
        TripTheme.BUSINESS -> Canvas(modifier) { drawCity(abs(seed.hashCode())) }
        TripTheme.VACATION -> Canvas(modifier) { drawSunset(abs(seed.hashCode())) }
    }
}

@Composable
fun ThemeChip(theme: TripTheme, modifier: Modifier = Modifier, onDark: Boolean = true) {
    Row(
        modifier.clip(RoundedCornerShape(50))
            .background(if (onDark) Color(0x33000000) else theme.accent().copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${theme.emoji}  ${theme.label}",
            style = MaterialTheme.typography.labelMedium,
            color = if (onDark) Color.White else theme.accent(),
        )
    }
}


// ------------------------------------------------------------------ Beach

private fun DrawScope.drawBeach(h: Int) {
    val w = size.width
    val ht = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF8ED8E8), Color(0xFFDDF4F2))))
    drawCircle(Color(0xFFFFE08A), radius = ht * 0.11f, center = Offset(w * (0.28f + (h % 20) / 100f), ht * 0.30f))
    // Sea with two wave bands
    val horizon = ht * 0.52f
    drawRect(Color(0xFF1BA8B5), topLeft = Offset(0f, horizon), size = Size(w, ht - horizon))
    for ((i, c) in listOf(Color(0xFF34C3C9), Color(0xFF7FDAD6)).withIndex()) {
        val y = horizon + ht * (0.08f + i * 0.08f)
        val p = Path().apply {
            moveTo(0f, y)
            var x = 0f
            val step = w / 6f
            while (x < w) {
                quadraticTo(x + step / 2, y - ht * 0.025f, x + step, y)
                x += step
            }
            lineTo(w, ht); lineTo(0f, ht); close()
        }
        drawPath(p, c)
    }
    // Sand
    val sand = Path().apply {
        moveTo(0f, ht * 0.80f)
        quadraticTo(w * 0.5f, ht * 0.70f, w, ht * 0.84f)
        lineTo(w, ht); lineTo(0f, ht); close()
    }
    drawPath(sand, Color(0xFFF2D7A6))
    // Palm tree
    val baseX = w * 0.80f
    val trunk = Path().apply {
        moveTo(baseX, ht * 0.86f)
        quadraticTo(baseX - w * 0.05f, ht * 0.55f, baseX - w * 0.015f, ht * 0.30f)
    }
    drawPath(trunk, Color(0xFF7A5230), style = Stroke(width = w * 0.022f, cap = StrokeCap.Round))
    val top = Offset(baseX - w * 0.015f, ht * 0.30f)
    for (angle in listOf(-160f, -120f, -60f, -20f, 20f)) {
        val rad = Math.toRadians(angle.toDouble())
        val len = w * 0.13f
        val end = Offset(top.x + (len * kotlin.math.cos(rad)).toFloat(), top.y + (len * kotlin.math.sin(rad)).toFloat() + ht * 0.06f)
        val leaf = Path().apply {
            moveTo(top.x, top.y)
            quadraticTo((top.x + end.x) / 2, top.y - ht * 0.06f, end.x, end.y)
        }
        drawPath(leaf, Color(0xFF2F8F4E), style = Stroke(width = w * 0.018f, cap = StrokeCap.Round))
    }
}

// ------------------------------------------------------------------ Business (city skyline at dusk)

private fun DrawScope.drawCity(h: Int) {
    val w = size.width
    val ht = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2C3E6B), Color(0xFF6E7FB0), Color(0xFFC9CFE6))))
    val ground = ht * 0.92f
    var x = -w * 0.02f
    var i = 0
    while (x < w) {
        val bw = w * (0.07f + ((h shr (i % 16)) and 7) / 100f)
        val bh = ht * (0.28f + ((h shr ((i * 3) % 18)) and 31) / 64f)
        val shade = if (i % 2 == 0) Color(0xFF1E2B4D) else Color(0xFF263763)
        drawRect(shade, topLeft = Offset(x, ground - bh), size = Size(bw, bh))
        // lit windows
        val cols = 3
        val rows = (bh / (ht * 0.07f)).toInt().coerceAtLeast(1)
        for (r in 0 until rows) for (c in 0 until cols) {
            if (((h shr ((r + c + i) % 20)) and 3) == 0) continue
            drawRect(
                Color(0xFFFFE9A8).copy(alpha = 0.85f),
                topLeft = Offset(x + bw * (0.15f + c * 0.27f), ground - bh + ht * 0.03f + r * ht * 0.07f),
                size = Size(bw * 0.16f, ht * 0.03f),
            )
        }
        x += bw + w * 0.008f
        i++
    }
    drawRect(Color(0xFF162040), topLeft = Offset(0f, ground), size = Size(w, ht - ground))
}

// ------------------------------------------------------------------ Vacation (sunset over water)

private fun DrawScope.drawSunset(h: Int) {
    val w = size.width
    val ht = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFF9A62), Color(0xFFFFC37A), Color(0xFFFFE3B0))))
    val horizon = ht * 0.62f
    val sunX = w * (0.45f + (h % 20) / 100f)
    drawCircle(Color(0xFFFFF1C9), radius = ht * 0.16f, center = Offset(sunX, horizon))
    drawRect(Brush.verticalGradient(listOf(Color(0xFFE5704A), Color(0xFF9C3F5C)), startY = horizon, endY = ht), topLeft = Offset(0f, horizon), size = Size(w, ht - horizon))
    for (k in 0..4) {
        val y = horizon + ht * (0.05f + k * 0.06f)
        val half = w * (0.12f - k * 0.018f)
        drawLine(Color(0x99FFF1C9), Offset(sunX - half, y), Offset(sunX + half, y), strokeWidth = ht * 0.012f, cap = StrokeCap.Round)
    }
    // birds
    for (b in 0..2) {
        val bx = w * (0.18f + b * 0.09f)
        val by = ht * (0.22f + (b % 2) * 0.05f)
        val s = w * 0.025f
        val p = Path().apply {
            moveTo(bx - s, by)
            quadraticTo(bx - s / 2, by - s * 0.6f, bx, by)
            quadraticTo(bx + s / 2, by - s * 0.6f, bx + s, by)
        }
        drawPath(p, Color(0xAA5A2A3A), style = Stroke(width = w * 0.006f, cap = StrokeCap.Round))
    }
}
