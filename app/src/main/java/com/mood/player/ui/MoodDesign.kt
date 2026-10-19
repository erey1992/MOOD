package com.mood.player.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mood.player.data.LibraryGrouping

// ---------------------------------------------------------------------------------------------
// Paleta de orbes (en el orden de los mockups: amarillo, azul, rosa, morado, violeta, rojo, verde)
// ---------------------------------------------------------------------------------------------

val OrbPalette = listOf(
    Color(0xFFF5B321),
    Color(0xFF3B82F6),
    Color(0xFFEC4899),
    Color(0xFF8B5CF6),
    Color(0xFFA855F7),
    Color(0xFFEF4444),
    Color(0xFF10B981),
)

fun orbColor(index: Int): Color = OrbPalette[Math.floorMod(index, OrbPalette.size)]

/** Icono y color de un género musical; los desconocidos usan una nota con color estable por nombre. */
fun genreStyle(name: String): Pair<ImageVector, Color> {
    val n = name.lowercase()
    return when {
        name == LibraryGrouping.UNKNOWN_GENRE -> Icons.Rounded.Category to OrbPalette[1]
        listOf("jazz", "blues", "soul", "swing").any { it in n } -> Icons.Rounded.Piano to OrbPalette[0]
        listOf("rock", "metal", "punk", "grunge").any { it in n } -> Icons.Rounded.Bolt to OrbPalette[5]
        listOf("electr", "dance", "house", "techno", "edm", "trance").any { it in n } -> Icons.Rounded.GraphicEq to OrbPalette[4]
        listOf("hip", "rap", "trap", "r&b", "rnb").any { it in n } -> Icons.Rounded.Headphones to OrbPalette[3]
        listOf("latin", "reggae", "salsa", "bachata", "cumbia", "merengue", "flamenco").any { it in n } ->
            Icons.Rounded.Celebration to Color(0xFFFB8C00)
        listOf("classic", "clásic", "clasic", "orquest", "soundtrack", "banda sonora").any { it in n } ->
            Icons.Rounded.LibraryMusic to OrbPalette[1]
        "pop" in n -> Icons.Rounded.Mic to OrbPalette[2]
        else -> Icons.Rounded.MusicNote to orbColor(n.hashCode())
    }
}

// ---------------------------------------------------------------------------------------------
// Fondo lavanda con destellos (bokeh)
// ---------------------------------------------------------------------------------------------

private class Bokeh(val x: Float, val y: Float, val radius: Float, val color: Int)

private val BOKEH = listOf(
    Bokeh(0.10f, 0.06f, 0.38f, 0), Bokeh(0.92f, 0.16f, 0.42f, 1), Bokeh(0.35f, 0.40f, 0.30f, 2),
    Bokeh(0.85f, 0.55f, 0.45f, 0), Bokeh(0.08f, 0.72f, 0.40f, 1), Bokeh(0.62f, 0.92f, 0.42f, 3),
    Bokeh(0.50f, 0.20f, 0.22f, 3),
)

/** Pequeños destellos: (x, y, radio en dp). */
private val SPARKLES = listOf(
    Triple(0.18f, 0.12f, 3f), Triple(0.72f, 0.07f, 2f), Triple(0.88f, 0.31f, 4f), Triple(0.27f, 0.27f, 2f),
    Triple(0.55f, 0.36f, 3f), Triple(0.12f, 0.47f, 2.5f), Triple(0.78f, 0.49f, 2f), Triple(0.40f, 0.63f, 3.5f),
    Triple(0.93f, 0.70f, 2.5f), Triple(0.22f, 0.86f, 3f), Triple(0.66f, 0.80f, 2f), Triple(0.49f, 0.95f, 3f),
)

@Composable
fun MoodBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = LocalMoodColors.current
    Box(modifier.fillMaxSize()) {
        // El fondo va en su propia capa: se pinta una vez y se reutiliza aunque el contenido
        // cambie en cada fotograma (barra de progreso, karaoke…).
        Spacer(
            Modifier
                .matchParentSize()
                .graphicsLayer { }
                .drawBehind {
                drawRect(Brush.verticalGradient(listOf(colors.backgroundTop, colors.backgroundBottom)))
                BOKEH.forEach { b ->
                    val color = colors.bokeh[b.color % colors.bokeh.size]
                    val center = Offset(size.width * b.x, size.height * b.y)
                    val radius = size.minDimension * b.radius
                    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
                }
                // En modo oscuro los destellos se atenúan para que no se vean como manchas grises bajo el cristal.
                val sparkleAlpha = if (colors == DarkMoodColors) 0.22f else 0.7f
                SPARKLES.forEach { (x, y, r) ->
                    val center = Offset(size.width * x, size.height * y)
                    val radius = r.dp.toPx() * 3
                    drawCircle(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = sparkleAlpha), Color.White.copy(alpha = 0f)), center, radius),
                        radius,
                        center,
                    )
                }
            },
        )
        Box(Modifier.matchParentSize(), content = content)
    }
}

// ---------------------------------------------------------------------------------------------
// Cristal
// ---------------------------------------------------------------------------------------------

/** Tarjeta translúcida con borde claro. [tint] le da un velo de color (p. ej. en los MOODS). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color? = null,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalMoodColors.current
    val base = if (strong) colors.glassStrong else colors.glass
    Box(
        modifier
            .clip(shape)
            .background(base)
            .then(
                if (tint != null) {
                    Modifier.background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.20f), tint.copy(alpha = 0.07f))))
                } else Modifier
            )
            .border(1.dp, tint?.copy(alpha = 0.30f) ?: colors.glassBorder, shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .then(if (interactionSource != null) Modifier.hoverable(interactionSource) else Modifier)
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                } else Modifier
            ),
        content = content,
    )
}

/** Botón redondo de cristal (barra superior). */
@Composable
fun RoundGlassButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, size: Dp = 48.dp) {
    val colors = LocalMoodColors.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.glass)
            .border(1.dp, colors.glassBorder, CircleShape)
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = colors.textPrimary, modifier = Modifier.size(size * 0.5f))
    }
}

/** Botón con forma de píldora: relleno con degradado morado ([primary]) o de cristal. */
@Composable
fun PillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val colors = LocalMoodColors.current
    val shape = RoundedCornerShape(50)
    val content = if (primary) Color.White else colors.textPrimary
    Row(
        modifier
            .height(52.dp)
            .clip(shape)
            .then(
                if (primary) Modifier.background(Brush.horizontalGradient(listOf(colors.accentLight, colors.accentDeep)))
                else Modifier.background(colors.glass).border(1.dp, colors.glassBorder, shape)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content)
        Spacer(Modifier.width(8.dp))
        Text(text, color = content, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------------------------
// Orbes
// ---------------------------------------------------------------------------------------------

/** Destellos dentro del orbe: (dx, dy, radio) relativos al radio. */
private val ORB_SPARKS = listOf(
    Triple(0.35f, -0.10f, 0.035f), Triple(-0.20f, 0.42f, 0.03f), Triple(0.48f, 0.35f, 0.025f),
    Triple(-0.45f, 0.05f, 0.02f), Triple(0.10f, 0.55f, 0.02f), Triple(0.22f, -0.48f, 0.02f),
)

fun DrawScope.drawOrb(color: Color, glow: Boolean = true) {
    val r = size.minDimension / 2
    val c = center
    if (glow) {
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0f)), c, r * 1.3f), r * 1.3f, c)
    }
    val light = lerp(color, Color.White, 0.55f)
    val dark = lerp(color, Color.Black, 0.22f)
    drawCircle(
        Brush.radialGradient(
            0f to light, 0.5f to color, 1f to dark,
            center = c + Offset(-r * 0.25f, -r * 0.3f),
            radius = r * 1.35f,
        ),
        r,
        c,
    )
    ORB_SPARKS.forEach { (dx, dy, s) -> drawCircle(Color.White.copy(alpha = 0.6f), r * s, c + Offset(dx * r, dy * r)) }
    drawCircle(
        Brush.sweepGradient(
            listOf(
                Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.5f),
                Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.75f),
            ),
            c,
        ),
        r - 1.dp.toPx(),
        c,
        style = Stroke(1.5.dp.toPx()),
    )
    drawOval(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0f)),
            center = c + Offset(-r * 0.32f, -r * 0.52f),
            radius = r * 0.5f,
        ),
        topLeft = c + Offset(-r * 0.72f, -r * 0.82f),
        size = Size(r * 0.8f, r * 0.55f),
    )
}

/** Burbuja brillante del color indicado con un icono blanco dentro. */
@Composable
fun OrbIcon(
    color: Color,
    icon: ImageVector?,
    size: Dp,
    modifier: Modifier = Modifier,
    iconScale: Float = 0.46f,
    glow: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .drawBehind { drawOrb(color, glow) },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * iconScale))
        }
    }
}

/** Carátula real (esquinas redondeadas) o, si no hay, un orbe con una nota. */
@Composable
fun ArtOrOrb(
    image: ImageBitmap?,
    fallbackColor: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
) {
    if (image != null) {
        Image(
            image,
            contentDescription = "Carátula",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .background(Color.White),
        )
    } else {
        OrbIcon(fallbackColor, Icons.Rounded.MusicNote, size, modifier)
    }
}


/** Ilustración para canciones sin carátula: fondo lavanda con burbujas y un orbe dorado con una nota. */
@Composable
fun ArtworkPlaceholder(modifier: Modifier = Modifier) {
    val colors = LocalMoodColors.current
    BoxWithConstraints(
        modifier.drawBehind {
            drawRect(Brush.verticalGradient(listOf(colors.artworkTop, colors.artworkBottom)))
            val w = size.width
            val h = size.height
            listOf(
                Triple(Offset(w * 0.12f, h * 0.30f), w * 0.16f, OrbPalette[1]),
                Triple(Offset(w * 0.82f, h * 0.20f), w * 0.12f, OrbPalette[2]),
                Triple(Offset(w * 0.96f, h * 0.45f), w * 0.11f, OrbPalette[6]),
            ).forEach { (c, r, color) ->
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0f)), c, r), r, c)
            }
            SPARKLES.forEach { (x, y, r) ->
                val c = Offset(w * x, h * y)
                val radius = r.dp.toPx() * 4
                drawCircle(Brush.radialGradient(listOf(Color(0xCCFFE9A8), Color(0x00FFE9A8)), c, radius), radius, c)
            }
            drawOval(
                Brush.radialGradient(listOf(Color(0x99FFD86B), Color(0x00FFD86B)), Offset(w / 2, h * 0.86f), w * 0.45f),
                topLeft = Offset(w * 0.08f, h * 0.76f),
                size = Size(w * 0.84f, h * 0.2f),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        OrbIcon(Color(0xFFF7C948), Icons.Rounded.MusicNote, maxWidth * 0.6f, iconScale = 0.42f)
    }
}

// ---------------------------------------------------------------------------------------------
// Progreso y reproducción
// ---------------------------------------------------------------------------------------------

/**
 * Barra de progreso con degradado amarillo → morado y un pulgar morado con brillo.
 * Si [onValueChange] es null solo muestra el progreso; si no, se puede tocar y arrastrar.
 */
@Composable
fun GradientSeekBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    thumbSize: Dp = 22.dp,
    trackHeight: Dp = 6.dp,
    onValueChange: ((Float) -> Unit)? = null,
    onValueChangeFinished: () -> Unit = {},
) {
    val colors = LocalMoodColors.current
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val value = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .height(thumbSize + 8.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                if (onValueChange != null) {
                    setProgress { target ->
                        change?.invoke(target.coerceIn(0f, 1f))
                        finished()
                        true
                    }
                }
            }
            .then(
                if (onValueChange == null) Modifier else Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            change?.invoke((offset.x / size.width).coerceIn(0f, 1f))
                            finished()
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset -> change?.invoke((offset.x / size.width).coerceIn(0f, 1f)) },
                            onDragEnd = { finished() },
                            onDragCancel = { finished() },
                        ) { input, _ -> change?.invoke((input.position.x / size.width).coerceIn(0f, 1f)) }
                    }
            )
            .drawBehind {
                val thumbR = thumbSize.toPx() / 2
                val trackH = trackHeight.toPx()
                val left = thumbR
                val right = size.width - thumbR
                val y = size.height / 2
                val x = left + (right - left) * value
                val radius = CornerRadius(trackH / 2)
                drawRoundRect(colors.track, Offset(0f, y - trackH / 2), Size(size.width, trackH), radius)
                drawRoundRect(
                    Brush.horizontalGradient(listOf(colors.progressStart, colors.progressEnd), startX = 0f, endX = size.width),
                    Offset(0f, y - trackH / 2),
                    Size(x, trackH),
                    radius,
                )
                drawCircle(
                    Brush.radialGradient(listOf(colors.accent.copy(alpha = 0.45f), colors.accent.copy(alpha = 0f)), Offset(x, y), thumbR * 1.8f),
                    thumbR * 1.8f,
                    Offset(x, y),
                )
                drawCircle(Color.White, thumbR, Offset(x, y))
                drawCircle(
                    Brush.radialGradient(listOf(colors.accentLight, colors.accent), Offset(x - thumbR * 0.3f, y - thumbR * 0.3f), thumbR),
                    thumbR - 3.dp.toPx(),
                    Offset(x, y),
                )
            },
    )
}

/** Botón grande de play/pausa: círculo morado con degradado y halo. */
@Composable
fun PlayCircleButton(isPlaying: Boolean, onClick: () -> Unit, size: Dp = 84.dp) {
    val colors = LocalMoodColors.current
    Box(
        Modifier
            .size(size)
            .drawBehind {
                val r = this.size.minDimension / 2
                drawCircle(
                    Brush.radialGradient(listOf(colors.accent.copy(alpha = 0.4f), colors.accent.copy(alpha = 0f)), center, r * 1.45f),
                    r * 1.45f,
                )
            }
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(colors.accentLight, colors.accentDeep)))
            .clickable(onClickLabel = if (isPlaying) "Pausar" else "Reproducir", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
            tint = Color.White,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Fila de acciones de una barra superior, con separación uniforme. */
@Composable
fun TopActions(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}
