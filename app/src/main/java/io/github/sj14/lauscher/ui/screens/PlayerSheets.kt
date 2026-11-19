package io.github.sj14.lauscher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sj14.lauscher.R
import io.github.sj14.lauscher.data.ShakeSensitivity
import io.github.sj14.lauscher.data.db.Chapter
import io.github.sj14.lauscher.playback.SLEEP_TIMER_MINUTES
import io.github.sj14.lauscher.playback.SleepTimerState
import io.github.sj14.lauscher.ui.LocalContainer
import io.github.sj14.lauscher.ui.components.Artwork
import io.github.sj14.lauscher.ui.formatTime
import kotlinx.coroutines.launch

/** The playback speeds offered in the player's speed sheet. */
private val SPEEDS = listOf(0.75f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

@Composable
internal fun SpeedSheet(current: Float, onSelect: (Float) -> Unit) {
    SheetTitle(stringResource(R.string.playback_speed))
    LazyColumn {
        items(SPEEDS) { speed ->
            SheetOption(formatSpeed(speed), selected = speed == current) { onSelect(speed) }
        }
    }
}

@Composable
internal fun SleepSheet(state: SleepTimerState, onDone: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val timer = container.sleepTimer
    val settings by container.settings.settings.collectAsStateWithLifecycle(null)
    val shakeEnabled = settings?.sleepShakeSensitivity != ShakeSensitivity.OFF
    SheetTitle(stringResource(R.string.sleep_timer))
    if (state is SleepTimerState.Countdown && shakeEnabled) {
        Text(
            stringResource(R.string.sleep_shake_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
    }
    LazyColumn {
        items(SLEEP_TIMER_MINUTES) { minutes ->
            SheetOption(pluralStringResource(R.plurals.minutes, minutes, minutes), selected = false) {
                timer.start(minutes)
                scope.launch { container.settings.setSleepMinutes(minutes) }
                onDone()
            }
        }
        item {
            SheetOption(stringResource(R.string.sleep_end_of_episode), selected = state == SleepTimerState.EndOfEpisode) {
                timer.startEndOfEpisode()
                onDone()
            }
        }
        if (state != SleepTimerState.Off) {
            item {
                SheetOption(stringResource(R.string.sleep_off), selected = false) {
                    timer.cancel()
                    onDone()
                }
            }
        }
    }
}

@Composable
internal fun ChapterSheet(
    chapters: List<Chapter>,
    position: Long,
    episodeImageUrl: String?,
    onSelect: (Chapter) -> Unit,
) {
    val current = chapters.lastOrNull { it.startMs <= position }
    // Either every entry gets an image or none, so the titles stay aligned. The few chapters
    // without an image of their own show the cover, as they do in the player.
    val showImages = chapters.any { it.imageUrl != null }
    SheetTitle(stringResource(R.string.chapters))
    LazyColumn {
        items(chapters) { chapter ->
            ListItem(
                headlineContent = { Text(chapter.title) },
                supportingContent = { Text(formatTime(chapter.startMs)) },
                leadingContent = if (showImages) {
                    {
                        Artwork(
                            chapter.imageUrl,
                            Modifier.size(48.dp),
                            cornerRadius = 8.dp,
                            fallbackUrl = episodeImageUrl,
                        )
                    }
                } else {
                    null
                },
                colors = sheetItemColors(selected = chapter == current),
                modifier = Modifier.clickable { onSelect(chapter) },
            )
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun SheetOption(text: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        colors = sheetItemColors(selected),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Highlights the selected entry of a sheet; the others are transparent, so the sheet's background shows. */
@Composable
private fun sheetItemColors(selected: Boolean): ListItemColors {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    return ListItemDefaults.colors(containerColor = background)
}

@Composable
internal fun sleepLabel(state: SleepTimerState): String = when (state) {
    SleepTimerState.Off -> stringResource(R.string.sleep_timer)
    SleepTimerState.EndOfEpisode -> stringResource(R.string.sleep_end_of_episode_short)
    is SleepTimerState.Countdown -> formatTime(state.remainingMs)
}

internal fun formatSpeed(speed: Float): String {
    val text = if (speed == speed.toInt().toFloat()) "%.1f".format(speed) else "%.2f".format(speed).trimEnd('0')
    return "${text}×"
}
