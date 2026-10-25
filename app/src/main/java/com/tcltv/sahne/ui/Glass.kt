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
    /** How strongly the edge bends the backdrop behind it, 0 to 1; only surfaces that show the backdrop can bend it. */
    val refraction: Float = 0f,
) {
    companion object {
        /** Large containers such as the shelf and the settings panel. */
        val Panel = GlassStyle(backdrop = 1f, veilTop = 0.15f, veilBottom = 0.1f, sheen = 0f, rim = 0.62f, shadow = 0.34f, refraction = 1f)

        /** Capsules and small controls at rest. */
        val Pill = GlassStyle(backdrop = 1f, veilTop = 0.2f, veilBottom = 0.09f, sheen = 0.14f, rim = 0.58f, refraction = 0.8f)

        /** A control that sits on a panel in its own window, where the screen's backdrop can't be lined up. */
        val Inset = GlassStyle(backdrop = 0f, veilTop = 0.17f, veilBottom = 0.07f, sheen = 0.1f, rim = 0.5f)

        /** A focused control that keeps its colour: lighter and sharper than [Pill], with white text still on top. */
        val Lit = GlassStyle(backdrop = 1f, veilTop = 0.36f, veilBottom = 0.2f, sheen = 0.32f, rim = 1f, shadow = 0.3f, refraction = 1f)

        /**
         * Menus and dialogs. They live in their own window, where the screen's backdrop can't be lined up behind
         * them, so they carry their own dark tint instead and stay readable over anything.
         */
        val Dense = GlassStyle(backdrop = 0f, veilTop = 0.2f, veilBottom = 0.07f, sheen = 0.14f, rim = 0.7f, base = Color(0xF2151A2B))

        /** A control that has focus: bright, nearly opaque glass with dark text on top. */
        val Bright = GlassStyle(backdrop = 0f, veilTop = 0.98f, veilBottom = 0.86f, sheen = 0.5f, rim = 0.9f, shadow = 0.32f)

        /** A row that is selected but not focused. */
        val Selected = GlassStyle(backdrop = 1f, veilTop = 0.16f, veilBottom = 0.1f, sheen = 0.1f, rim = 0.4f, refraction = 0.6f)
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
    private val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var shadowBlur = 0f
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
            // Over a bright or busy wallpaper the glass darkens, so the text on it stays readable.
            val dim = currentValueOf(LocalWallpaper)?.glassDim ?: 0f
            if (dim > 0.01f) drawOutline(outline, Color.Black.copy(alpha = dim * s.backdrop))
        } else {
            drawOutline(outline, glass.veil)
        }
        if (s.sheen > 0f) drawOutline(outline, glass.sheen)
        // A thin rim that catches light at the top left; centred on the edge, so half of it falls just outside.
        drawOutline(outline, glass.rim, style = Stroke(width = RIM_WIDTH_DP * density))
        drawContent()
    }

    /**
     * A blurred rounded rectangle under the surface. Skia draws a rounded-rectangle blur analytically on the GPU, so it
     * is smooth at any size; stacked stroked bands, the cheaper stand-in, showed up as steps over photos.
     */
    private fun DrawScope.drawSoftShadow(outline: Outline.Rounded, strength: Float) {
        // Before Android 9 the GPU renderer can't draw mask filters; a glass edge without a shadow still reads well.
        if (android.os.Build.VERSION.SDK_INT < 28) return
        val radius = SHADOW_BLUR_DP * density
        if (shadowPaint.maskFilter == null || shadowBlur != radius) {
            shadowPaint.maskFilter = android.graphics.BlurMaskFilter(radius, android.graphics.BlurMaskFilter.Blur.NORMAL)
            shadowBlur = radius
        }
        shadowPaint.color = Color.Black.copy(alpha = (strength * SHADOW_ALPHA).coerceIn(0f, 1f)).toArgb()
        val corner = outline.roundRect.topLeftCornerRadius.x
        val drop = SHADOW_DROP_DP * density
        drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, drop, size.width, size.height + drop, corner, corner, shadowPaint) }
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
            // The bent edge is drawn over the plain fill with the same paint, so the two meet without a seam.
            glass.ring?.let { ring ->
                native.drawVertices(
                    android.graphics.Canvas.VertexMode.TRIANGLES,
                    ring.positions.size, ring.positions, 0, ring.textures, 0,
                    null, 0, ring.indices, 0, ring.indices.size, slicePaint,
                )
            }
        }
    }

    private companion object {
        const val RIM_WIDTH_DP = 1.1f
        const val SHADOW_DROP_DP = 6f
        const val SHADOW_BLUR_DP = 16f
        const val SHADOW_ALPHA = 0.7f
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
    val ring: RefractionRing?,
) {
    fun matches(size: Size, direction: androidx.compose.ui.unit.LayoutDirection, shape: Shape, style: GlassStyle) =
        this.size == size && layoutDirection == direction && this.shape == shape && this.style == style

    companion object {
        fun create(scope: DrawScope, shape: Shape, style: GlassStyle): BuiltGlass {
            val size = scope.size
            val outline = shape.createOutline(size, scope.layoutDirection, scope)
            return BuiltGlass(
                size = size,
                layoutDirection = scope.layoutDirection,
                shape = shape,
                style = style,
                outline = outline,
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
                ring = (outline as? Outline.Rounded)
                    ?.takeIf { style.refraction > 0f && style.backdrop > 0f && android.os.Build.VERSION.SDK_INT >= 29 }
                    ?.let { RefractionRing.create(size, it.roundRect.topLeftCornerRadius.x, style.refraction, scope.density) },
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

/**
 * The band along a glass edge where the backdrop bends, as in Apple's Liquid Glass. It follows the refraction model of
 * Kyant's AndroidLiquidGlass (Apache-2.0): a point at depth s inside the edge shows the backdrop pushed outward along
 * the edge normal by circleMap(1 - s / height) * amount. That library evaluates it per pixel in an AGSL shader, which
 * needs Android 13; here the same displacement is baked into a textured triangle mesh, which the GPU draws from
 * Android 10 on.
 */
internal class RefractionRing(val positions: FloatArray, val textures: FloatArray, val indices: ShortArray) {

    companion object {
        private const val HEIGHT_DP = 20f
        private const val AMOUNT_DP = 26f
        private const val LEVELS = 7
        private const val ARC_STEPS = 10

        fun create(size: Size, cornerRadius: Float, strength: Float, density: Float): RefractionRing? {
            val w = size.width
            val h = size.height
            val radius = cornerRadius.coerceIn(0f, minOf(w, h) / 2f)
            // Deeper than the corner radius, the inset would fold the corners over themselves.
            val band = minOf(HEIGHT_DP * density, minOf(w, h) / 2f, radius)
            if (band < 2f) return null
            val amount = AMOUNT_DP * density * strength

            // The outline as points with outward normals: each corner's arc, then straight on to the next corner.
            val outline = ArrayList<FloatArray>()
            val corners = listOf(
                floatArrayOf(w - radius, radius, -90f),
                floatArrayOf(w - radius, h - radius, 0f),
                floatArrayOf(radius, h - radius, 90f),
                floatArrayOf(radius, radius, 180f),
            )
            for (corner in corners) {
                for (step in 0..ARC_STEPS) {
                    val angle = Math.toRadians((corner[2] + 90f * step / ARC_STEPS).toDouble())
                    val nx = kotlin.math.cos(angle).toFloat()
                    val ny = kotlin.math.sin(angle).toFloat()
                    outline += floatArrayOf(corner[0] + nx * radius, corner[1] + ny * radius, nx, ny)
                }
            }
            val count = outline.size
            val positions = FloatArray(count * (LEVELS + 1) * 2)
            val textures = FloatArray(positions.size)
            for (level in 0..LEVELS) {
                // Levels crowd toward the edge, where the bend changes fastest.
                val t = level.toFloat() / LEVELS
                val depth = band * t * t + if (level == 0) 0.5f else 0f
                val x = 1f - depth / band
                val bend = (1f - kotlin.math.sqrt((1f - x * x).coerceAtLeast(0f))) * amount
                for (i in 0 until count) {
                    val p = outline[i]
                    val px = p[0] - p[2] * depth
                    val py = p[1] - p[3] * depth
                    val at = (level * count + i) * 2
                    positions[at] = px
                    positions[at + 1] = py
                    textures[at] = px + p[2] * bend
                    textures[at + 1] = py + p[3] * bend
                }
            }
            val indices = ShortArray(LEVELS * count * 6)
            var n = 0
            for (level in 0 until LEVELS) {
                for (i in 0 until count) {
                    val a = level * count + i
                    val b = level * count + (i + 1) % count
                    val c = a + count
                    val d = b + count
                    indices[n++] = a.toShort(); indices[n++] = b.toShort(); indices[n++] = c.toShort()
                    indices[n++] = b.toShort(); indices[n++] = d.toShort(); indices[n++] = c.toShort()
                }
            }
            return RefractionRing(positions, textures, indices)
        }
    }
}
