package com.tcltv.sahne.ui.home

import android.app.ActivityOptions
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tcltv.sahne.LauncherViewModel
import com.tcltv.sahne.data.LaunchableApp
import com.tcltv.sahne.ui.LocalReduceMotion
import kotlinx.coroutines.launch

/** Where the selected app card sits on screen, so an app can open out of it. Cards update it as they get focus. */
class LaunchOrigin {
    var bounds: Rect? = null

    /** Set when an app was opened with the animation, so coming back to Matinee can play the return. */
    var openedApp = false
}

val LocalLaunchOrigin = staticCompositionLocalOf { LaunchOrigin() }

/** Whether apps open out of their card and the home screen settles back on return; off with the setting or reduced motion. */
val LocalLaunchAnimation = staticCompositionLocalOf { true }

/** Opens an app the way tvOS does: its window grows out of the card that was selected. */
@Composable
fun rememberAppLauncher(viewModel: LauncherViewModel): (LaunchableApp) -> Unit {
    val view = LocalView.current
    val origin = LocalLaunchOrigin.current
    val animated = LocalLaunchAnimation.current
    return remember(viewModel, view, origin, animated) {
        { app ->
            val bounds = origin.bounds
            val options = if (animated && bounds != null && bounds.width > 0f && app.folderId == null) {
                origin.openedApp = true
                ActivityOptions.makeScaleUpAnimation(
                    view,
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.width.toInt(),
                    bounds.height.toInt(),
                ).toBundle()
            } else {
                null
            }
            viewModel.launchApp(app, options)
        }
    }
}

/**
 * Coming back from an app opened with the animation, the home screen starts slightly enlarged and faded and settles
 * into place, as if the app had shrunk back into the row.
 */
@Composable
fun Modifier.settleOnReturn(resumeTick: Int): Modifier {
    val origin = LocalLaunchOrigin.current
    val animated = LocalLaunchAnimation.current
    val progress = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    // Set up while the home screen is hidden behind the app, so it never shows a frame in its final place first.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (origin.openedApp && animated) scope.launch { progress.snapTo(0f) }
    }
    LaunchedEffect(resumeTick) {
        origin.openedApp = false
        if (progress.value < 1f) progress.animateTo(1f, tween(RETURN_MILLIS, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        val p = progress.value
        val scale = RETURN_SCALE + (1f - RETURN_SCALE) * p
        scaleX = scale
        scaleY = scale
        alpha = RETURN_ALPHA + (1f - RETURN_ALPHA) * p
    }
}

/**
 * Scrolls rows and the grid with a softer, unhurried glide, the way tvOS moves between items, while keeping where each
 * list already chooses to stop.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmoothScrolling(content: @Composable () -> Unit) {
    val base = LocalBringIntoViewSpec.current
    val reduceMotion = LocalReduceMotion.current
    val spec = remember(base, reduceMotion) {
        object : BringIntoViewSpec {
            override val scrollAnimationSpec: AnimationSpec<Float> =
                if (reduceMotion) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = SCROLL_STIFFNESS)

            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                base.calculateScrollDistance(offset, size, containerSize)
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

private const val RETURN_MILLIS = 380
private const val RETURN_SCALE = 1.06f
private const val RETURN_ALPHA = 0.35f
private const val SCROLL_STIFFNESS = 260f
