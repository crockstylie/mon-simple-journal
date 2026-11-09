package fr.antoinehory.monsimplejournal.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import fr.antoinehory.monsimplejournal.data.preferences.UserPreferences
import fr.antoinehory.monsimplejournal.domain.ContrastPalette

/**
 * Thème principal de **Mon Simple Journal**.
 *
 * Supporte :
 * - Le basculement automatique selon le thème Dark / Light du smartphone (`ThemeMode.SYSTEM_AUTO`)
 * - Les presets de contraste inspirés de *Hungry Cat Nonogram* (`ThemeMode.PRESET`)
 * - Les couleurs de fond et de texte personnalisées (`ThemeMode.CUSTOM`)
 * - Le facteur d'échelle de la police (`FontScaleOption`)
 */
@Composable
fun MonSimpleJournalTheme(
    userPreferences: UserPreferences = UserPreferences(),
    systemInDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = remember(
        userPreferences.themeMode,
        userPreferences.contrastPreset,
        userPreferences.customBackgroundColorArgb,
        userPreferences.customTextColorArgb,
        systemInDarkTheme,
    ) {
        ContrastPalette.resolve(
            themeMode = userPreferences.themeMode,
            preset = userPreferences.contrastPreset,
            customBackgroundColorArgb = userPreferences.customBackgroundColorArgb,
            customTextColorArgb = userPreferences.customTextColorArgb,
            isSystemInDarkTheme = systemInDarkTheme,
        )
    }

    val isLightBackground = remember(palette.backgroundColorArgb) {
        ContrastPalette.calculateRelativeLuminance(palette.backgroundColorArgb) >= 0.5
    }

    val colorScheme = remember(palette, isLightBackground) {
        buildColorScheme(palette, isLightBackground)
    }

    val typography = remember(
        userPreferences.fontScaleOption,
        userPreferences.fontFamilyOption,
    ) {
        journalTypography(
            fontScaleOption = userPreferences.fontScaleOption,
            fontFamilyOption = userPreferences.fontFamilyOption,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = isLightBackground
            insetsController.isAppearanceLightNavigationBars = isLightBackground
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}

private fun buildColorScheme(
    palette: ContrastPalette,
    isLightBackground: Boolean,
): ColorScheme {
    val background = Color(palette.backgroundColorArgb)
    val onBackground = Color(palette.textColorArgb)
    val primary = Color(palette.accentColorArgb)
    val surfaceVariant = Color(palette.surfaceColorArgb)

    return if (isLightBackground) {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = primary,
            onSecondary = Color.White,
            background = background,
            onBackground = onBackground,
            surface = background,
            onSurface = onBackground,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onBackground.copy(alpha = 0.78f),
            outline = onBackground.copy(alpha = 0.28f),
        )
    } else {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.Black,
            secondary = primary,
            onSecondary = Color.Black,
            background = background,
            onBackground = onBackground,
            surface = background,
            onSurface = onBackground,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onBackground.copy(alpha = 0.78f),
            outline = onBackground.copy(alpha = 0.28f),
        )
    }
}
