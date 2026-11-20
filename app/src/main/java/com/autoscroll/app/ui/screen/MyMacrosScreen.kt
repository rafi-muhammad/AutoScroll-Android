package com.autoscroll.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.autoscroll.app.data.model.MacroAction
import com.autoscroll.app.data.model.MacroActionType
import com.autoscroll.app.data.model.MacroPlaybackState
import com.autoscroll.app.data.model.MacroRecord
import com.autoscroll.app.ui.theme.BackgroundSoft
import com.autoscroll.app.ui.theme.BorderHairline
import com.autoscroll.app.ui.theme.ShopeeOrange
import com.autoscroll.app.ui.theme.StatusActiveGreen
import com.autoscroll.app.ui.theme.StatusActiveGreenSoft
import com.autoscroll.app.ui.theme.SurfaceWhite
import com.autoscroll.app.ui.theme.TextPrimary
import com.autoscroll.app.ui.theme.TextSecondary
import com.autoscroll.app.util.CoordinateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMacrosScreen(
    macros: List<MacroRecord>,
    playbackState: MacroPlaybackState,
    onCreateNewMacro: () -> Unit,
    onUseMacro: (MacroRecord) -> Unit,
    onRenameMacro: (Long, String) -> Unit,
    onUpdateRepeat: (Long, Int) -> Unit,
    onSaveEditedActions: (Long, List<MacroAction>) -> Unit,
    onDeleteMacro: (Long) -> Unit,
    onOpenPermissions: () -> Unit
) {
    var renamingMacro by remember { mutableStateOf<MacroRecord?>(null) }
    var repeatEditingMacro by remember { mutableStateOf<MacroRecord?>(null) }
    var actionsEditingMacro by remember { mutableStateOf<MacroRecord?>(null) }
    var deletingMacro by remember { mutableStateOf<MacroRecord?>(null) }

    Scaffold(
        containerColor = BackgroundSoft,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Macros",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                actions = {
                    TextButton(onClick = onCreateNewMacro) {
                        Icon(imageVector = Icons.Rounded.Add, contentDescription = null, tint = ShopeeOrange)
                        Text("Create New", color = ShopeeOrange, fontWeight = FontWeight.Bold)
                    }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (macros.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        border = BorderStroke(1.dp, BorderHairline)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Belum Ada Macro Tersimpan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Buat macro koordinat langsung di atas aplikasi target menggunakan Floating Crosshair Picker lalu simpan ke Room Database.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onCreateNewMacro,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ShopeeOrange)
                            ) {
                                Icon(imageVector = Icons.Rounded.AddLocationAlt, contentDescription = null)
                                Text("Create New Macro", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }

            items(macros, key = { it.id }) { macro ->
                val isActive = playbackState.activeMacro?.id == macro.id
                MacroItemCard(
                    macro = macro,
                    isActive = isActive,
                    onUseMacro = { onUseMacro(macro) },
                    onRename = { renamingMacro = macro },
                    onEditRepeat = { repeatEditingMacro = macro },
                    onEditActions = { actionsEditingMacro = macro },
                    onDelete = { deletingMacro = macro }
                )
            }
        }
    }

    renamingMacro?.let { target ->
        var nameDraft by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renamingMacro = null },
            title = { Text("Ganti Nama Macro") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("Nama baru") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameMacro(target.id, nameDraft)
                        renamingMacro = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ShopeeOrange)
                ) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { renamingMacro = null }) { Text("Batal") }
            }
        )
    }

    repeatEditingMacro?.let { target ->
        var repeatDraft by remember(target.id) { mutableStateOf(target.repeatCount.toString()) }
        AlertDialog(
            onDismissRequest = { repeatEditingMacro = null },
            title = { Text("Atur Pengulangan (Repeat)") },
            text = {
                OutlinedTextField(
                    value = repeatDraft,
                    onValueChange = { repeatDraft = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("Jumlah Repeat (1 - 999)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = repeatDraft.toIntOrNull()?.coerceIn(1, 999) ?: 1
                        onUpdateRepeat(target.id, parsed)
                        repeatEditingMacro = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ShopeeOrange)
                ) { Text("Terapkan") }
            },
            dismissButton = {
                TextButton(onClick = { repeatEditingMacro = null }) { Text("Batal") }
            }
        )
    }

    deletingMacro?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingMacro = null },
            title = { Text("Hapus Macro?") },
            text = { Text("Macro \"${target.name}\" beserta ${target.actions.size} gerakan di dalamnya akan dihapus permanen.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMacro(target.id)
                        deletingMacro = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { deletingMacro = null }) { Text("Batal") }
            }
        )
    }

    actionsEditingMacro?.let { target ->
        MacroActionsEditorSheet(
            macro = target,
            onDismiss = { actionsEditingMacro = null },
            onSave = { updated ->
                onSaveEditedActions(target.id, updated)
                actionsEditingMacro = null
            }
        )
    }
}

@Composable
private fun MacroItemCard(
    macro: MacroRecord,
    isActive: Boolean,
    onUseMacro: () -> Unit,
    onRename: () -> Unit,
    onEditRepeat: () -> Unit,
    onEditActions: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, if (isActive) ShopeeOrange else BorderHairline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = macro.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${macro.targetAppName} · ${macro.actions.size} aksi · Repeat ${macro.repeatCount}x",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (isActive) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = StatusActiveGreenSoft)
                    ) {
                        Text(
                            text = "Aktif",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusActiveGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onUseMacro,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) TextPrimary else ShopeeOrange
                )
            ) {
                Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                Text(
                    text = if (isActive) "Aktif di Floating Control" else "Use Macro (Buka Floating Control)",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onRename) {
                    Text("Ganti Nama", color = TextPrimary)
                }
                TextButton(onClick = onEditActions) {
                    Icon(imageVector = Icons.Rounded.Edit, contentDescription = null, tint = ShopeeOrange)
                    Text("Edit Gerakan", color = ShopeeOrange, modifier = Modifier.padding(start = 4.dp))
                }
                TextButton(onClick = onEditRepeat) {
                    Icon(imageVector = Icons.Rounded.Repeat, contentDescription = null, tint = TextPrimary)
                    Text("${macro.repeatCount}x", color = TextPrimary, modifier = Modifier.padding(start = 4.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Hapus Macro",
                        tint = Color(0xFFDC2626)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MacroActionsEditorSheet(
    macro: MacroRecord,
    onDismiss: () -> Unit,
    onSave: (List<MacroAction>) -> Unit
) {
    var draftActions by remember(macro.id) { mutableStateOf(macro.actions) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Edit Gerakan: ${macro.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Ubah urutan, koordinat (%), durasi, atau delay.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = {
                        val nextIdx = draftActions.size
                        val added = MacroAction(
                            orderIndex = nextIdx,
                            type = MacroActionType.TAP,
                            startXPercent = 0.50f,
                            startYPercent = 0.55f,
                            endXPercent = 0.50f,
                            endYPercent = 0.25f,
                            durationMs = 80L,
                            delayAfterMs = 800L,
                            note = "Tap (50.0%, 55.0%)"
                        )
                        draftActions = draftActions + added
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null, tint = ShopeeOrange)
                    Text("Tambah", color = ShopeeOrange)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(draftActions) { index, action ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BackgroundSoft),
                        border = BorderStroke(1.dp, BorderHairline)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}. ${action.type.label}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            draftActions = CoordinateUtils.reorderAction(draftActions, index, index - 1)
                                        },
                                        enabled = index > 0
                                    ) {
                                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Naikkan")
                                    }
                                    IconButton(
                                        onClick = {
                                            draftActions = CoordinateUtils.reorderAction(draftActions, index, index + 1)
                                        },
                                        enabled = index < draftActions.lastIndex
                                    ) {
                                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Turunkan")
                                    }
                                    IconButton(
                                        onClick = {
                                            draftActions = CoordinateUtils.deleteActionAt(draftActions, index)
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Hapus Aksi", tint = Color(0xFFDC2626))
                                    }
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MacroActionType.entries.forEach { typeOpt ->
                                    FilterChip(
                                        selected = action.type == typeOpt,
                                        onClick = {
                                            val updated = action.copy(
                                                type = typeOpt,
                                                durationMs = CoordinateUtils.defaultDurationFor(typeOpt)
                                            )
                                            draftActions = draftActions.toMutableList().also { list ->
                                                list[index] = updated.copy(note = CoordinateUtils.buildSummaryNote(updated))
                                            }
                                        },
                                        label = { Text(typeOpt.label) }
                                    )
                                }
                            }

                            if (action.type != MacroActionType.WAIT) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = (action.startXPercent * 100f).toInt().toString(),
                                        onValueChange = { raw ->
                                            val pct = (raw.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 99) ?: 50) / 100f
                                            val updated = action.copy(startXPercent = pct)
                                            draftActions = draftActions.toMutableList().also {
                                                it[index] = updated.copy(note = CoordinateUtils.buildSummaryNote(updated))
                                            }
                                        },
                                        label = { Text("Start X (%)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = (action.startYPercent * 100f).toInt().toString(),
                                        onValueChange = { raw ->
                                            val pct = (raw.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 99) ?: 55) / 100f
                                            val updated = action.copy(startYPercent = pct)
                                            draftActions = draftActions.toMutableList().also {
                                                it[index] = updated.copy(note = CoordinateUtils.buildSummaryNote(updated))
                                            }
                                        },
                                        label = { Text("Start Y (%)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            if (action.type == MacroActionType.SWIPE) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = (action.endXPercent * 100f).toInt().toString(),
                                        onValueChange = { raw ->
                                            val pct = (raw.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 99) ?: 50) / 100f
                                            val updated = action.copy(endXPercent = pct)
                                            draftActions = draftActions.toMutableList().also {
                                                it[index] = updated.copy(note = CoordinateUtils.buildSummaryNote(updated))
                                            }
                                        },
                                        label = { Text("End X (%)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = (action.endYPercent * 100f).toInt().toString(),
                                        onValueChange = { raw ->
                                            val pct = (raw.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 99) ?: 25) / 100f
                                            val updated = action.copy(endYPercent = pct)
                                            draftActions = draftActions.toMutableList().also {
                                                it[index] = updated.copy(note = CoordinateUtils.buildSummaryNote(updated))
                                            }
                                        },
                                        label = { Text("End Y (%)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = action.durationMs.toString(),
                                    onValueChange = { raw ->
                                        val valMs = raw.filter { it.isDigit() }.toLongOrNull()?.coerceIn(0L, 5000L) ?: action.durationMs
                                        draftActions = draftActions.toMutableList().also { list ->
                                            list[index] = action.copy(durationMs = valMs)
                                        }
                                    },
                                    label = { Text("Durasi (ms)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = action.delayAfterMs.toString(),
                                    onValueChange = { raw ->
                                        val valDelay = raw.filter { it.isDigit() }.toLongOrNull()?.coerceIn(0L, 60_000L) ?: action.delayAfterMs
                                        draftActions = draftActions.toMutableList().also { list ->
                                            list[index] = action.copy(delayAfterMs = valDelay)
                                        }
                                    },
                                    label = { Text("Delay (ms)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Batal") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSave(draftActions) },
                    colors = ButtonDefaults.buttonColors(containerColor = ShopeeOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Simpan Perubahan", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
