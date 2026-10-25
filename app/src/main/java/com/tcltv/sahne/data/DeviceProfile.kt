package com.tcltv.sahne.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import com.tcltv.sahne.R
import java.io.File

/** How much the TV can draw: decides whether the heavier visual effects start on or off. */
enum class PerformanceTier(@StringRes val label: Int) {
    LIGHT(R.string.performance_light),
    BALANCED(R.string.performance_balanced),
    FULL(R.string.performance_full),
}

/**
 * What this TV runs on, read once at start. It only picks the defaults for the glass and the moving light; anything
 * the user has switched themselves is kept.
 */
class DeviceProfile(
    /** The chip as the system names it, such as "MediaTek MT9615"; empty when the TV doesn't say. */
    val chip: String,
    val cores: Int,
    val memoryBytes: Long,
    val tier: PerformanceTier,
) {
    /** The settings this TV starts with before the user changes anything. */
    fun defaults(): LauncherSettings = LauncherSettings(
        glassEffects = tier != PerformanceTier.LIGHT,
        wallpaperAnimated = tier == PerformanceTier.FULL,
    )

    companion object {
        @Volatile
        private var cached: DeviceProfile? = null

        fun of(context: Context): DeviceProfile = cached ?: detect(context.applicationContext).also { cached = it }

        private fun detect(context: Context): DeviceProfile {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }.totalMem
            val cores = Runtime.getRuntime().availableProcessors()
            val chip = chipName()
            val gib = memory / (1024.0 * 1024 * 1024)
            val tier = when {
                activityManager.isLowRamDevice || gib < 1.6 || cores <= 2 -> PerformanceTier.LIGHT
                // Shield-class boxes and 4 GB TVs with a modern 64-bit chip keep everything on, moving light included.
                isFastChip(chip) || (gib >= 3.5 && cores >= 6 && Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) -> PerformanceTier.FULL
                else -> PerformanceTier.BALANCED
            }
            return DeviceProfile(chip, cores, memory, tier)
        }

        private fun chipName(): String {
            if (Build.VERSION.SDK_INT >= 31) {
                val name = listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
                    .filter { it.isNotBlank() && it != Build.UNKNOWN }
                    .joinToString(" ")
                if (name.isNotBlank()) return name
            }
            // Older TVs name the chip only in /proc/cpuinfo's "Hardware" line, or in the board name.
            val hardware = runCatching {
                File("/proc/cpuinfo").useLines { lines ->
                    lines.firstOrNull { it.startsWith("Hardware") }?.substringAfter(':')?.trim()
                }
            }.getOrNull()
            return listOf(hardware, Build.HARDWARE, Build.BOARD).firstOrNull { !it.isNullOrBlank() && it != Build.UNKNOWN }.orEmpty()
        }

        private fun isFastChip(chip: String): Boolean {
            val name = chip.lowercase()
            return FAST_CHIPS.any { it in name }
        }

        /** Chips known to handle the glass and the moving light easily, whatever the memory reports. */
        private val FAST_CHIPS = listOf("tegra", "nvidia", "s905x4", "s928x", "s908x", "sm8", "sm6")
    }
}
