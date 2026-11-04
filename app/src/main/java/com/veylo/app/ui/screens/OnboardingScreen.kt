package com.veylo.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veylo.app.ui.components.ButtonStyle
import com.veylo.app.ui.components.MountainArt
import com.veylo.app.ui.components.VButton
import com.veylo.app.ui.components.VeyloLogo
import com.veylo.app.ui.theme.Palette

@Composable
fun OnboardingScreen(onStart: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    com.veylo.app.ui.components.DarkHeaderStatusBar()

    Box(Modifier.fillMaxSize().background(Palette.ForestDeep)) {
        MountainArt("veylo-onboarding", Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x330F2A1D), 0.45f to Color(0x660F2A1D), 1f to Palette.ForestDeep,
                )
            )
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                VeyloLogo(Modifier.size(84.dp))
                Spacer(Modifier.height(6.dp))
                Text("VEYLO", color = Color.White, style = MaterialTheme.typography.displaySmall, letterSpacing = 4.sp)
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(shown, enter = fadeIn() + slideInVertically { it / 3 }) {
                Column {
                    Text("Split. Sync.", color = Color.White, style = MaterialTheme.typography.displaySmall)
                    Text("Travel Together.", color = Palette.Lime, style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Track group expenses, split bills, and stay in sync — even offline. No account needed.",
                        color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("What should friends call you?", color = Color(0xB3FFFFFF), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        singleLine = true,
                        placeholder = { Text("Your name", color = Color(0x80FFFFFF)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0x1AFFFFFF),
                            unfocusedContainerColor = Color(0x1AFFFFFF),
                            focusedBorderColor = Palette.Lime,
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            cursorColor = Palette.Lime,
                        ),
                    )
                    Spacer(Modifier.height(16.dp))
                    VButton(
                        "Get Started  →",
                        onClick = { onStart(name) },
                        style = ButtonStyle.Lime,
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Together, expenses made effortless.",
                        color = Color(0x80FFFFFF),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}
