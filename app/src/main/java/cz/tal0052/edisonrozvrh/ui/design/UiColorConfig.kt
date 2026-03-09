package cz.tal0052.edisonrozvrh.ui.design

import androidx.compose.ui.graphics.Color

data class LessonTypePalette(
    val container: Color,
    val border: Color,
    val title: Color,
    val meta: Color
)

object UiColorConfig {
    // Card body tint alpha over lesson accent.
    const val CardFillAlpha = 0.17f
    // Card border alpha over lesson accent.
    const val CardBorderAlpha = 0.42f
}
