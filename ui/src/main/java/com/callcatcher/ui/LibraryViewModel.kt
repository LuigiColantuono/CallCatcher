package com.callcatcher.ui

import android.Manifest
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callcatcher.core.CallCatcherCapture
import com.callcatcher.core.capture.LiveRecording
import com.callcatcher.core.db.CallCatcherDatabase
import com.callcatcher.core.model.CallMetadata
import com.callcatcher.core.model.CallSource
import com.callcatcher.core.service.RecorderService
import com.callcatcher.core.storage.StatusStore
import com.callcatcher.player.PlaybackController
import com.callcatcher.ui.model.LibraryStats
import com.callcatcher.ui.model.RecordingUi
import com.callcatcher.ui.model.SetupStatus
import com.callcatcher.ui.model.SourceFilter
import com.callcatcher.ui.model.toUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = CallCatcherDatabase.get(app).recordingDao()
    private val statusStore = StatusStore(app)
    private val engine = CallCatcherCapture.engine(app)
    val playback = PlaybackController(app)

    /** The recording in progress (call or test), or null. */
    val live: StateFlow<LiveRecording?> = engine.live

    /** Peak level of the latest captured buffer, for the live scope. */
    val level: StateFlow<Float> = engine.level

    private val _filter = MutableStateFlow(SourceFilter.ALL)
    val filter: StateFlow<SourceFilter> = _filter

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _status = MutableStateFlow(SetupStatus())
    val status: StateFlow<SetupStatus> = _status

    private val all = dao.observeAll()

    val stats: StateFlow<LibraryStats> = all
        .map { rows -> LibraryStats(rows.size, rows.sumOf { it.durationMillis }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryStats())

    val recordings: StateFlow<List<RecordingUi>> =
        combine(all, _filter, _query) { rows, filter, query ->
            rows.asSequence()
                .map { it.toUi() }
                .filter { filter.matches(it.source) }
                .filter {
                    query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                        it.address?.contains(query) == true
                }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            CallCatcherCapture.reconcileLibrary(getApplication())
        }
        // A finished recording updates "last saved" / "last error" and free space.
        viewModelScope.launch {
            engine.recording.collect { refreshStatus() }
        }
    }

    fun setFilter(filter: SourceFilter) { _filter.value = filter }
    fun setQuery(query: String) { _query.value = query }

    fun select(recording: RecordingUi) = playback.play(recording.path)
    fun togglePlayback() {
        val state = playback.state.value
        val path = state.path ?: return
        if (state.isPlaying) playback.pause() else playback.play(path)
    }
    fun closePlayer() = playback.stop()
    fun seekTo(positionMillis: Long) = playback.seekTo(positionMillis)
    fun cycleSpeed() {
        val speeds = listOf(1f, 1.25f, 1.5f, 2f)
        val next = speeds[(speeds.indexOf(playback.state.value.speed) + 1).mod(speeds.size)]
        playback.setSpeed(next)
    }

    /** Microphone recording (a voice memo) until [stopRecording]. Closes the player first. */
    fun startRecording() {
        val app = getApplication<Application>()
        if (engine.isRecording) return
        playback.stop()
        app.startForegroundService(
            RecorderService.startIntent(app, CallMetadata(source = CallSource.TEST, contactName = "Recording"))
        )
    }

    /** Stops whatever is being recorded; the file is kept. */
    fun stopRecording() = engine.onStreamEnd()

    fun delete(recording: RecordingUi) {
        if (playback.state.value.path == recording.path) playback.stop()
        viewModelScope.launch(Dispatchers.IO) {
            File(recording.path).delete()
            dao.deleteById(recording.id)
        }
    }

    fun clearError() {
        statusStore.clearError()
        refreshStatus()
    }

    fun refreshStatus() {
        val app = getApplication<Application>()
        fun granted(permission: String) = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val snapshot = statusStore.snapshot()
        val baseDir = CallCatcherCapture.defaultBaseDir(app)
        val free = runCatching { StatFs((baseDir.takeIf { it.exists() } ?: app.filesDir).path).availableBytes }.getOrDefault(0L)
        _status.value = SetupStatus(
            loaded = true,
            isSystemApp = app.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            canCaptureCallAudio = granted("android.permission.CAPTURE_AUDIO_OUTPUT"),
            canDetectCalls = granted("android.permission.CONTROL_INCALL_EXPERIENCE"),
            canCapturePlayback = granted("android.permission.MODIFY_AUDIO_ROUTING") &&
                granted("android.permission.CAPTURE_VOICE_COMMUNICATION_OUTPUT"),
            hasMicrophone = granted(Manifest.permission.RECORD_AUDIO),
            hasContacts = granted(Manifest.permission.READ_CONTACTS),
            hasNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                granted(Manifest.permission.POST_NOTIFICATIONS),
            freeBytes = free,
            lastError = snapshot.lastError,
            lastErrorAt = snapshot.lastErrorAt,
            lastSaved = snapshot.lastSaved,
            lastSavedAt = snapshot.lastSavedAt,
            lastTriggerAt = snapshot.lastTriggerAt,
        )
    }

    override fun onCleared() {
        playback.release()
    }
}
