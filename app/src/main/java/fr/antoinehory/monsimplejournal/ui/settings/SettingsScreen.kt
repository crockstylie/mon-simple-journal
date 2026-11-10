package fr.antoinehory.monsimplejournal.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.antoinehory.monsimplejournal.R
import fr.antoinehory.monsimplejournal.data.backup.BackupResult
import fr.antoinehory.monsimplejournal.domain.ContrastPreset
import fr.antoinehory.monsimplejournal.domain.DateFormatStyle
import fr.antoinehory.monsimplejournal.domain.FontFamilyOption
import fr.antoinehory.monsimplejournal.domain.FontScaleOption
import fr.antoinehory.monsimplejournal.domain.JournalDateFormatter
import fr.antoinehory.monsimplejournal.domain.ThemeMode
import fr.antoinehory.monsimplejournal.ui.components.CustomContrastColorsDialog
import fr.antoinehory.monsimplejournal.ui.theme.MonSimpleJournalTheme
import fr.antoinehory.monsimplejournal.ui.theme.toComposeFontFamily

/**
 * Route stateful de l'écran des paramètres (`SettingsScreen`).
 */
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToInfo: () -> Unit,
    onBackupImported: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportAllToZip(context.contentResolver, uri)
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importFromUri(
                contentResolver = context.contentResolver,
                sourceUri = uri,
                onImportCompleted = onBackupImported,
            )
        }
    }

    val exportSuccessFormat = stringResource(R.string.settings_export_success)
    val importSuccessFormat = stringResource(R.string.settings_import_success)
    val backupErrorText = stringResource(R.string.settings_backup_error)

    val backupResult = uiState.lastBackupResult
    LaunchedEffect(backupResult) {
        if (backupResult != null) {
            val message = when (backupResult) {
                is BackupResult.ExportSuccess -> String.format(exportSuccessFormat, backupResult.entryCount)
                is BackupResult.ImportSuccess -> String.format(importSuccessFormat, backupResult.mergedCount)
                is BackupResult.Error -> backupErrorText
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.onBackupResultConsumed()
        }
    }

    SettingsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onDateFormatSelected = viewModel::onDateFormatSelected,
        onThemeModeAutoSelected = viewModel::onThemeModeAutoSelected,
        onContrastPresetSelected = viewModel::onContrastPresetSelected,
        onCustomColorsSaved = viewModel::onCustomColorsSaved,
        onFontFamilySelected = viewModel::onFontFamilySelected,
        onFontScaleSelected = viewModel::onFontScaleSelected,
        onKeepScreenOnToggled = viewModel::onKeepScreenOnToggled,
        onBiometricLockToggled = viewModel::onBiometricLockToggled,
        onExportZipClicked = {
            exportZipLauncher.launch(uiState.suggestedZipFileName)
        },
        onImportBackupClicked = {
            importBackupLauncher.launch(arrayOf("application/zip", "text/markdown", "text/plain", "*/*"))
        },
        onNavigateToInfo = onNavigateToInfo,
        modifier = modifier,
    )
}

/**
 * Écran complet des paramètres de **Mon Simple Journal**.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onDateFormatSelected: (DateFormatStyle) -> Unit,
    onThemeModeAutoSelected: () -> Unit,
    onContrastPresetSelected: (ContrastPreset) -> Unit,
    onCustomColorsSaved: (backgroundColorArgb: Long, textColorArgb: Long) -> Unit,
    onFontFamilySelected: (FontFamilyOption) -> Unit,
    onFontScaleSelected: (FontScaleOption) -> Unit,
    onKeepScreenOnToggled: (Boolean) -> Unit,
    onBiometricLockToggled: (Boolean) -> Unit,
    onExportZipClicked: () -> Unit,
    onImportBackupClicked: () -> Unit,
    onNavigateToInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val prefs = uiState.preferences

    var showCustomColorDialog by remember { mutableStateOf(false) }

    if (showCustomColorDialog) {
        CustomContrastColorsDialog(
            initialBackgroundArgb = prefs.customBackgroundColorArgb,
            initialTextArgb = prefs.customTextColorArgb,
            onDismiss = { showCustomColorDialog = false },
            onConfirm = { bgArgb, txtArgb ->
                onCustomColorsSaved(bgArgb, txtArgb)
                showCustomColorDialog = false
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = textColor,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = textColor,
                        navigationIconContentColor = textColor,
                    ),
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // 1. Format de la date du titre
            SettingsSection(title = stringResource(R.string.settings_section_date)) {
                DateFormatStyle.entries.forEach { style ->
                    val previewTitle = remember(uiState.todayDate, style) {
                        JournalDateFormatter.formatTitle(uiState.todayDate, style)
                    }
                    val selected = prefs.dateFormatStyle == style
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.RadioButton) { onDateFormatSelected(style) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = previewTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // 2. Thème Dark/Light & Fonds de contraste (Hungry Cat Nonogram)
            SettingsSection(title = stringResource(R.string.settings_section_theme)) {
                // Option Automatique (Dark / Light du smartphone)
                val isAuto = prefs.themeMode == ThemeMode.SYSTEM_AUTO
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.RadioButton) { onThemeModeAutoSelected() }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isAuto,
                        onClick = null,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_theme_auto_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                        )
                        Text(
                            text = stringResource(R.string.settings_theme_auto_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor.copy(alpha = 0.65f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Presets Hungry Cat Nonogram
                ContrastPreset.entries.forEach { preset ->
                    val presetSelected = prefs.themeMode == ThemeMode.PRESET && prefs.contrastPreset == preset
                    val presetLabel = when (preset) {
                        ContrastPreset.PURE_BLACK -> stringResource(R.string.settings_preset_pure_black)
                        ContrastPreset.SLATE_GREY -> stringResource(R.string.settings_preset_slate_grey)
                        ContrastPreset.PAPER_GREY -> stringResource(R.string.settings_preset_paper_grey)
                        ContrastPreset.PURE_WHITE -> stringResource(R.string.settings_preset_pure_white)
                        ContrastPreset.WARM_SEPIA -> stringResource(R.string.settings_preset_warm_sepia)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.RadioButton) { onContrastPresetSelected(preset) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = presetSelected,
                            onClick = null,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        // Pastille d'aperçu du contraste
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(preset.backgroundColorArgb))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "A",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(preset.textColorArgb),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = presetLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bouton Couleurs Personnalisées
                OutlinedButton(
                    onClick = { showCustomColorDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_theme_custom_button))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // 3. Police d'écriture (Sans-serif / Serif / Monospace)
            SettingsSection(title = stringResource(R.string.settings_section_font_family)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FontFamilyOption.entries.forEach { familyOption ->
                        val label = when (familyOption) {
                            FontFamilyOption.SANS_SERIF -> stringResource(R.string.settings_font_family_sans)
                            FontFamilyOption.SERIF -> stringResource(R.string.settings_font_family_serif)
                            FontFamilyOption.MONOSPACE -> stringResource(R.string.settings_font_family_mono)
                        }
                        FilterChip(
                            selected = prefs.fontFamilyOption == familyOption,
                            onClick = { onFontFamilySelected(familyOption) },
                            label = {
                                Text(
                                    text = label,
                                    fontFamily = familyOption.toComposeFontFamily(),
                                )
                            },
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // 4. Taille du texte
            SettingsSection(title = stringResource(R.string.settings_section_font_size)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FontScaleOption.entries.forEach { option ->
                        val label = when (option) {
                            FontScaleOption.SMALL -> stringResource(R.string.settings_font_small)
                            FontScaleOption.NORMAL -> stringResource(R.string.settings_font_normal)
                            FontScaleOption.LARGE -> stringResource(R.string.settings_font_large)
                            FontScaleOption.EXTRA_LARGE -> stringResource(R.string.settings_font_extra_large)
                        }
                        FilterChip(
                            selected = prefs.fontScaleOption == option,
                            onClick = { onFontScaleSelected(option) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // 4. Confort & Confidentialité (Keep Screen On + Biométrie)
            SettingsSection(title = stringResource(R.string.settings_section_comfort)) {
                SwitchPreferenceRow(
                    title = stringResource(R.string.settings_keep_screen_on_title),
                    subtitle = stringResource(R.string.settings_keep_screen_on_subtitle),
                    checked = prefs.keepScreenOn,
                    onCheckedChange = onKeepScreenOnToggled,
                )

                Spacer(modifier = Modifier.height(12.dp))

                SwitchPreferenceRow(
                    title = stringResource(R.string.settings_biometric_title),
                    subtitle = stringResource(R.string.settings_biometric_subtitle),
                    checked = prefs.biometricLockEnabled,
                    onCheckedChange = onBiometricLockToggled,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // 5. Sauvegarde & Changement de téléphone (.MD & .ZIP)
            SettingsSection(title = stringResource(R.string.settings_section_backup)) {
                Text(
                    text = stringResource(R.string.settings_backup_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.75f),
                )
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onExportZipClicked,
                    enabled = !uiState.isBackupInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_export_zip_button))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onImportBackupClicked,
                    enabled = !uiState.isBackupInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_import_zip_button))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // 6. À propos & Crédits
            OutlinedButton(
                onClick = onNavigateToInfo,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_about_button))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SwitchPreferenceRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.65f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MonSimpleJournalTheme {
        SettingsScreen(
            uiState = SettingsUiState(),
            onNavigateBack = {},
            onDateFormatSelected = {},
            onThemeModeAutoSelected = {},
            onContrastPresetSelected = {},
            onCustomColorsSaved = { _, _ -> },
            onFontFamilySelected = {},
            onFontScaleSelected = {},
            onKeepScreenOnToggled = {},
            onBiometricLockToggled = {},
            onExportZipClicked = {},
            onImportBackupClicked = {},
            onNavigateToInfo = {},
        )
    }
}
