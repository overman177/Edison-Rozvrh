package cz.tal0052.edisonrozvrh

import androidx.compose.ui.graphics.Color

data class LessonTypePalette(
    val container: Color,
    val border: Color,
    val title: Color,
    val meta: Color
)

object UiColorConfig {
    // ---- BRAND / THEME ----
    val VsbBrand = Color(0xFF00A499)
    val VsbBrandDark = Color(0xFF007D76)
    val VsbBrandDeep = Color(0xFF063C39)
    val VsbBackground = Color(0xFF07181A)

    // ---- APP COLOR SCHEME TUNING ----
    val DarkSecondary = Color(0xFF4CC3BB)
    val DarkTertiary = Color(0xFF86E0DA)
    val DarkSurface = Color(0xFF0E2528)
    val DarkSurfaceVariant = Color(0xFF12363A)
    val DarkOnPrimary = Color(0xFFFFFFFF)
    val DarkOnSurface = Color(0xFFE8F6F4)
    val DarkOnSurfaceVariant = Color(0xFFB7D8D4)
    val DarkOutline = Color(0xFF2B5A57)

    val LightSecondary = Color(0xFF007D76)
    val LightTertiary = Color(0xFF3CB9B0)
    val LightBackground = Color(0xFFF2FBFA)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFD8F0ED)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightOnSurface = Color(0xFF0A2E2B)
    val LightOnSurfaceVariant = Color(0xFF355C58)
    val LightOutline = Color(0xFF7CB8B2)

    val HeaderTextMuted = Color(0xFFD7F3F0)

    // ---- LESSON CARD PALETTE ----
    // Accent base colors live in res/values/colors.xml (lesson_accent_*),
    // so widget cards and app cards share the same source.
    const val CardFillAlpha = 0.17f
    const val CardBorderAlpha = 0.42f

    val CardTitle = Color(0xFFF1FAF9)
    val CardMeta = Color(0xFFC7DEDB)
}
