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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoscroll.app.data.model.CustomTimeUnit
import com.autoscroll.app.data.model.IntervalPreset
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.data.model.ScrollDirection
import com.autoscroll.app.data.model.ScrollRuntimeState
import com.autoscroll.app.data.model.ScrollSpeed
import com.autoscroll.app.data.model.ServiceStatus
import com.autoscroll.app.data.model.TargetApp
import com.autoscroll.app.ui.components.IntervalSelectorCard
import com.autoscroll.app.ui.components.ScrollSettingsCard
import com.autoscroll.app.ui.components.TargetAppCard
import com.autoscroll.app.ui.theme.BackgroundSoft
import com.autoscroll.app.ui.theme.BorderHairline
import com.autoscroll.app.ui.theme.ShopeeOrange
import com.autoscroll.app.ui.theme.ShopeeOrangeSoft
import com.autoscroll.app.ui.theme.StatusActiveGreen
import com.autoscroll.app.ui.theme.StatusActiveGreenSoft
import com.autoscroll.app.ui.theme.StatusWarningAmber
import com.autoscroll.app.ui.theme.StatusWarningAmberSoft
import com.autoscroll.app.ui.theme.SurfaceWhite
import com.autoscroll.app.ui.theme.TextPrimary
import com.autoscroll.app.ui.theme.TextSecondary
import com.autoscroll.app.util.PermissionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    config: ScrollConfig,
    runtimeState: ScrollRuntimeState,
    permissions: PermissionStatus,
    targetApps: List<TargetApp>,
    uiMessage: String?,
    onClearMessage: () -> Unit,
    onToggleStartStop: () -> Unit,
    onResetCounter: () -> Unit,
    onSelectPreset: (IntervalPreset) -> Unit,
    onApplyCustom: (Int, CustomTimeUnit) -> Unit,
    onSelectDirection: (ScrollDirection) -> Unit,
    onSelectSpeed: (ScrollSpeed) -> Unit,
    onDurationChange: (Long) -> Unit,
    onSelectTargetApp: (TargetApp) -> Unit,
    onOpenTargetApp: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val status = runtimeState.resolveStatus(permissions.allGranted)

    LaunchedEffect(uiMessage) {
        if (!uiMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(uiMessage)
            onClearMessage()
        }
    }

    Scaffold(
        containerColor = BackgroundSoft,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Auto Scroll",
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
            if (!permissions.allGranted) {
                item {
                    PermissionAlertBanner(onOpenPermissions = onOpenPermissions)
                }
            }

            item {
                PrimaryControlCard(
                    status = status,
                    config = config,
                    runtimeState = runtimeState,
                    onToggleStartStop = onToggleStartStop,
                    onResetCounter = onResetCounter
                )
            }

            item {
                TargetAppCard(
                    config = config,
                    targetApps = targetApps,
                    onSelectApp = onSelectTargetApp,
                    onOpenTargetApp = onOpenTargetApp
                )
            }

            item {
                IntervalSelectorCard(
                    config = config,
                    onSelectPreset = onSelectPreset,
                    onApplyCustom = onApplyCustom
                )
            }

            item {
                ScrollSettingsCard(
                    config = config,
                    onSelectDirection = onSelectDirection,
                    onSelectSpeed = onSelectSpeed,
                    onDurationChange = onDurationChange
                )
            }
        }
    }
}

@Composable
private fun PermissionAlertBanner(onOpenPermissions: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StatusWarningAmberSoft),
        border = BorderStroke(1.dp, StatusWarningAmber.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = StatusWarningAmber
                )
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        text = "Izin Layanan Belum Lengkap",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Aktifkan Aksesibilitas & Overlay agar auto scroll berjalan di Shopee.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            TextButton(onClick = onOpenPermissions) {
                Text("Atur", color = ShopeeOrange, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PrimaryControlCard(
    status: ServiceStatus,
    config: ScrollConfig,
    runtimeState: ScrollRuntimeState,
    onToggleStartStop: () -> Unit,
    onResetCounter: () -> Unit
) {
    val isRunning = runtimeState.isEnabledByUser
    val statusColor = when (status) {
        ServiceStatus.ACTIVE -> StatusActiveGreen
        ServiceStatus.WAITING_FOR_TARGET, ServiceStatus.PAUSED -> StatusWarningAmber
        ServiceStatus.ERROR, ServiceStatus.PERMISSION_REQUIRED -> Color(0xFFDC2626)
        ServiceStatus.STOPPED -> TextSecondary
    }
    val statusBg = when (status) {
        ServiceStatus.ACTIVE -> StatusActiveGreenSoft
        ServiceStatus.WAITING_FOR_TARGET, ServiceStatus.PAUSED -> StatusWarningAmberSoft
        ServiceStatus.ERROR, ServiceStatus.PERMISSION_REQUIRED -> Color(0xFFFEF2F2)
        ServiceStatus.STOPPED -> Color(0xFFF1F5F9)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, BorderHairline)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Status Layanan",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = statusBg)
                ) {
                    Text(
                        text = if (isRunning) "${runtimeState.remainingSeconds}d" else "Siap",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleStartStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) TextPrimary else ShopeeOrange
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = if (isRunning) "Hentikan Auto Scroll" else "Mulai Auto Scroll",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Interval Terpilih", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = config.formattedInterval(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Jumlah Scroll", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${runtimeState.scrollCount} kali",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ShopeeOrange
                        )
                    }
                    if (runtimeState.scrollCount > 0) {
                        IconButton(onClick = onResetCounter) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Reset Hitungan",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            if (isRunning && !runtimeState.isTargetAppInForeground) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = ShopeeOrangeSoft)
                ) {
                    Text(
                        text = "Buka ${config.targetAppName} sekarang — scroll akan berjalan otomatis saat aplikasi target aktif di layar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ShopeeOrange,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
