package com.autoscroll.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.sp
import com.autoscroll.app.data.model.MacroAction
import com.autoscroll.app.data.model.RecordingCaptureMethod
import com.autoscroll.app.data.model.RecordingState
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.ui.theme.BackgroundSoft
import com.autoscroll.app.ui.theme.BorderHairline
import com.autoscroll.app.ui.theme.ShopeeOrange
import com.autoscroll.app.ui.theme.ShopeeOrangeSoft
import com.autoscroll.app.ui.theme.SurfaceWhite
import com.autoscroll.app.ui.theme.TextPrimary
import com.autoscroll.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderScreen(
    config: ScrollConfig,
    recordingState: RecordingState,
    onSelectCaptureMethod: (RecordingCaptureMethod) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onOpenTargetApp: () -> Unit,
    onSaveMacro: (String, Int) -> Unit,
    onDiscardDraft: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    var macroName by remember(recordingState.hasPendingDraft) {
        mutableStateOf("Macro ${config.targetAppName}")
    }
    var repeatInput by remember(recordingState.hasPendingDraft) { mutableStateOf("1") }

    Scaffold(
        containerColor = BackgroundSoft,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Macro Recorder",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                actions = {
                    IconButton(onClick = onOpenPermissions) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Pengaturan Izin",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = BorderStroke(1.dp, BorderHairline)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = if (recordingState.isRecording) "Perekaman Sedang Berjalan" else "Rekam Interaksi Layar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Floating Control akan berubah ke mode REC. Pindah ke ${config.targetAppName}, lakukan Tap, Double Tap, Long Press, Swipe, atau Scroll, lalu tekan Stop Recording.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RecordingCaptureMethod.entries.forEach { method ->
                                val selected = recordingState.captureMethod == method
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        if (!recordingState.isRecording) onSelectCaptureMethod(method)
                                    },
                                    label = { Text(method.label) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ShopeeOrangeSoft,
                                        selectedLabelColor = ShopeeOrange
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = !recordingState.isRecording,
                                        selected = selected,
                                        selectedBorderColor = ShopeeOrange,
                                        borderColor = BorderHairline
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                if (recordingState.isRecording) onStopRecording() else onStartRecording()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (recordingState.isRecording) Color(0xFFDC2626) else ShopeeOrange
                            )
                        ) {
                            Icon(
                                imageVector = if (recordingState.isRecording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (recordingState.isRecording) "Hentikan Rekaman" else "Mulai Rekam (Mode REC)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        if (recordingState.isRecording) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onOpenTargetApp,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInNew,
                                    contentDescription = null,
                                    tint = ShopeeOrange
                                )
                                Text(
                                    text = "Buka ${config.targetAppName}",
                                    color = ShopeeOrange,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = BorderStroke(1.dp, BorderHairline)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = ShopeeOrange
                        )
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(
                                text = "Keterbatasan Android Non-Root",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Android memblokir aplikasi lain membaca sentuhan mentah secara pasif. Mode Overlay Capture + Forward menangkap sentuhan nyata lalu meneruskannya ke ${config.targetAppName} via dispatchGesture(). Layar berbendera FLAG_SECURE (halaman PIN/pembayaran) tetap dilindungi oleh sistem.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            if (!recordingState.lastCaptureNotice.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = recordingState.lastCaptureNotice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFDC2626),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            if (recordingState.hasPendingDraft && recordingState.capturedActions.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        border = BorderStroke(1.dp, ShopeeOrange.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Simpan Hasil Rekaman (${recordingState.capturedActions.size} Aksi)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = macroName,
                                onValueChange = { macroName = it },
                                label = { Text("Nama Macro") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = repeatInput,
                                onValueChange = { repeatInput = it.filter { ch -> ch.isDigit() }.take(3) },
                                label = { Text("Jumlah Pengulangan (Repeat)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = onDiscardDraft,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Buang", color = TextSecondary)
                                }
                                Button(
                                    onClick = {
                                        val repeat = repeatInput.toIntOrNull()?.coerceIn(1, 999) ?: 1
                                        onSaveMacro(macroName, repeat)
                                    },
                                    modifier = Modifier.weight(2f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ShopeeOrange)
                                ) {
                                    Icon(imageVector = Icons.Rounded.Save, contentDescription = null)
                                    Text(
                                        text = "Simpan ke My Macros",
                                        modifier = Modifier.padding(start = 6.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                itemsIndexed(recordingState.capturedActions) { idx, action ->
                    RecordedActionRow(index = idx, action = action)
                }
            }
        }
    }
}

@Composable
private fun RecordedActionRow(index: Int, action: MacroAction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, BorderHairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${index + 1}. ${action.type.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = action.note.ifBlank {
                        "(${action.startX.toInt()}, ${action.startY.toInt()}) -> (${action.endX.toInt()}, ${action.endY.toInt()})"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Text(
                text = "${action.durationMs}ms · jeda ${action.delayAfterMs}ms",
                style = MaterialTheme.typography.bodySmall,
                color = ShopeeOrange,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
