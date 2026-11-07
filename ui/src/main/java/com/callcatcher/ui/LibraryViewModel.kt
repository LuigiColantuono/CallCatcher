package com.callcatcher.ui

import android.Manifest
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callcatcher.core.CallCatcherCapture
import com.callcatcher.core.db.CallCatcherDatabase
import com.callcatcher.core.model.CallMetadata
import com.callcatcher.core.model.CallSource
import com.callcatcher.core.service.RecorderService
import com.callcatcher.core.storage.StatusStore
import com.callcatcher.player.PlaybackController
import com.callcatcher.ui.model.RecordingUi
import com.callcatcher.ui.model.SetupStatus
import com.callcatcher.ui.model.SourceFilter
import com.callcatcher.ui.model.toUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = CallCatcherDatabase.get(app).recordingDao()
    private val statusStore = StatusStore(app)
    private val engine = CallCatcherCapture.engine(app)
    val playback = PlaybackController(app)

    /** True while any recording (call or test) is in progress. */
    val isRecording: StateFlow<Boolean> = engine.recording

    private val _filter = MutableStateFlow(SourceFilter.ALL)
    val filter: StateFlow<SourceFilter> = _filter

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _status = MutableStateFlow(SetupStatus())
    val status: StateFlow<SetupStatus> = _status

    val recordings: StateFlow<List<RecordingUi>> =
        combine(dao.observeAll(), _filter, _query) { rows, filter, query ->
            rows.asSequence()
                .map { it.toUi() }
                .filter { filter.matches(it.source) }
                .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.address?.contains(query) == true }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            CallCatcherCapture.reconcileLibrary(getApplication())
        }
        // Refresh the status line when a recording finishes (new "last saved" / "last error").
        viewModelScope.launch {
            engine.recording.collect { refreshStatus() }
        }
    }

    fun setFilter(filter: SourceFilter) { _filter.value = filter }
    fun setQuery(query: String) { _query.value = query }

    fun play(path: String) = playback.play(path)
    fun pause() = playback.pause()
    fun seekTo(positionMillis: Long) = playback.seekTo(positionMillis)

    /** 5-second microphone recording: verifies encoding, storage and playback without a call. */
    fun startTestRecording() {
        val app = getApplication<Application>()
        if (engine.isRecording) return
        app.startForegroundService(
            RecorderService.startIntent(app, CallMetadata(source = CallSource.TEST, contactName = "Test"), autoStopMillis = 5_000)
        )
    }

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
        _status.value = SetupStatus(
            isSystemApp = app.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            canCaptureCallAudio = granted("android.permission.CAPTURE_AUDIO_OUTPUT"),
            canDetectCalls = granted("android.permission.CONTROL_INCALL_EXPERIENCE"),
            hasMicrophone = granted(Manifest.permission.RECORD_AUDIO),
            hasContacts = granted(Manifest.permission.READ_CONTACTS),
            hasNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                granted(Manifest.permission.POST_NOTIFICATIONS),
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
