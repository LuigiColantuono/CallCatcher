package com.callcatcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.callcatcher.core.capture.LiveRecording
import com.callcatcher.ui.components.BottomDock
import com.callcatcher.ui.components.EmptyLibrary
import com.callcatcher.ui.components.LIVE_SLOTS
import com.callcatcher.ui.components.LiveDeck
import com.callcatcher.ui.components.PlaybackDeck
import com.callcatcher.ui.components.RecordingList
import com.callcatcher.ui.components.SearchField
import com.callcatcher.ui.components.SegmentedFilter
import com.callcatcher.ui.components.StatusCard
import com.callcatcher.ui.components.TopBar
import com.callcatcher.ui.model.RecordingUi
import com.callcatcher.ui.model.SourceFilter
import com.callcatcher.ui.theme.CC
import com.callcatcher.ui.util.formatSize
import com.callcatcher.ui.util.formatTotalDuration
import com.callcatcher.ui.util.shareRecording

/**
 * The whole app in one window: top bar, optional setup card, a deck that appears while listening
 * or recording, the filterable list, and the bottom dock. Nothing opens a second screen.
 */
@Composable
fun HomeScreen(viewModel: LibraryViewModel) {
    val recordings by viewModel.recordings.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val query by viewModel.query.collectAsState()
    val playback by viewModel.playback.state.collectAsState()
    val status by viewModel.status.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val live by viewModel.live.collectAsState()

    val context = LocalContext.current
    var statusOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(status.loaded, status.ready) {
        if (status.loaded && !status.ready) statusOpen = true
    }

    val selected = recordings.firstOrNull { it.path == playback.path }

    // Keep the last value so the exit animation still has something to draw.
    var lastLive by remember { mutableStateOf<LiveRecording?>(null) }
    var lastSelected by remember { mutableStateOf<RecordingUi?>(null) }
    live?.let { lastLive = it }
    selected?.let { lastSelected = it }

    val levels = remember { mutableStateListOf<Float>() }
    LaunchedEffect(live != null) {
        levels.clear()
        if (live != null) {
            viewModel.level.collect {
                levels.add(it)
                if (levels.size > LIVE_SLOTS) levels.removeAt(0)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(CC.Canvas).systemBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                summary = summaryLine(stats.count, stats.totalDurationMillis, status.freeBytes),
                ready = status.ready,
                searchOpen = searchOpen,
                onToggleSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) viewModel.setQuery("")
                },
                onToggleStatus = { statusOpen = !statusOpen },
            )

            Reveal(visible = statusOpen) {
                StatusCard(
                    status = status,
                    onClearError = viewModel::clearError,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                )
            }

            Reveal(visible = live != null) {
                lastLive?.let {
                    LiveDeck(it, levels, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
                }
            }

            Reveal(visible = live == null && selected != null) {
                lastSelected?.let { rec ->
                    PlaybackDeck(
                        recording = rec,
                        playback = playback,
                        onSeek = viewModel::seekTo,
                        onTogglePlay = viewModel::togglePlayback,
                        onCycleSpeed = viewModel::cycleSpeed,
                        onShare = { shareRecording(context, rec.path, rec.title) },
                        onDelete = { viewModel.delete(rec) },
                        onClose = viewModel::closePlayer,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    )
                }
            }

            SegmentedFilter(
                selected = filter,
                onSelect = viewModel::setFilter,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Reveal(visible = searchOpen) {
                SearchField(
                    query = query,
                    onQueryChange = viewModel::setQuery,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 10.dp),
                )
            }

            if (recordings.isEmpty()) {
                EmptyLibrary(filtered = filter != SourceFilter.ALL || query.isNotBlank())
            } else {
                RecordingList(
                    recordings = recordings,
                    selectedPath = playback.path,
                    onSelect = viewModel::select,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                )
            }
        }

        BottomDock(
            recording = live != null,
            onClick = { if (live != null) viewModel.stopRecording() else viewModel.startRecording() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) { content() }
}

private fun summaryLine(count: Int, totalMillis: Long, freeBytes: Long): String {
    val free = if (freeBytes > 0) "${formatSize(freeBytes)} free" else null
    val library = when (count) {
        0 -> "No recordings"
        1 -> "1 recording · ${formatTotalDuration(totalMillis)}"
        else -> "$count recordings · ${formatTotalDuration(totalMillis)}"
    }
    return listOfNotNull(library, free).joinToString(" · ")
}
