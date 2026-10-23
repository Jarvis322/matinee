package com.tcltv.sahne.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.layout.LayoutCoordinates
import com.tcltv.sahne.data.ResolvedWallpaper
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

/**
 * The screen's backdrop, shared with glass surfaces so each one can show its own slice of it, which reads as frosted
 * glass. The backdrop is already a small, soft bitmap, so this costs one cheap image draw per surface; a live blur
 * of whatever is behind would cost far more than this TV's GPU can spare.
 */
class BackdropState {
    var from by mutableStateOf<ImageBitmap?>(null)
    var to by mutableStateOf<ImageBitmap?>(null)

    /** 0 shows [from], 1 shows [to]; the glass follows the backdrop's own cross-fade. */
    var progress by mutableFloatStateOf(1f)
}

val LocalBackdrop = staticCompositionLocalOf { BackdropState() }

/** The wallpaper the user chose, or null while the colour follows the focused app. */
val LocalWallpaper = staticCompositionLocalOf<ResolvedWallpaper?> { null }

/** Whether the built-in wallpapers' light should drift, which the user can turn off for a slow TV. */
val LocalWallpaperAnimated = staticCompositionLocalOf { false }

/** Off draws every glass surface as a flat tint with a thin edge, for TVs whose GPU can't spare the extra passes. */
val LocalGlassEffects = staticCompositionLocalOf { true }

/**
 * How a glass surface is built, as light on top of the backdrop slice: a white veil that is brighter at the top, a
 * diagonal sheen, a thin rim that catches light at the top left, and an optional soft shadow around the outside.
 */
data class GlassStyle(
    val backdrop: Float,
    val veilTop: Float,
    val veilBottom: Float,
    val sheen: Float,
    val rim: Float,
    val shadow: Float = 0f,
    /** Drawn under the veil; for surfaces in their own window, which have no backdrop slice to show. */
    val base: Color = Color.Transparent,
) {
    companion object {
        /** Large containers such as the shelf and the settings panel. */
        val Panel = GlassStyle(backdrop = 1f, veilTop = 0.15f, veilBottom = 0.1f, sheen = 0f, rim = 0.62f, shadow = 0.34f)

        /** Capsules and small controls at rest. */
        val Pill = GlassStyle(backdrop = 1f, veilTop = 0.2f, veilBottom = 0.09f, sheen = 0.14f, rim = 0.58f)

        /** A control that sits on a panel in its own window, where the screen's backdrop can't be lined up. */
        val Inset = GlassStyle(backdrop = 0f, veilTop = 0.17f, veilBottom = 0.07f, sheen = 0.1f, rim = 0.5f)

        /** A focused control that keeps its colour: lighter and sharper than [Pill], with white text still on top. */
        val Lit = GlassStyle(backdrop = 1f, veilTop = 0.36f, veilBottom = 0.2f, sheen = 0.32f, rim = 1f, shadow = 0.3f)

        /**
         * Menus and dialogs. They live in their own window, where the screen's backdrop can't be lined up behind
         * them, so they carry their own dark tint instead and stay readable over anything.
         */
        val Dense = GlassStyle(backdrop = 0f, veilTop = 0.2f, veilBottom = 0.07f, sheen = 0.14f, rim = 0.7f, base = Color(0xF2151A2B))

        /** A control that has focus: bright, nearly opaque glass with dark text on top. */
        val Bright = GlassStyle(backdrop = 0f, veilTop = 0.98f, veilBottom = 0.86f, sheen = 0.5f, rim = 0.9f, shadow = 0.32f)

        /** A row that is selected but not focused. */
        val Selected = GlassStyle(backdrop = 1f, veilTop = 0.16f, veilBottom = 0.1f, sheen = 0.1f, rim = 0.4f)
    }
}

fun Modifier.glass(shape: Shape, style: GlassStyle = GlassStyle.Panel): Modifier = this.then(GlassElement(shape, style))

private data class GlassElement(val shape: Shape, val style: GlassStyle) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(shape, style)

    override fun update(node: GlassNode) {
        if (node.shape != shape || node.style != style) {
            node.shape = shape
            node.style = style
            node.invalidateDraw()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glass"
    }
}

private class GlassNode(var shape: Shape, var style: GlassStyle) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var origin = Offset.Zero
    private var root = IntSize.Zero

    // Everything below depends only on the node's size and style, so it is built once rather than on every frame.
    private var built: BuiltGlass? = null
    private val slicePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
    private val sliceMatrix = android.graphics.Matrix()
    private var shaderImage: ImageBitmap? = null
    private var shader: android.graphics.BitmapShader? = null
    private var fromShaderImage: ImageBitmap? = null
    private var fromShader: android.graphics.BitmapShader? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val position = coordinates.positionInRoot()
        val rootSize = coordinates.findRootCoordinates().size
        if (position != origin || rootSize != root) {
            origin = position
            root = rootSize
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val s = style
        val glass = built?.takeIf { it.matches(size, layoutDirection, shape, s) } ?: BuiltGlass.create(this, shape, s).also { built = it }
        val outline = glass.outline
        if (!currentValueOf(LocalGlassEffects)) {
            // The cheap look: one flat tint and a thin edge, no backdrop slice, gradients or shadow.
            if (s.base != Color.Transparent) drawOutline(outline, s.base)
            drawOutline(outline, Color.White.copy(alpha = (s.veilTop + s.veilBottom) / 2f))
            drawOutline(outline, Color.White.copy(alpha = s.rim * 0.3f), style = Stroke(width = RIM_WIDTH_DP * density))
            drawContent()
            return
        }
        if (s.shadow > 0f && outline is Outline.Rounded) drawSoftShadow(outline, s.shadow)
        if (s.base != Color.Transparent) drawOutline(outline, s.base)
        if (s.backdrop > 0f) {
            // The veil rides on the slice's own pass as a colour filter: one fill instead of two over a large area.
            drawBackdropFill(glass, currentValueOf(LocalBackdrop), s.backdrop)
        } else {
            drawOutline(outline, glass.veil)
        }
        if (s.sheen > 0f) drawOutline(outline, glass.sheen)
        // A thin rim that catches light at the top left; centred on the edge, so half of it falls just outside.
        drawOutline(outline, glass.rim, style = Stroke(width = RIM_WIDTH_DP * density))
        drawContent()
    }

    /** Rings of falling strength read as a blurred shadow. Each is a stroked band outside the surface, so no clip is needed. */
    private fun DrawScope.drawSoftShadow(outline: Outline.Rounded, strength: Float) {
        val corner = outline.roundRect.topLeftCornerRadius.x
        for ((spread, fraction) in SHADOW_RINGS) {
            val grow = spread * density
            drawRoundRect(
                color = Color.Black.copy(alpha = strength * fraction),
                topLeft = Offset(-grow / 2f, -grow / 2f + SHADOW_DROP_DP * density),
                size = Size(size.width + grow, size.height + grow),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner + grow / 2f),
                style = Stroke(width = grow),
            )
        }
    }

    /**
     * Fills the surface with its slice of the backdrop through a bitmap shader mapped onto the screen, so the slice
     * takes the surface's own rounded shape without a clip.
     */
    private fun DrawScope.drawBackdropFill(glass: BuiltGlass, state: BackdropState, opacity: Float) {
        val from = state.from
        val to = state.to
        val progress = state.progress
        val fading = from != null && progress < 1f
        if (from != null && fading) {
            if (fromShaderImage !== from) {
                fromShader = from.asAndroidBitmap().let { android.graphics.BitmapShader(it, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP) }
                fromShaderImage = from
            }
            fillSlice(glass, fromShader, from, opacity * (1f - progress))
        }
        if (to != null) {
            if (shaderImage !== to) {
                shader = to.asAndroidBitmap().let { android.graphics.BitmapShader(it, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP) }
                shaderImage = to
            }
            fillSlice(glass, shader, to, opacity * if (fading) progress else 1f)
        }
    }

    private fun DrawScope.fillSlice(glass: BuiltGlass, bitmapShader: android.graphics.BitmapShader?, image: ImageBitmap, alpha: Float) {
        if (bitmapShader == null || root.width == 0 || root.height == 0) return
        // Shader space is the bitmap's pixels; the matrix carries them onto this surface's own coordinates.
        val toBitmapX = image.width.toFloat() / root.width
        val toBitmapY = image.height.toFloat() / root.height
        sliceMatrix.setScale(1f / toBitmapX, 1f / toBitmapY)
        sliceMatrix.postTranslate(-origin.x, -origin.y)
        bitmapShader.setLocalMatrix(sliceMatrix)
        slicePaint.shader = bitmapShader
        slicePaint.colorFilter = glass.veilFilter
        slicePaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val rounded = glass.outline as? Outline.Rounded
            if (rounded != null) {
                val r = rounded.roundRect
                native.drawRoundRect(0f, 0f, size.width, size.height, r.topLeftCornerRadius.x, r.topLeftCornerRadius.y, slicePaint)
            } else {
                native.drawRect(0f, 0f, size.width, size.height, slicePaint)
            }
        }
    }

    private companion object {
        const val RIM_WIDTH_DP = 1.1f
        const val SHADOW_DROP_DP = 4f
        // Many faint bands blend into one soft falloff; a few strong ones show up as dark outlines on a TV.
        val SHADOW_RINGS = listOf(4f to 0.14f, 9f to 0.14f, 15f to 0.14f, 22f to 0.14f, 30f to 0.14f)
    }
}

/** What a glass surface needs to draw itself at one size: its outline and the three light gradients. */
private class BuiltGlass(
    val size: Size,
    val layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    val shape: Shape,
    val style: GlassStyle,
    val outline: Outline,
    val veil: Brush,
    val veilFilter: android.graphics.PorterDuffColorFilter,
    val sheen: Brush,
    val rim: Brush,
) {
    fun matches(size: Size, direction: androidx.compose.ui.unit.LayoutDirection, shape: Shape, style: GlassStyle) =
        this.size == size && layoutDirection == direction && this.shape == shape && this.style == style

    companion object {
        fun create(scope: DrawScope, shape: Shape, style: GlassStyle): BuiltGlass {
            val size = scope.size
            return BuiltGlass(
                size = size,
                layoutDirection = scope.layoutDirection,
                shape = shape,
                style = style,
                outline = shape.createOutline(size, scope.layoutDirection, scope),
                veil = Brush.verticalGradient(listOf(Color.White.copy(alpha = style.veilTop), Color.White.copy(alpha = style.veilBottom)), endY = size.height),
                veilFilter = android.graphics.PorterDuffColorFilter(
                    Color.White.copy(alpha = (style.veilTop + style.veilBottom) / 2f).toArgb(),
                    android.graphics.PorterDuff.Mode.SRC_OVER,
                ),
                // Light falling from the top left across the surface, gone by the middle.
                sheen = Brush.linearGradient(
                    0f to Color.White.copy(alpha = style.sheen),
                    0.5f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(size.width * 0.8f, size.height * 1.6f),
                ),
                rim = Brush.linearGradient(
                    0f to Color.White.copy(alpha = style.rim),
                    0.45f to Color.White.copy(alpha = style.rim * 0.1f),
                    1f to Color.White.copy(alpha = style.rim * 0.5f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
        }
    }
}

/**
 * Draws the part of the backdrop that lies behind this node, scaled to fill it. [origin] is the node's position on
 * screen and [root] the screen size, both in pixels; the backdrop bitmap is far smaller than the screen.
 */
internal fun DrawScope.drawBackdrop(state: BackdropState, origin: Offset, root: IntSize, opacity: Float) {
    val from = state.from
    val to = state.to
    val progress = state.progress
    val fading = from != null && progress < 1f
    if (from != null && fading) drawBackdropSlice(from, origin, root, opacity * (1f - progress))
    if (to != null) drawBackdropSlice(to, origin, root, opacity * if (fading) progress else 1f)
}

private fun DrawScope.drawBackdropSlice(image: ImageBitmap, origin: Offset, root: IntSize, alpha: Float) {
    if (root.width == 0 || root.height == 0) return
    val scaleX = image.width.toFloat() / root.width
    val scaleY = image.height.toFloat() / root.height
    // The node's rectangle in the bitmap's pixels, trimmed to the bitmap when it hangs off the screen.
    val src = Rect(origin.x * scaleX, origin.y * scaleY, (origin.x + size.width) * scaleX, (origin.y + size.height) * scaleY)
    val visible = src.intersect(Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()))
    if (visible.width < 1f || visible.height < 1f) return
    val toDstX = size.width / src.width
    val toDstY = size.height / src.height
    drawImage(
        image = image,
        srcOffset = IntOffset(visible.left.toInt(), visible.top.toInt()),
        srcSize = IntSize(visible.width.toInt().coerceAtLeast(1), visible.height.toInt().coerceAtLeast(1)),
        dstOffset = IntOffset(((visible.left - src.left) * toDstX).toInt(), ((visible.top - src.top) * toDstY).toInt()),
        dstSize = IntSize((visible.width * toDstX).toInt().coerceAtLeast(1), (visible.height * toDstY).toInt().coerceAtLeast(1)),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

private fun Outline.toPath(): Path = Path().apply {
    when (this@toPath) {
        is Outline.Rectangle -> addRect(rect)
        is Outline.Rounded -> addRoundRect(roundRect)
        is Outline.Generic -> addPath(path)
    }
}

/** A highlight that sweeps across the focused item; the alpha is read while drawing so it never recomposes. */
@Composable
fun rememberFocusSheen(focused: Boolean): State<Float> =
    animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (LocalReduceMotion.current) snap() else tween(220),
        label = "focusSheen",
    )

fun Modifier.focusSheen(alpha: State<Float>, shape: Shape): Modifier = this.then(FocusSheenElement(alpha, shape))

private class FocusSheenElement(val alpha: State<Float>, val shape: Shape) : ModifierNodeElement<FocusSheenNode>() {
    override fun create() = FocusSheenNode(alpha, shape)

    override fun update(node: FocusSheenNode) {
        node.alpha = alpha
        node.shape = shape
    }

    override fun equals(other: Any?) = other is FocusSheenElement && other.alpha === alpha && other.shape == shape

    override fun hashCode() = 31 * System.identityHashCode(alpha) + shape.hashCode()

    override fun InspectorInfo.inspectableProperties() {
        name = "focusSheen"
    }
}

private class FocusSheenNode(var alpha: State<Float>, var shape: Shape) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    override fun ContentDrawScope.draw() {
        drawContent()
        val strength = alpha.value
        if (strength <= 0.01f) return
        val path = shape.createOutline(size, layoutDirection, this).toPath()
        val full = currentValueOf(LocalGlassEffects)
        clipPath(path) {
            // The sheen is skipped with glass effects off, but the rim stays: focus must always be easy to see.
            if (full) {
                drawRect(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.42f * strength),
                        0.42f to Color.White.copy(alpha = 0.1f * strength),
                        0.7f to Color.Transparent,
                        start = Offset.Zero,
                        end = Offset(size.width * 0.7f, size.height * 1.5f),
                    ),
                )
            }
            drawPath(
                path,
                if (full) {
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.95f * strength),
                        0.5f to Color.White.copy(alpha = 0.25f * strength),
                        1f to Color.White.copy(alpha = 0.6f * strength),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                } else {
                    SolidColor(Color.White.copy(alpha = strength))
                },
                style = Stroke(width = 3f * density),
            )
        }
    }
}

/**
 * Fades the bottom [height] of a node into the screen's backdrop by drawing the backdrop over it in steps of rising
 * strength. An alpha mask would do the same but needs an extra full-size layer, which costs real memory on this TV.
 */
fun Modifier.fadeIntoBackdrop(height: Dp): Modifier = this.then(BackdropFadeElement(height))

private data class BackdropFadeElement(val height: Dp) : ModifierNodeElement<BackdropFadeNode>() {
    override fun create() = BackdropFadeNode(height)

    override fun update(node: BackdropFadeNode) {
        if (node.height != height) {
            node.height = height
            node.invalidateDraw()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "fadeIntoBackdrop"
    }
}

private class BackdropFadeNode(var height: Dp) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var origin = Offset.Zero
    private var root = IntSize.Zero

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val position = coordinates.positionInRoot()
        val rootSize = coordinates.findRootCoordinates().size
        if (position != origin || rootSize != root) {
            origin = position
            root = rootSize
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val fade = height.toPx().coerceIn(1f, size.height)
        val top = size.height - fade
        val state = currentValueOf(LocalBackdrop)
        for (step in 0 until FADE_STEPS) {
            val t = (step + 1f) / FADE_STEPS
            val strength = t * t * (3f - 2f * t)
            val y0 = top + fade * step / FADE_STEPS
            val y1 = if (step == FADE_STEPS - 1) size.height else top + fade * (step + 1) / FADE_STEPS + 1f
            clipRect(0f, y0, size.width, y1) { drawBackdrop(state, origin, root, strength) }
        }
    }

    private companion object {
        const val FADE_STEPS = 18
    }
}
