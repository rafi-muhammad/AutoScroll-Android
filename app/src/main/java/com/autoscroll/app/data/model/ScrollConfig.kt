package com.autoscroll.app.data.model

enum class ScrollDirection(val label: String) {
    DOWN("Ke Bawah"),
    UP("Ke Atas")
}

enum class IntervalPreset(val label: String, val seconds: Long?) {
    SEC_30("30 Detik", 30L),
    MIN_1("1 Menit", 60L),
    MIN_2("2 Menit", 120L),
    MIN_3("3 Menit", 180L),
    MIN_5("5 Menit", 300L),
    CUSTOM("Custom", null)
}

enum class CustomTimeUnit(val label: String, val multiplierSeconds: Long) {
    SECONDS("Detik", 1L),
    MINUTES("Menit", 60L)
}

enum class ScrollSpeed(
    val label: String,
    val distanceFraction: Float
) {
    SLOW("Lambat", 0.30f),
    MEDIUM("Sedang", 0.48f),
    FAST("Cepat", 0.65f),
    VERY_FAST("Sangat Cepat", 0.80f)
}

data class TargetApp(
    val packageName: String,
    val displayName: String,
    val isInstalled: Boolean = false
) {
    companion object {
        val SHOPEE_ID = TargetApp(
            packageName = "com.shopee.id",
            displayName = "Shopee"
        )

        val DEFAULT_TARGETS = listOf(
            TargetApp("com.shopee.id", "Shopee (Indonesia)"),
            TargetApp("com.shopee.my", "Shopee (Malaysia)"),
            TargetApp("com.shopee.sg", "Shopee (Singapore)"),
            TargetApp("com.tokopedia.tkpd", "Tokopedia"),
            TargetApp("com.lazada.android", "Lazada"),
            TargetApp("com.ss.android.ugc.trill", "TikTok"),
            TargetApp("com.instagram.android", "Instagram")
        )
    }
}

enum class ServiceStatus(val label: String) {
    STOPPED("Nonaktif"),
    WAITING_FOR_TARGET("Menunggu Aplikasi Target"),
    ACTIVE("Aktif"),
    PAUSED("Dijeda"),
    PERMISSION_REQUIRED("Izin Diperlukan"),
    ERROR("Terjadi Kesalahan")
}

data class ScrollConfig(
    val intervalPreset: IntervalPreset = IntervalPreset.SEC_30,
    val customIntervalValue: Int = 45,
    val customTimeUnit: CustomTimeUnit = CustomTimeUnit.SECONDS,
    val direction: ScrollDirection = ScrollDirection.DOWN,
    val speed: ScrollSpeed = ScrollSpeed.MEDIUM,
    val durationMs: Long = 550L,
    val targetPackageName: String = TargetApp.SHOPEE_ID.packageName,
    val targetAppName: String = TargetApp.SHOPEE_ID.displayName
) {
    fun effectiveIntervalSeconds(): Long {
        val presetSec = intervalPreset.seconds
        if (presetSec != null) return presetSec
        val safeValue = customIntervalValue.coerceAtLeast(1)
        return (safeValue.toLong() * customTimeUnit.multiplierSeconds).coerceIn(2L, 3600L)
    }

    fun formattedInterval(): String {
        return when (intervalPreset) {
            IntervalPreset.CUSTOM -> {
                val safeValue = customIntervalValue.coerceAtLeast(1)
                "$safeValue ${customTimeUnit.label} (Custom)"
            }
            else -> intervalPreset.label
        }
    }
}

data class ScrollRuntimeState(
    val isServiceConnected: Boolean = false,
    val isEnabledByUser: Boolean = false,
    val isPaused: Boolean = false,
    val currentForegroundPackage: String = "",
    val isTargetAppInForeground: Boolean = false,
    val scrollCount: Int = 0,
    val remainingSeconds: Long = 30L,
    val lastErrorMessage: String? = null
) {
    fun resolveStatus(hasRequiredPermissions: Boolean): ServiceStatus {
        if (!hasRequiredPermissions) return ServiceStatus.PERMISSION_REQUIRED
        if (lastErrorMessage != null && !isEnabledByUser) return ServiceStatus.ERROR
        if (!isEnabledByUser) return ServiceStatus.STOPPED
        if (isPaused) return ServiceStatus.PAUSED
        if (!isTargetAppInForeground) return ServiceStatus.WAITING_FOR_TARGET
        return ServiceStatus.ACTIVE
    }
}
