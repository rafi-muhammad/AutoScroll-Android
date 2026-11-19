package com.autoscroll.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.data.model.ScrollDirection
import com.autoscroll.app.data.model.ScrollSpeed
import com.autoscroll.app.ui.theme.BorderHairline
import com.autoscroll.app.ui.theme.ShopeeOrange
import com.autoscroll.app.ui.theme.ShopeeOrangeSoft
import com.autoscroll.app.ui.theme.SurfaceWhite
import kotlin.math.roundToLong

@Composable
fun ScrollSettingsCard(
    config: ScrollConfig,
    onSelectDirection: (ScrollDirection) -> Unit,
    onSelectSpeed: (ScrollSpeed) -> Unit,
    onDurationChange: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, BorderHairline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Pengaturan Gesture Scroll",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Arah Scroll",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScrollDirection.entries.forEach { dir ->
                    val selected = config.direction == dir
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectDirection(dir) },
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(
                                imageVector = if (dir == ScrollDirection.DOWN) {
                                    Icons.Rounded.ArrowDownward
                                } else {
                                    Icons.Rounded.ArrowUpward
                                },
                                contentDescription = null
                            )
                        },
                        label = { Text(dir.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ShopeeOrangeSoft,
                            selectedLabelColor = ShopeeOrange,
                            selectedLeadingIconColor = ShopeeOrange
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

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Kecepatan & Jarak Scroll",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScrollSpeed.entries.forEach { speed ->
                    val selected = config.speed == speed
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectSpeed(speed) },
                        label = { Text(speed.label) },
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

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Durasi Gerakan Scroll",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${config.durationMs} ms",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ShopeeOrange
                )
            }
            Slider(
                value = config.durationMs.toFloat(),
                onValueChange = { onDurationChange((it / 50f).roundToLong() * 50L) },
                valueRange = 200f..1500f,
                colors = SliderDefaults.colors(
                    thumbColor = ShopeeOrange,
                    activeTrackColor = ShopeeOrange,
                    inactiveTrackColor = ShopeeOrangeSoft
                )
            )
        }
    }
}
