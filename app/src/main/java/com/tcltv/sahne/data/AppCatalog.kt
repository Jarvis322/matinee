package com.tcltv.sahne.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.tcltv.sahne.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

data class LaunchableApp(
    val packageName: String,
    val component: ComponentName,
    val label: String,
    val accent: Int,
    val icon: ImageBitmap?,
    /** The icon's transparent foreground layer when the app ships an adaptive icon, else its full icon. */
    val glyph: ImageBitmap?,
    /** Average lightness of the glyph's visible pixels, 0 (black) to 1 (white); null when there is no glyph. */
    val glyphLightness: Float?,
    /** The app's own 16:9 TV banner, when it ships one; it already carries the app's name. */
    val banner: ImageBitmap?,
    @StringRes val tagline: Int,
    /** Signed with the platform key: an OEM system component rather than a user-facing app. */
    val isSystemComponent: Boolean,
    /** Declares a TV (leanback) launcher activity; phone-style apps only have a regular launcher activity. */
    val isTvApp: Boolean,
    /** Needs the parental PIN to open. */
    val isLocked: Boolean = false,
    /** Set when this card is a folder rather than an app; [packageName] is then the folder's key. */
    val folderId: String? = null,
)

private data class KnownApp(val accent: Int, @StringRes val tagline: Int)

private val knownApps = mapOf(
    "com.netflix.ninja" to KnownApp(0xFFE50914.toInt(), R.string.tagline_netflix),
    "com.google.android.youtube.tv" to KnownApp(0xFFE0281F.toInt(), R.string.tagline_youtube),
    "com.google.android.youtube.tvmusic" to KnownApp(0xFFE0281F.toInt(), R.string.tagline_youtube_music),
    "com.google.android.videos" to KnownApp(0xFF2F6FE0.toInt(), R.string.tagline_google_tv),
    "com.spotify.tv.android" to KnownApp(0xFF1DB954.toInt(), R.string.tagline_spotify),
    "com.android.vending" to KnownApp(0xFFE8A21A.toInt(), R.string.tagline_play_store),
    "com.android.tv.settings" to KnownApp(0xFF6B6E78.toInt(), R.string.tagline_settings),
    "com.amazon.amazonvideo.livingroom" to KnownApp(0xFF1A98FF.toInt(), R.string.tagline_prime_video),
)

private val defaultOrder = listOf(
    "com.netflix.ninja",
    "com.google.android.youtube.tv",
    "com.google.android.videos",
    "com.spotify.tv.android",
    "com.android.vending",
    "com.android.tv.settings",
)

class AppCatalog(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    @OptIn(FlowPreview::class)
    val apps: Flow<List<LaunchableApp>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(false)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        // true marks the first load, which must not wait.
        trySend(true)
        awaitClose { appContext.unregisterReceiver(receiver) }
    }
        // One app update sends REMOVED, ADDED and CHANGED back to back; reload once after the burst, not per broadcast.
        .debounce { initial -> if (initial) 0L else RELOAD_DEBOUNCE_MS }
        .conflate()
        .map { load() }
        .flowOn(Dispatchers.IO)

    private fun load(): List<LaunchableApp> {
        fun query(category: String) =
            packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), PackageManager.MATCH_ALL)
        val tvActivities = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val tvPackages = tvActivities.mapTo(HashSet()) { it.activityInfo.packageName }
        // Many TV boxes also run phone-style apps (some satellite and IPTV players) that have no TV launcher entry.
        return (tvActivities + query(Intent.CATEGORY_LAUNCHER))
            .filter { it.activityInfo.packageName != appContext.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val pkg = info.activityInfo.packageName
                val known = knownApps[pkg]
                // The app's own icon, never a bundled brand logo; sized for the large watermark. Inflated once for both uses.
                val iconDrawable = runCatching { info.loadIcon(packageManager) }.getOrNull()
                val iconBitmap = iconDrawable?.let { runCatching { it.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX) }.getOrNull() }
                val glyph = iconDrawable?.let { runCatching { glyphBitmap(it) }.getOrNull() }
                LaunchableApp(
                    packageName = pkg,
                    component = ComponentName(pkg, info.activityInfo.name),
                    label = info.loadLabel(packageManager).toString(),
                    accent = known?.accent ?: iconBitmap?.let(::dominantColor) ?: 0xFF5B5E68.toInt(),
                    icon = iconBitmap?.asImageBitmap(),
                    glyph = glyph?.asImageBitmap() ?: iconBitmap?.asImageBitmap(),
                    glyphLightness = glyph?.let(::lightness),
                    banner = runCatching {
                        (info.activityInfo.loadBanner(packageManager) ?: info.activityInfo.applicationInfo.loadBanner(packageManager))
                            ?.toBitmap(BANNER_WIDTH_PX, BANNER_HEIGHT_PX)?.asImageBitmap()
                    }.getOrNull(),
                    tagline = known?.tagline ?: R.string.tagline_default,
                    isSystemComponent = pkg !in alwaysVisible &&
                        packageManager.checkSignatures(PLATFORM_PACKAGE, pkg) == PackageManager.SIGNATURE_MATCH,
                    isTvApp = pkg in tvPackages,
                )
            }
    }

    fun launchIntent(app: LaunchableApp): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(if (app.isTvApp) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    companion object {
        private const val PLATFORM_PACKAGE = "android"
        private const val RELOAD_DEBOUNCE_MS = 600L
        private const val ICON_SIZE_PX = 192
        private const val GLYPH_LAYER_PX = 216
        private const val BANNER_WIDTH_PX = 320
        private const val BANNER_HEIGHT_PX = 180
        private val alwaysVisible = setOf("com.android.tv.settings")

        /** Phone-style apps start off the home row and are added from the add-app list; TV apps start on it. */
        fun isHidden(app: LaunchableApp, settings: LauncherSettings): Boolean =
            app.packageName in settings.hiddenApps ||
                (app.packageName !in settings.shownApps &&
                    (!app.isTvApp || (app.isSystemComponent && !settings.showSystemApps)))

        /** Phone-style system components (test tools, OEM utilities) stay out of every list unless system apps are shown. */
        private fun isListed(app: LaunchableApp, settings: LauncherSettings): Boolean =
            app.isTvApp || !app.isSystemComponent || settings.showSystemApps || app.packageName in settings.shownApps

        fun ordered(apps: List<LaunchableApp>, settings: LauncherSettings): List<LaunchableApp> {
            val order = settings.appOrder.ifEmpty { defaultOrder }
            val rank = order.withIndex().associate { it.value to it.index }
            return apps
                .filter { isListed(it, settings) && !isHidden(it, settings) }
                .sortedWith(compareBy<LaunchableApp> { rank[it.packageName] ?: Int.MAX_VALUE }.thenBy { it.label.lowercase() })
        }

        fun orderedIncludingHidden(apps: List<LaunchableApp>, settings: LauncherSettings): List<LaunchableApp> {
            val order = settings.appOrder.ifEmpty { defaultOrder }
            val rank = order.withIndex().associate { it.value to it.index }
            return apps
                .filter { isListed(it, settings) }
                .sortedWith(compareBy<LaunchableApp> { rank[it.packageName] ?: Int.MAX_VALUE }.thenBy { it.label.lowercase() })
        }

        /** Adaptive icons keep the logo inside the central 72dp of a 108dp layer; crop to it so the logo isn't tiny. */
        private fun glyphBitmap(drawable: Drawable): Bitmap? {
            if (drawable !is AdaptiveIconDrawable) return null
            val foreground = drawable.foreground ?: return null
            val full = foreground.toBitmap(GLYPH_LAYER_PX, GLYPH_LAYER_PX)
            val inset = GLYPH_LAYER_PX * 18 / 108
            return Bitmap.createBitmap(full, inset, inset, GLYPH_LAYER_PX - 2 * inset, GLYPH_LAYER_PX - 2 * inset)
        }

        private fun lightness(bitmap: Bitmap): Float? {
            var sum = 0f
            var count = 0
            val step = (bitmap.width / 24).coerceAtLeast(1)
            for (x in 0 until bitmap.width step step) {
                for (y in 0 until bitmap.height step step) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.alpha(pixel) < 128) continue
                    sum += (0.299f * Color.red(pixel) + 0.587f * Color.green(pixel) + 0.114f * Color.blue(pixel)) / 255f
                    count++
                }
            }
            return if (count == 0) null else sum / count
        }

        internal fun dominantColor(bitmap: Bitmap): Int {
            val hsv = FloatArray(3)
            var bestScore = -1f
            var best = 0xFF5B5E68.toInt()
            val step = (bitmap.width / 16).coerceAtLeast(1)
            for (x in 0 until bitmap.width step step) {
                for (y in 0 until bitmap.height step step) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.alpha(pixel) < 200) continue
                    Color.colorToHSV(pixel, hsv)
                    val score = hsv[1] * hsv[2]
                    if (score > bestScore) {
                        bestScore = score
                        best = pixel
                    }
                }
            }
            return best
        }
    }
}
