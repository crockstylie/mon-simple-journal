package fr.antoinehory.monsimplejournal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.antoinehory.monsimplejournal.domain.FontFamilyOption
import fr.antoinehory.monsimplejournal.domain.FontScaleOption

/**
 * Résout la [FontFamily] Compose correspondant à l'option [FontFamilyOption].
 */
fun FontFamilyOption.toComposeFontFamily(): FontFamily = when (this) {
    FontFamilyOption.SANS_SERIF -> FontFamily.SansSerif
    FontFamilyOption.SERIF -> FontFamily.Serif
    FontFamilyOption.MONOSPACE -> FontFamily.Monospace
}

/**
 * Génère la typographie de **Mon Simple Journal** adaptée au facteur d'échelle [fontScaleOption]
 * et à la famille de police système [fontFamilyOption].
 */
fun journalTypography(
    fontScaleOption: FontScaleOption = FontScaleOption.NORMAL,
    fontFamilyOption: FontFamilyOption = FontFamilyOption.SANS_SERIF,
): Typography {
    val scale = fontScaleOption.scale
    val family = fontFamilyOption.toComposeFontFamily()
    return Typography(
        headlineSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (22f * scale).sp,
            lineHeight = (28f * scale).sp,
            letterSpacing = 0.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (19f * scale).sp,
            lineHeight = (25f * scale).sp,
            letterSpacing = 0.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (16f * scale).sp,
            lineHeight = (22f * scale).sp,
            letterSpacing = 0.15.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (17f * scale).sp,
            lineHeight = (26f * scale).sp,
            letterSpacing = 0.2.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (15f * scale).sp,
            lineHeight = (22f * scale).sp,
            letterSpacing = 0.2.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (12f * scale).sp,
            lineHeight = (16f * scale).sp,
            letterSpacing = 0.4.sp,
        ),
    )
}
