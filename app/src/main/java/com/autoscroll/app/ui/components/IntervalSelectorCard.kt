package com.autoscroll.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.autoscroll.app.data.model.CustomTimeUnit
import com.autoscroll.app.data.model.IntervalPreset
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.ui.theme.BorderHairline
import com.autoscroll.app.ui.theme.ShopeeOrange
import com.autoscroll.app.ui.theme.ShopeeOrangeSoft
import com.autoscroll.app.ui.theme.SurfaceWhite
import com.autoscroll.app.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntervalSelectorCard(
    config: ScrollConfig,
    onSelectPreset: (IntervalPreset) -> Unit,
    onApplyCustom: (Int, CustomTimeUnit) -> Unit
) {
    var customInput by remember(config.customIntervalValue) {
        mutableStateOf(config.customIntervalValue.toString())
    }
    var selectedUnit by remember(config.customTimeUnit) {
        mutableStateOf(config.customTimeUnit)
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, BorderHairline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Interval Scroll",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Jeda waktu antar setiap gerakan scroll otomatis.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IntervalPreset.entries.forEach { preset ->
                    val selected = config.intervalPreset == preset
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectPreset(preset) },
                        label = {
                            Text(
                                text = preset.label,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ShopeeOrangeSoft,
                            selectedLabelColor = ShopeeOrange
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = ShopeeOrange,
                            borderColor = BorderHairline
                        )
                    )
                }
            }

            if (config.intervalPreset == IntervalPreset.CUSTOM) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { raw ->
                            customInput = raw.filter { it.isDigit() }.take(4)
                            validationError = null
                        },
                        label = { Text("Nilai") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CustomTimeUnit.entries.forEach { unit ->
                        val active = selectedUnit == unit
                        FilterChip(
                            selected = active,
                            onClick = { selectedUnit = unit },
                            label = { Text(unit.label) },
                            modifier = Modifier.padding(start = 4.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ShopeeOrangeSoft,
                                selectedLabelColor = ShopeeOrange
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = validationError ?: "Minimal 2 detik · Maksimal 60 menit",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (validationError != null) Color(0xFFDC2626) else TextSecondary
                    )
                    Button(
                        onClick = {
                            val parsed = customInput.toIntOrNull()
                            val seconds = if (parsed != null) parsed * selectedUnit.multiplierSeconds else 0L
                            if (parsed == null || seconds < 2L || seconds > 3600L) {
                                validationError = "Masukkan antara 2 detik – 60 menit"
                            } else {
                                onApplyCustom(parsed, selectedUnit)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ShopeeOrange),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Terapkan")
                    }
                }
            }
        }
    }
}
