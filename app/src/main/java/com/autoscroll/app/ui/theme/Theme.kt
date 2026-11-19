package com.autoscroll.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ShopeeOrange = Color(0xFFEE4D2D)
val ShopeeOrangeDark = Color(0xFFD93B1C)
val ShopeeOrangeSoft = Color(0xFFFFF3EF)
val SurfaceWhite = Color(0xFFFFFFFF)
val BackgroundSoft = Color(0xFFF8FAFC)
val BorderHairline = Color(0xFFE2E8F0)
val TextPrimary = Color(0xFF0F172A)
val TextSecondary = Color(0xFF64748B)
val StatusActiveGreen = Color(0xFF16A34A)
val StatusActiveGreenSoft = Color(0xFFECFDF5)
val StatusWarningAmber = Color(0xFFD97706)
val StatusWarningAmberSoft = Color(0xFFFFFBEB)

private val AutoScrollLightColors = lightColorScheme(
    primary = ShopeeOrange,
    onPrimary = Color.White,
    primaryContainer = ShopeeOrangeSoft,
    onPrimaryContainer = ShopeeOrangeDark,
    secondary = TextPrimary,
    onSecondary = Color.White,
    background = BackgroundSoft,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondary,
    outline = BorderHairline
)

private val AutoScrollTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = TextPrimary
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = TextSecondary
    )
)

@Composable
fun AutoScrollTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AutoScrollLightColors,
        typography = AutoScrollTypography,
        content = content
    )
}
