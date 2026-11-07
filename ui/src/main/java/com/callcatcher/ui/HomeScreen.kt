package com.callcatcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.callcatcher.ui.components.PlayerBar
import com.callcatcher.ui.components.RecordingRow
import com.callcatcher.ui.components.StatusPanel
import com.callcatcher.ui.model.RecordingUi
import com.callcatcher.ui.model.SourceFilter

private val RecordingRed = Color(0xFFFF453A)

@Composable
fun HomeScreen(viewModel: LibraryViewModel) {
    val recordings by viewModel.recordings.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val query by viewModel.query.collectAsState()
    val playback by viewModel.playback.state.collectAsState()
    val status by viewModel.status.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()

    var pendingDelete by remember { mutableStateOf<RecordingUi?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val current = recordings.firstOrNull { it.path == playback.path }
            if (current != null) {
                PlayerBar(
                    title = current.title,
                    state = playback,
                    onPlay = { viewModel.play(current.path) },
                    onPause = { viewModel.pause() },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Header(isRecording = isRecording, onTestRecording = viewModel::startTestRecording)

            StatusPanel(
                status = status,
                onClearError = viewModel::clearError,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SearchField(query = query, onQueryChange = viewModel::setQuery)
            FilterRow(selected = filter, onSelect = viewModel::setFilter)

            if (recordings.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(recordings, key = { it.id }) { recording ->
                        RecordingRow(
                            recording = recording,
                            isActive = recording.path == playback.path,
                            playback = playback,
                            onPlay = { viewModel.play(recording.path) },
                            onPause = { viewModel.pause() },
                            onSeek = viewModel::seekTo,
                            onLongPress = { pendingDelete = recording },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete recording?") },
            text = { Text("${target.title} will be removed from the list and deleted from storage.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Header(isRecording: Boolean, onTestRecording: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "CallCatcher", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.weight(1f))
        if (isRecording) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(RecordingRed, CircleShape))
                Text("Recording", style = MaterialTheme.typography.labelMedium, color = RecordingRed)
            }
        } else {
            IconButton(onClick = onTestRecording) {
                Icon(Icons.Filled.Mic, contentDescription = "5-second test recording")
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        placeholder = { Text("Search name or number") },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun FilterRow(selected: SourceFilter, onSelect: (SourceFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceFilter.entries.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(option.label) },
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "No recordings yet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
