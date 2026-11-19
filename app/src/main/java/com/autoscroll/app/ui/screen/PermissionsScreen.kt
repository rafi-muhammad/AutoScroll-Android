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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoscroll.app.ui.theme.BackgroundSoft
import com.autoscroll.app.ui.theme.BorderHairline
import com.autoscroll.app.ui.theme.ShopeeOrange
import com.autoscroll.app.ui.theme.StatusActiveGreen
import com.autoscroll.app.ui.theme.StatusActiveGreenSoft
import com.autoscroll.app.ui.theme.SurfaceWhite
import com.autoscroll.app.ui.theme.TextPrimary
import com.autoscroll.app.ui.theme.TextSecondary
import com.autoscroll.app.util.PermissionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    permissions: PermissionStatus,
    onBack: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit
) {
    Scaffold(
        containerColor = BackgroundSoft,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pengaturan Izin",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Kembali"
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
                Text(
                    text = "Auto Scroll hanya membutuhkan 2 izin sistem tanpa akses root. Kedua izin ini diperlukan agar aplikasi dapat mengirim gesture scroll di Shopee dan menampilkan tombol mengambang.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            item {
                PermissionItemCard(
                    icon = Icons.Rounded.AccessibilityNew,
                    title = "1. Layanan Aksesibilitas",
                    description = "Mengaktifkan dispatchGesture() untuk scroll layar otomatis saat aplikasi target seperti Shopee sedang terbuka.",
                    isGranted = permissions.isAccessibilityEnabled,
                    buttonLabel = if (permissions.isAccessibilityEnabled) "Aktif" else "Buka Aksesibilitas",
                    onClick = onOpenAccessibility
                )
            }

            item {
                PermissionItemCard(
                    icon = Icons.Rounded.Layers,
                    title = "2. Tampil di Atas Aplikasi Lain (Overlay)",
                    description = "Izin SYSTEM_ALERT_WINDOW untuk menampilkan Floating Control (Pause, Resume, Stop) di atas aplikasi Shopee.",
                    isGranted = permissions.isOverlayEnabled,
                    buttonLabel = if (permissions.isOverlayEnabled) "Aktif" else "Izinkan Overlay",
                    onClick = onOpenOverlay
                )
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, BorderHairline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) StatusActiveGreen else ShopeeOrange
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
                if (isGranted) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = StatusActiveGreenSoft)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = StatusActiveGreen
                            )
                            Text(
                                text = "Diizinkan",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusActiveGreen,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) StatusActiveGreen else ShopeeOrange
                )
            ) {
                Text(text = buttonLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
