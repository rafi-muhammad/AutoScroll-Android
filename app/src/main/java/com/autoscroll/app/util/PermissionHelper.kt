package com.autoscroll.app.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.autoscroll.app.data.model.TargetApp
import com.autoscroll.app.service.AutoScrollAccessibilityService

data class PermissionStatus(
    val isAccessibilityEnabled: Boolean = false,
    val isOverlayEnabled: Boolean = false
) {
    val allGranted: Boolean
        get() = isAccessibilityEnabled && isOverlayEnabled
}

object PermissionHelper {

    fun checkPermissions(context: Context): PermissionStatus {
        return PermissionStatus(
            isAccessibilityEnabled = isAccessibilityServiceEnabled(context),
            isOverlayEnabled = canDrawOverlays(context)
        )
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, AutoScrollAccessibilityService::class.java)
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        if (enabledServices != null) {
            val matched = enabledServices.any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo
                serviceInfo != null &&
                    serviceInfo.packageName == expectedComponent.packageName &&
                    serviceInfo.name == expectedComponent.className
            }
            if (matched) return true
        }

        val enabledSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val flattened = expectedComponent.flattenToString()
        val shortFlattened = expectedComponent.flattenToShortString()
        return enabledSetting.split(':').any { entry ->
            entry.equals(flattened, ignoreCase = true) ||
                entry.equals(shortFlattened, ignoreCase = true)
        }
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openOverlaySettings(context: Context) {
        val packageIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { context.startActivity(packageIntent) }.isSuccess
        if (!launched) {
            val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }

    fun getTargetAppCatalog(context: Context): List<TargetApp> {
        val pm = context.packageManager
        val curated = TargetApp.DEFAULT_TARGETS.map { preset ->
            preset.copy(isInstalled = isPackageInstalled(pm, preset.packageName))
        }

        val curatedPackages = curated.map { it.packageName }.toSet()
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val extraInstalled = runCatching {
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, 0)
            }
            resolved.mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName || pkg in curatedPackages) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                TargetApp(packageName = pkg, displayName = label, isInstalled = true)
            }.distinctBy { it.packageName }.sortedBy { it.displayName.lowercase() }
        }.getOrDefault(emptyList())

        return curated + extraInstalled
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }
}
