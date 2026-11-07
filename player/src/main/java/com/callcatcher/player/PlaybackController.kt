package com.callcatcher.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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
    val speed: Float = 1f,
    val error: String? = null,
)

/**
 * Thin wrapper over ExoPlayer. The UI observes [state] and calls play/pause/seek; it never touches
 * ExoPlayer directly. No capture logic here: this module only reads files and plays them.
 * Must be used from the main thread (ExoPlayer's application looper).
 */
class PlaybackController(context: Context) {

    private val player = ExoPlayer.Builder(context).build()
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = push()
            override fun onPlaybackStateChanged(playbackState: Int) = push()
            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(isPlaying = false, error = error.errorCodeName)
            }
        })
    }

    /** Load (if needed) and play [path]; restarts from the top if it had finished. */
    fun play(path: String) {
        if (_state.value.path != path) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            player.prepare()
            _state.value = PlaybackState(path = path, speed = _state.value.speed)
        } else if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
        }
        player.play()
    }

    fun pause() = player.pause()

    fun seekTo(positionMillis: Long) {
        player.seekTo(positionMillis.coerceAtLeast(0))
        push()
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    /** Unload the current item (e.g. before deleting its file). Keeps the chosen speed. */
    fun stop() {
        player.stop()
        player.clearMediaItems()
        _state.value = PlaybackState(speed = _state.value.speed)
    }

    /** Call periodically while playing to advance the position readout. */
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
