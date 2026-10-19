package com.mood.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mood.player.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LOGO_LETTERS = listOf(R.drawable.logo_m, R.drawable.logo_o1, R.drawable.logo_o2, R.drawable.logo_d)

/** Índice de la "O" que lleva el botón de play: gira como un disco. */
private const val PLAY_LETTER = 2

/**
 * Intro animada al abrir la app: las letras de MOOD caen una a una con rebote, la O del play gira,
 * aparece un halo de colores y todo se desvanece. Un toque la salta.
 */
@Composable
fun LogoIntro(onFinished: () -> Unit) {
    val finish by rememberUpdatedState(onFinished)
    val letters = remember { LOGO_LETTERS.map { Animatable(0f) } }
    val spin = remember { Animatable(0f) }
    val halo = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }
    val exit = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        letters.forEachIndexed { i, anim ->
            launch {
                delay(130L * i)
                anim.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessLow))
            }
        }
        delay(130L * (letters.size - 1) + 380)
        launch { spin.animateTo(360f, tween(750, easing = FastOutSlowInEasing)) }
        launch { halo.animateTo(1f, tween(600)) }
        pulse.animateTo(1.07f, tween(380, easing = FastOutSlowInEasing))
        pulse.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        delay(250)
        exit.animateTo(0f, tween(380))
        finish()
    }

    MoodBackground(
        Modifier
            .graphicsLayer { alpha = exit.value }
            .semantics { contentDescription = "MOOD" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() }
    ) {
        // Halo arcoíris detrás del logo (los colores del icono).
        Box(
            Modifier
                .align(Alignment.Center)
                .size(340.dp)
                .graphicsLayer {
                    alpha = halo.value * 0.75f
                    scaleX = 0.6f + 0.4f * halo.value
                    scaleY = scaleX
                }
                .drawBehind {
                    val r = size.minDimension / 2
                    listOf(
                        Color(0x66FFC93C) to -0.35f, Color(0x66EC4899) to -0.1f,
                        Color(0x663B82F6) to 0.15f, Color(0x66A855F7) to 0.35f,
                    ).forEach { (color, dx) ->
                        val c = center.copy(x = center.x + dx * r)
                        drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), c, r * 0.75f), r * 0.75f, c)
                    }
                },
        )
        Row(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LOGO_LETTERS.forEachIndexed { i, res ->
                val painter = painterResource(res)
                val ratio = painter.intrinsicSize.width / painter.intrinsicSize.height
                Image(
                    painter,
                    contentDescription = null,
                    modifier = Modifier
                        .height(64.dp)
                        .aspectRatio(ratio)
                        .graphicsLayer {
                            val p = letters[i].value
                            alpha = p.coerceIn(0f, 1f)
                            scaleX = 0.3f + 0.7f * p
                            scaleY = scaleX
                            translationY = (1f - p) * -90.dp.toPx()
                            if (i == PLAY_LETTER) rotationZ = spin.value
                        },
                )
            }
        }
    }
}

/** Logo estático (para la pantalla de permisos). */
@Composable
fun LogoStatic(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LOGO_LETTERS.forEach { res ->
            val painter = painterResource(res)
            Image(
                painter,
                contentDescription = null,
                modifier = Modifier
                    .height(52.dp)
                    .aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height),
            )
        }
    }
}
