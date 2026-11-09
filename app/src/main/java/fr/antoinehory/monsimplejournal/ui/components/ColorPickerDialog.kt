package fr.antoinehory.monsimplejournal.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.antoinehory.monsimplejournal.R
import java.util.Locale

/**
 * Boîte de dialogue permettant à l'utilisateur de personnaliser librement la couleur du fond
 * et la couleur du texte via des curseurs HSV (Teinte, Saturation, Luminosité) et un code Hexadécimal `#RRGGBB`.
 */
@Composable
fun CustomContrastColorsDialog(
    initialBackgroundArgb: Long,
    initialTextArgb: Long,
    onDismiss: () -> Unit,
    onConfirm: (backgroundArgb: Long, textArgb: Long) -> Unit,
) {
    var editingBackground by remember { mutableStateOf(true) }
    var bgArgb by remember { mutableStateOf(initialBackgroundArgb) }
    var txtArgb by remember { mutableStateOf(initialTextArgb) }

    val activeArgb = if (editingBackground) bgArgb else txtArgb
    val initialHsv = remember(editingBackground) {
        argbToHsv(if (editingBackground) bgArgb else txtArgb)
    }

    var hue by remember(editingBackground) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(editingBackground) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(editingBackground) { mutableFloatStateOf(initialHsv[2]) }
    var hexInput by remember(editingBackground) {
        mutableStateOf(argbToHexString(activeArgb))
    }

    fun updateActiveColorFromHsv(h: Float, s: Float, v: Float) {
        val newArgb = hsvToArgb(h, s, v)
        hexInput = argbToHexString(newArgb)
        if (editingBackground) {
            bgArgb = newArgb
        } else {
            txtArgb = newArgb
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.custom_colors_dialog_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Aperçu en temps réel du contraste Fond / Texte
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(bgArgb))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column {
                        Text(
                            text = "Lundi 9 novembre 2026",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(txtArgb),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.custom_colors_preview_sample),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(txtArgb),
                        )
                    }
                }

                // Sélecteur : Couleur du fond vs Couleur du texte
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = editingBackground,
                        onClick = { editingBackground = true },
                        label = { Text(stringResource(R.string.custom_colors_tab_background)) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = !editingBackground,
                        onClick = { editingBackground = false },
                        label = { Text(stringResource(R.string.custom_colors_tab_text)) },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Curseurs HSV (Teinte, Saturation, Luminosité)
                Column {
                    Text(
                        text = stringResource(R.string.custom_colors_hue),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = hue,
                        onValueChange = {
                            hue = it
                            updateActiveColorFromHsv(hue, saturation, value)
                        },
                        valueRange = 0f..360f,
                    )

                    Text(
                        text = stringResource(R.string.custom_colors_saturation),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = saturation,
                        onValueChange = {
                            saturation = it
                            updateActiveColorFromHsv(hue, saturation, value)
                        },
                        valueRange = 0f..1f,
                    )

                    Text(
                        text = stringResource(R.string.custom_colors_brightness),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = value,
                        onValueChange = {
                            value = it
                            updateActiveColorFromHsv(hue, saturation, value)
                        },
                        valueRange = 0f..1f,
                    )
                }

                // Saisie directe du code Hexadécimal #RRGGBB
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        val cleaned = raw.uppercase(Locale.ROOT).take(7)
                        hexInput = cleaned
                        parseHexToArgb(cleaned)?.let { parsedArgb ->
                            val hsv = argbToHsv(parsedArgb)
                            hue = hsv[0]
                            saturation = hsv[1]
                            value = hsv[2]
                            if (editingBackground) {
                                bgArgb = parsedArgb
                            } else {
                                txtArgb = parsedArgb
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.custom_colors_hex_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(bgArgb, txtArgb) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun argbToHsv(argb: Long): FloatArray {
    val hsv = FloatArray(3)
    val r = ((argb shr 16) and 0xFF).toInt()
    val g = ((argb shr 8) and 0xFF).toInt()
    val b = (argb and 0xFF).toInt()
    AndroidColor.RGBToHSV(r, g, b, hsv)
    return hsv
}

private fun hsvToArgb(hue: Float, saturation: Float, value: Float): Long {
    val colorInt = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    return colorInt.toLong() and 0xFFFFFFFFL
}

private fun argbToHexString(argb: Long): String {
    val rgb = argb and 0xFFFFFFL
    return String.format(Locale.ROOT, "#%06X", rgb)
}

private fun parseHexToArgb(hex: String): Long? {
    val normalized = hex.removePrefix("#")
    if (normalized.length != 6) return null
    val rgb = normalized.toLongOrNull(16) ?: return null
    return 0xFF000000L or rgb
}
