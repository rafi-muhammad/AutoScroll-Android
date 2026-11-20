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
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.autoscroll.app.data.model.MacroBuilderState
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.ui.theme.BackgroundSoft
import com.autoscroll.app.ui.theme.BorderHairline
import com.autoscroll.app.ui.theme.ShopeeOrange
import com.autoscroll.app.ui.theme.SurfaceWhite
import com.autoscroll.app.ui.theme.TextPrimary
import com.autoscroll.app.ui.theme.TextSecondary
import com.autoscroll.app.util.CoordinateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroBuilderScreen(
    config: ScrollConfig,
    builderState: MacroBuilderState,
    onCreateNewMacro: () -> Unit,
    onStopBuilderAndShowSave: () -> Unit,
    onOpenTargetApp: () -> Unit,
    onUpdateDraftActions: (List<MacroAction>) -> Unit,
    onSaveMacro: (name: String, repeatCount: Int) -> Unit,
    onDiscardDraft: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    var macroName by remember(builderState.hasPendingSaveDraft) {
        mutableStateOf("Macro ${config.targetAppName}")
    }
    var repeatInput by remember(builderState.hasPendingSaveDraft) { mutableStateOf("1") }

    Scaffold(
        containerColor = BackgroundSoft,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (builderState.hasPendingSaveDraft) "Simpan Macro" else "Macro Builder",
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
            if (builderState.hasPendingSaveDraft) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        border = BorderStroke(1.dp, ShopeeOrange.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Simpan Macro (${builderState.actions.size} Gerakan)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Seluruh koordinat relatif (%) dan pengaturan durasi serta delay akan disimpan permanen ke Room Database.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

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

                            Spacer(modifier = Modifier.height(14.dp))
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
                                    enabled = builderState.actions.isNotEmpty(),
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

                itemsIndexed(builderState.actions) { index, action ->
                    DraftActionCard(
                        index = index,
                        lastIndex = builderState.actions.lastIndex,
                        action = action,
                        onMoveUp = {
                            onUpdateDraftActions(
                                CoordinateUtils.reorderAction(builderState.actions, index, index - 1)
                            )
                        },
                        onMoveDown = {
                            onUpdateDraftActions(
                                CoordinateUtils.reorderAction(builderState.actions, index, index + 1)
                            )
                        },
                        onDelete = {
                            onUpdateDraftActions(
                                CoordinateUtils.deleteActionAt(builderState.actions, index)
                            )
                        }
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        border = BorderStroke(1.dp, BorderHairline)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = if (builderState.isActive) "Macro Builder Aktif" else "Buat Macro Berbasis Koordinat",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tekan Create New Macro untuk memunculkan Floating Control di atas aplikasi lain seperti ${config.targetAppName}. Pilih titik layar langsung dengan Crosshair Picker tanpa mengandalkan event sentuhan mentah.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (builderState.isActive) onStopBuilderAndShowSave() else onCreateNewMacro()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (builderState.isActive) TextPrimary else ShopeeOrange
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AddLocationAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = if (builderState.isActive) "Stop & Save (${builderState.actions.size} Aksi)" else "Create New Macro",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                            if (builderState.isActive) {
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
                                imageVector = Icons.Rounded.TouchApp,
                                contentDescription = null,
                                tint = ShopeeOrange
                            )
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text(
                                    text = "Cara Kerja Coordinate Picker",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "1. Tekan Create New Macro lalu buka ${config.targetAppName}.\n" +
                                        "2. Tekan + Tambah Gerakan di Floating Control.\n" +
                                        "3. Geser crosshair ke tombol/area sasaran lalu tekan Confirm Location.\n" +
                                        "4. Pilih Action (Tap, Double Tap, Long Press, Swipe, Scroll Up/Down, Wait), Durasi, dan Delay.\n" +
                                        "5. Tekan Stop & Save untuk menyimpan ke My Macros.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftActionCard(
    index: Int,
    lastIndex: Int,
    action: MacroAction,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
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
                    text = "${index + 1}. ${action.note.ifBlank { CoordinateUtils.buildSummaryNote(action) }}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Durasi ${action.durationMs}ms · Delay berikutnya ${action.delayAfterMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Row {
                IconButton(onClick = onMoveUp, enabled = index > 0) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Naikkan")
                }
                IconButton(onClick = onMoveDown, enabled = index < lastIndex) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Turunkan")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Hapus", tint = Color(0xFFDC2626))
                }
            }
        }
    }
}
