package fr.antoinehory.monsimplejournal.data.preferences

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.antoinehory.monsimplejournal.domain.ContrastPreset
import fr.antoinehory.monsimplejournal.domain.DateFormatStyle
import fr.antoinehory.monsimplejournal.domain.FontFamilyOption
import fr.antoinehory.monsimplejournal.domain.FontScaleOption
import fr.antoinehory.monsimplejournal.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mon_simple_journal_preferences",
)

/**
 * État immuable regroupant l'ensemble des préférences utilisateur de **Mon Simple Journal**.
 */
@Immutable
data class UserPreferences(
    val dateFormatStyle: DateFormatStyle = DateFormatStyle.FULL_DAY_FIRST,
    val themeMode: ThemeMode = ThemeMode.SYSTEM_AUTO,
    val contrastPreset: ContrastPreset = ContrastPreset.SLATE_GREY,
    val customBackgroundColorArgb: Long = ContrastPreset.SLATE_GREY.backgroundColorArgb,
    val customTextColorArgb: Long = ContrastPreset.SLATE_GREY.textColorArgb,
    val fontFamilyOption: FontFamilyOption = FontFamilyOption.SANS_SERIF,
    val fontScaleOption: FontScaleOption = FontScaleOption.NORMAL,
    val keepScreenOn: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val recentSearchQueries: List<String> = emptyList(),
)

/**
 * Repository gérant la persistance réactive des préférences utilisateur via Jetpack DataStore.
 */
class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.userPreferencesDataStore)

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val dateStyle = prefs[Keys.DATE_FORMAT_STYLE]
                ?.let { runCatching { DateFormatStyle.valueOf(it) }.getOrNull() }
                ?: DateFormatStyle.FULL_DAY_FIRST

            val themeMode = prefs[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM_AUTO

            val preset = prefs[Keys.CONTRAST_PRESET]
                ?.let { runCatching { ContrastPreset.valueOf(it) }.getOrNull() }
                ?: ContrastPreset.SLATE_GREY

            val customBg = prefs[Keys.CUSTOM_BG_COLOR] ?: ContrastPreset.SLATE_GREY.backgroundColorArgb
            val customText = prefs[Keys.CUSTOM_TEXT_COLOR] ?: ContrastPreset.SLATE_GREY.textColorArgb

            val fontFamily = prefs[Keys.FONT_FAMILY]
                ?.let { runCatching { FontFamilyOption.valueOf(it) }.getOrNull() }
                ?: FontFamilyOption.SANS_SERIF

            val fontScale = prefs[Keys.FONT_SCALE]
                ?.let { runCatching { FontScaleOption.valueOf(it) }.getOrNull() }
                ?: FontScaleOption.NORMAL

            val keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true
            val biometricLock = prefs[Keys.BIOMETRIC_LOCK_ENABLED] ?: false
            val recentSearches = prefs[Keys.RECENT_SEARCH_QUERIES]
                .orEmpty()
                .split(RECENT_SEARCH_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            UserPreferences(
                dateFormatStyle = dateStyle,
                themeMode = themeMode,
                contrastPreset = preset,
                customBackgroundColorArgb = customBg,
                customTextColorArgb = customText,
                fontFamilyOption = fontFamily,
                fontScaleOption = fontScale,
                keepScreenOn = keepScreenOn,
                biometricLockEnabled = biometricLock,
                recentSearchQueries = recentSearches,
            )
        }

    suspend fun setDateFormatStyle(style: DateFormatStyle) {
        dataStore.edit { it[Keys.DATE_FORMAT_STYLE] = style.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun selectContrastPreset(preset: ContrastPreset) {
        dataStore.edit {
            it[Keys.THEME_MODE] = ThemeMode.PRESET.name
            it[Keys.CONTRAST_PRESET] = preset.name
        }
    }

    suspend fun setCustomColors(backgroundColorArgb: Long, textColorArgb: Long) {
        dataStore.edit {
            it[Keys.THEME_MODE] = ThemeMode.CUSTOM.name
            it[Keys.CUSTOM_BG_COLOR] = backgroundColorArgb
            it[Keys.CUSTOM_TEXT_COLOR] = textColorArgb
        }
    }

    suspend fun setFontFamilyOption(option: FontFamilyOption) {
        dataStore.edit { it[Keys.FONT_FAMILY] = option.name }
    }

    suspend fun setFontScaleOption(option: FontScaleOption) {
        dataStore.edit { it[Keys.FONT_SCALE] = option.name }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BIOMETRIC_LOCK_ENABLED] = enabled }
    }

    suspend fun addRecentSearchQuery(query: String) {
        val cleaned = query.replace(RECENT_SEARCH_SEPARATOR, ' ').trim()
        if (cleaned.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_SEARCH_QUERIES]
                .orEmpty()
                .split(RECENT_SEARCH_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals(cleaned, ignoreCase = true) }
            val updated = (listOf(cleaned) + current).take(MAX_RECENT_SEARCHES)
            prefs[Keys.RECENT_SEARCH_QUERIES] = updated.joinToString(RECENT_SEARCH_SEPARATOR.toString())
        }
    }

    suspend fun removeRecentSearchQuery(query: String) {
        dataStore.edit { prefs ->
            val updated = prefs[Keys.RECENT_SEARCH_QUERIES]
                .orEmpty()
                .split(RECENT_SEARCH_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals(query.trim(), ignoreCase = true) }
            prefs[Keys.RECENT_SEARCH_QUERIES] = updated.joinToString(RECENT_SEARCH_SEPARATOR.toString())
        }
    }

    suspend fun clearRecentSearchQueries() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.RECENT_SEARCH_QUERIES)
        }
    }

    private object Keys {
        val DATE_FORMAT_STYLE = stringPreferencesKey("date_format_style")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CONTRAST_PRESET = stringPreferencesKey("contrast_preset")
        val CUSTOM_BG_COLOR = longPreferencesKey("custom_bg_color")
        val CUSTOM_TEXT_COLOR = longPreferencesKey("custom_text_color")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
        val RECENT_SEARCH_QUERIES = stringPreferencesKey("recent_search_queries")
    }

    companion object {
        const val MAX_RECENT_SEARCHES = 10
        private const val RECENT_SEARCH_SEPARATOR = '\u001F'
    }
}
