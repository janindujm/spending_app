package com.spendplan.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.spendplan.app.data.ThemeMode
import com.spendplan.app.domain.BudgetStatus

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6B5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5F2DF),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB0),
    onSecondaryContainer = Color(0xFF2C1700),
    tertiary = Color(0xFF3F6375),
    background = Color(0xFFF5FBF8),
    surface = Color(0xFFF5FBF8),
    surfaceContainer = Color(0xFFE9F0EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF89D6C3),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005144),
    onPrimaryContainer = Color(0xFFA5F2DF),
    secondary = Color(0xFFFFB950),
    onSecondary = Color(0xFF492900),
    secondaryContainer = Color(0xFF693D00),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFFA7CCE0),
)

@Composable
fun SpendPlanTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Semantic colors for budget status (green / amber / red), tuned per light/dark theme. */
object StatusColors {
    @Composable
    @ReadOnlyComposable
    private fun isDark() = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val good: Color @Composable @ReadOnlyComposable get() = if (isDark()) Color(0xFF81C784) else Color(0xFF2E7D32)
    val warning: Color @Composable @ReadOnlyComposable get() = if (isDark()) Color(0xFFFFD54F) else Color(0xFFE09B00)
    val bad: Color @Composable @ReadOnlyComposable get() = if (isDark()) Color(0xFFEF9A9A) else Color(0xFFC62828)

    @Composable
    @ReadOnlyComposable
    fun forStatus(status: BudgetStatus): Color = when (status) {
        BudgetStatus.NONE, BudgetStatus.OK -> good
        BudgetStatus.WARNING -> warning
        BudgetStatus.OVER -> bad
    }

    @Composable
    @ReadOnlyComposable
    fun forAmount(amount: Long): Color = if (amount < 0) bad else good
}
