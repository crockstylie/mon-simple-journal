package fr.antoinehory.monsimplejournal.domain

import androidx.compose.runtime.Immutable

/**
 * Mode de gestion du thème de l'application.
 */
enum class ThemeMode {
    /** Suit automatiquement le thème clair ou sombre du smartphone (Défaut). */
    SYSTEM_AUTO,

    /** Applique l'un des presets fixes inspirés de Hungry Cat Nonogram. */
    PRESET,

    /** Applique les couleurs de fond et de texte choisies sur-mesure par l'utilisateur. */
    CUSTOM,
}

/**
 * Presets de contraste inspirés de *Hungry Cat Nonogram*.
 */
enum class ContrastPreset(
    val backgroundColorArgb: Long,
    val textColorArgb: Long,
    val accentColorArgb: Long,
    val surfaceColorArgb: Long,
) {
    /** Noir profond (OLED) */
    PURE_BLACK(
        backgroundColorArgb = 0xFF000000L,
        textColorArgb = 0xFFEBEBEBL,
        accentColorArgb = 0xFF8AB4F8L,
        surfaceColorArgb = 0xFF141416L,
    ),

    /** Gris ardoise doux (Mode sombre par défaut en mode Système Auto) */
    SLATE_GREY(
        backgroundColorArgb = 0xFF2B2D30L,
        textColorArgb = 0xFFE8EAEDL,
        accentColorArgb = 0xFFA8C7FAL,
        surfaceColorArgb = 0xFF36393DL,
    ),

    /** Gris papier neutre (Mode clair par défaut en mode Système Auto) */
    PAPER_GREY(
        backgroundColorArgb = 0xFFE5E5E0L,
        textColorArgb = 0xFF1F1F1FL,
        accentColorArgb = 0xFF3B5998L,
        surfaceColorArgb = 0xFFDADAD3L,
    ),

    /** Blanc pur à haut contraste */
    PURE_WHITE(
        backgroundColorArgb = 0xFFFFFFFFL,
        textColorArgb = 0xFF111111L,
        accentColorArgb = 0xFF1A73E8L,
        surfaceColorArgb = 0xFFF1F3F4L,
    ),

    /** Sépia chaud */
    WARM_SEPIA(
        backgroundColorArgb = 0xFFF4ECD8L,
        textColorArgb = 0xFF3C2F2FL,
        accentColorArgb = 0xFF8C5A3CL,
        surfaceColorArgb = 0xFFE8DEC5L,
    ),
}

/**
 * Familles de polices système disponibles dans les paramètres.
 */
enum class FontFamilyOption {
    /** Police système Sans-serif moderne (Défaut) */
    SANS_SERIF,

    /** Police système Serif littéraire (style livre) */
    SERIF,

    /** Police système Monospace à chasse fixe (style machine à écrire / carnet brut) */
    MONOSPACE,
}

/**
 * Facteurs d'échelle pour la taille du texte dans l'éditeur et le lecteur.
 */
enum class FontScaleOption(val scale: Float) {
    SMALL(0.90f),
    NORMAL(1.00f),
    LARGE(1.15f),
    EXTRA_LARGE(1.30f),
}

/**
 * Palette de couleurs résolue pour l'interface utilisateur.
 */
@Immutable
data class ContrastPalette(
    val backgroundColorArgb: Long,
    val textColorArgb: Long,
    val accentColorArgb: Long,
    val surfaceColorArgb: Long,
) {
    companion object {
        fun fromPreset(preset: ContrastPreset): ContrastPalette = ContrastPalette(
            backgroundColorArgb = preset.backgroundColorArgb,
            textColorArgb = preset.textColorArgb,
            accentColorArgb = preset.accentColorArgb,
            surfaceColorArgb = preset.surfaceColorArgb,
        )

        /**
         * Résout la palette active selon le mode de thème, l'état Dark/Light du smartphone,
         * le preset sélectionné et les couleurs personnalisées.
         */
        fun resolve(
            themeMode: ThemeMode,
            preset: ContrastPreset,
            customBackgroundColorArgb: Long,
            customTextColorArgb: Long,
            isSystemInDarkTheme: Boolean,
        ): ContrastPalette {
            return when (themeMode) {
                ThemeMode.SYSTEM_AUTO -> {
                    val autoPreset = if (isSystemInDarkTheme) {
                        ContrastPreset.SLATE_GREY
                    } else {
                        ContrastPreset.PAPER_GREY
                    }
                    fromPreset(autoPreset)
                }

                ThemeMode.PRESET -> fromPreset(preset)

                ThemeMode.CUSTOM -> {
                    val isDarkBg = calculateRelativeLuminance(customBackgroundColorArgb) < 0.5
                    val accent = if (isDarkBg) 0xFF8AB4F8L else 0xFF3B5998L
                    val surface = blendArgb(customBackgroundColorArgb, customTextColorArgb, 0.08f)
                    ContrastPalette(
                        backgroundColorArgb = customBackgroundColorArgb,
                        textColorArgb = customTextColorArgb,
                        accentColorArgb = accent,
                        surfaceColorArgb = surface,
                    )
                }
            }
        }

        /**
         * Calcule une approximation rapide de la luminance relative (0.0 = noir, 1.0 = blanc).
         */
        fun calculateRelativeLuminance(argb: Long): Double {
            val r = ((argb shr 16) and 0xFF) / 255.0
            val g = ((argb shr 8) and 0xFF) / 255.0
            val b = (argb and 0xFF) / 255.0
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        private fun blendArgb(baseArgb: Long, targetArgb: Long, ratio: Float): Long {
            val inv = 1f - ratio
            val r = (((baseArgb shr 16) and 0xFF) * inv + ((targetArgb shr 16) and 0xFF) * ratio).toLong().coerceIn(0, 255)
            val g = (((baseArgb shr 8) and 0xFF) * inv + ((targetArgb shr 8) and 0xFF) * ratio).toLong().coerceIn(0, 255)
            val b = ((baseArgb and 0xFF) * inv + (targetArgb and 0xFF) * ratio).toLong().coerceIn(0, 255)
            return (0xFFL shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
