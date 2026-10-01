package com.royalchance.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.core.ui.resources.Res
import com.royalchance.core.ui.resources.event_achievement_hint
import com.royalchance.core.ui.resources.event_achievement_title
import com.royalchance.core.ui.resources.event_level_up_title
import com.royalchance.domain.progression.Achievements
import com.royalchance.domain.progression.Levels
import com.royalchance.domain.progression.ProgressEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Avisos de progreso (subida de nivel, logro desbloqueado), uno tras otro, sobre cualquier pantalla.
 * No bloquean: desaparecen solos y los lectores de pantalla los anuncian.
 */
@Composable
fun ProgressEventHost(events: Flow<ProgressEvent>, modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<ProgressEvent?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(events) {
        events.collect { event ->
            current = event
            visible = true
            delay(VISIBLE_TIME)
            visible = false
            delay(HIDE_ANIMATION)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        current?.let { ProgressEventCard(it) }
    }
}

@Composable
private fun ProgressEventCard(event: ProgressEvent) {
    val casino = RoyalTheme.casinoColors
    val (title, subtitle) = when (event) {
        is ProgressEvent.LevelUp -> stringResource(Res.string.event_level_up_title, event.level) to
            titleName(Levels.titleFor(event.level))
        is ProgressEvent.AchievementUnlocked -> stringResource(Res.string.event_achievement_title, achievementTitle(event.id)) to
            stringResource(Res.string.event_achievement_hint, chipsText(Achievements.of(event.id).reward))
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
        modifier = Modifier
            .widthIn(max = 480.dp)
            .padding(RoyalSpacing.l)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(RoyalSpacing.l)) {
            Icon(
                imageVector = if (event is ProgressEvent.LevelUp) RoyalIcons.Flame else RoyalIcons.Trophy,
                contentDescription = null,
                tint = casino.gold,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(RoyalSpacing.m))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private val VISIBLE_TIME = 3.5.seconds
private val HIDE_ANIMATION = 350.milliseconds
