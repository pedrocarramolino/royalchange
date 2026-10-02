package com.royalchance.core.designsystem.orientation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.royalchance.core.designsystem.component.RoyalSecondaryButton
import com.royalchance.core.designsystem.motion.LocalReducedMotion
import com.royalchance.core.designsystem.resources.Res
import com.royalchance.core.designsystem.resources.ds_rotate_back
import com.royalchance.core.designsystem.resources.ds_rotate_to_landscape_text
import com.royalchance.core.designsystem.resources.ds_rotate_to_landscape_title
import com.royalchance.core.designsystem.resources.ds_rotate_to_portrait_text
import com.royalchance.core.designsystem.resources.ds_rotate_to_portrait_title
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

enum class ScreenOrientation { Portrait, Landscape }

/**
 * Pantallas que piden horizontal (las mesas). Mientras haya alguna, la app va en horizontal; si
 * no, en vertical. Cada petición guarda cómo salir de su pantalla, para el aviso de girar.
 */
@Stable
class OrientationRequests {
    private var requests by mutableStateOf(listOf<Pair<Any, () -> Unit>>())

    val required: ScreenOrientation get() = if (requests.isEmpty()) ScreenOrientation.Portrait else ScreenOrientation.Landscape

    internal val backAction: (() -> Unit)? get() = requests.lastOrNull()?.second

    internal fun add(token: Any, onBack: () -> Unit) {
        requests = requests + (token to onBack)
    }

    internal fun remove(token: Any) {
        requests = requests.filterNot { it.first === token }
    }
}

val LocalOrientationRequests = staticCompositionLocalOf { OrientationRequests() }

/** La pantalla que la llama se juega en horizontal. [onBack] permite salir sin girar el móvil. */
@Composable
fun RequireLandscape(onBack: () -> Unit) {
    val requests = LocalOrientationRequests.current
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(requests) {
        val token = Any()
        requests.add(token) { currentOnBack() }
        onDispose { requests.remove(token) }
    }
}

/**
 * Orientación de la app: vertical, salvo en las mesas, que van en horizontal.
 *
 * - Donde se puede fijar (Android; la web solo en algunos navegadores y a pantalla completa), se fija.
 * - En un móvil en la orientación equivocada (iPhone, o si no se pudo fijar) se muestra encima un
 *   aviso para girarlo; en una mesa, con un botón para volver al casino (por si el giro automático
 *   está bloqueado). Tablets y escritorio usan cualquier orientación.
 */
@Composable
fun OrientationGate(content: @Composable () -> Unit) {
    val requests = remember { OrientationRequests() }
    val lock = rememberOrientationLock()
    val required = requests.required
    LaunchedEffect(required) { lock(required) }

    CompositionLocalProvider(LocalOrientationRequests provides requests) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            content()
            val isPhone = min(maxWidth.value, maxHeight.value) < PHONE_MAX_SHORT_SIDE_DP
            val current = if (maxWidth > maxHeight) ScreenOrientation.Landscape else ScreenOrientation.Portrait
            if (isPhone && current != required) {
                RotatePrompt(required, onBack = requests.backAction)
            }
        }
    }
}

@Composable
private fun RotatePrompt(required: ScreenOrientation, onBack: (() -> Unit)?) {
    val casino = RoyalTheme.casinoColors
    val landscape = required == ScreenOrientation.Landscape
    Surface(
        color = MaterialTheme.colorScheme.background,
        // Tapa la pantalla de debajo también para los toques.
        modifier = Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RoyalSpacing.l, Alignment.CenterVertically),
            modifier = Modifier.fillMaxSize().padding(RoyalSpacing.xl).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            RotatingPhone(toLandscape = landscape)
            Text(
                stringResource(if (landscape) Res.string.ds_rotate_to_landscape_title else Res.string.ds_rotate_to_portrait_title),
                style = MaterialTheme.typography.headlineSmall,
                color = casino.gold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(if (landscape) Res.string.ds_rotate_to_landscape_text else Res.string.ds_rotate_to_portrait_text),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (landscape && onBack != null) {
                RoyalSecondaryButton(text = stringResource(Res.string.ds_rotate_back), onClick = onBack)
            }
        }
    }
}

/** Silueta de un móvil que gira hacia la orientación pedida. */
@Composable
private fun RotatingPhone(toLandscape: Boolean) {
    val casino = RoyalTheme.casinoColors
    val angle = if (LocalReducedMotion.current) {
        if (toLandscape) 90f else 0f
    } else {
        val transition = rememberInfiniteTransition()
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_600), RepeatMode.Reverse),
        )
        if (toLandscape) 90f * progress else 90f * (1f - progress)
    }
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(3.dp, casino.gold),
            modifier = Modifier.size(width = 44.dp, height = 76.dp).rotate(angle),
        ) {}
    }
}

/** Fija la orientación donde la plataforma lo permite; si no, no hace nada. */
@Composable
expect fun rememberOrientationLock(): (ScreenOrientation) -> Unit

private const val PHONE_MAX_SHORT_SIDE_DP = 600f
