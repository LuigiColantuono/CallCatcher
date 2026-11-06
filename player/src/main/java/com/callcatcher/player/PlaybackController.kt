package com.callcatcher.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Immutable snapshot of what the player is doing, for the UI to render. */
data class PlaybackState(
    val path: String? = null,
    val isPlaying: Boolean = false,
    val positionMillis: Long = 0,
    val durationMillis: Long = 0,
)

/**
 * Thin wrapper over ExoPlayer. The UI observes [state] and calls play/pause/seek; it never touches
 * ExoPlayer directly. No capture logic here — this module only reads files and plays them (brief).
 */
class PlaybackController(context: Context) {

    private val player = ExoPlayer.Builder(context).build()
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = push()
            override fun onPlaybackStateChanged(playbackState: Int) = push()
        })
    }

    /** Load (if needed) and play the file at [path]. */
    fun play(path: String) {
        if (_state.value.path != path) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            player.prepare()
            _state.value = PlaybackState(path = path)
        }
        player.play()
    }

    fun pause() = player.pause()

    fun seekTo(positionMillis: Long) {
        player.seekTo(positionMillis)
        push()
    }

    /** Call periodically (e.g. every 200ms while playing) to advance the position readout. */
    fun tick() = push()

    fun release() = player.release()

    private fun push() {
        _state.value = _state.value.copy(
            isPlaying = player.isPlaying,
            positionMillis = player.currentPosition.coerceAtLeast(0),
            durationMillis = player.duration.coerceAtLeast(0),
        )
    }
}
