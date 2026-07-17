package com.benjo.fboplus.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Brush

// ── Palette Premium FBO+ Noir & Or ──
val BlackDeep      = Color(0xFF0B0D14)
val BlackCard      = Color(0xFF141821)
val BlackSurface   = Color(0xFF232834)
val BlackBorder    = Color(0xFF2C3040)

val GoldPrimary    = Color(0xFFD4AF37)
val GoldLight      = Color(0xFFFFD700)
val GoldDark       = Color(0xFFA07D20)
val GoldGlow       = Color(0x33D4AF37)
val GoldFaint      = Color(0x1AD4AF37)

val WhitePrimary   = Color(0xFFF2F2F2)
val WhiteSub       = Color(0xFFB0B8C8)
val WhiteFaint     = Color(0xFF5A6275)

val GreenPrimary   = Color(0xFF00C853)
val GreenDark      = Color(0xFF00952E)
val GreenGlow      = Color(0x2200C853)

val RedAlert       = Color(0xFFFF1744)
val BlueAccent     = Color(0xFF2979FF)

val GoldGradientH = Brush.horizontalGradient(listOf(Color(0xFFD4AF37), Color(0xFFFFD700)))
val GoldGradientB = Brush.linearGradient(listOf(Color(0xFFA07D20), Color(0xFFFFD700)))
val GoldGradient = Brush.linearGradient(listOf(Color(0xFFA07D20), Color(0xFFFFD700)))

private val DarkColorScheme = darkColorScheme(
    primary        = GoldPrimary,
    onPrimary      = BlackDeep,
    secondary      = GoldLight,
    onSecondary    = BlackDeep,
    background     = BlackDeep,
    onBackground   = WhitePrimary,
    surface        = BlackCard,
    onSurface      = WhitePrimary,
    surfaceVariant = BlackSurface,
    error          = RedAlert,
)

@Composable
fun FBOPlusTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BlackDeep.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}