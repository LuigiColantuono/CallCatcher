package com.callcatcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callcatcher.core.CallCatcherCapture
import com.callcatcher.core.db.CallCatcherDatabase
import com.callcatcher.player.PlaybackController
import com.callcatcher.ui.model.RecordingUi
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

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = CallCatcherDatabase.get(app).recordingDao()
    val playback = PlaybackController(app)

    private val _filter = MutableStateFlow(SourceFilter.ALL)
    val filter: StateFlow<SourceFilter> = _filter

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val recordings: StateFlow<List<RecordingUi>> =
        combine(dao.observeAll(), _filter, _query) { rows, filter, query ->
            rows.asSequence()
                .map { it.toUi() }
                .filter { filter.matches(it.source) }
                .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            CallCatcherCapture.reconcileLibrary(getApplication())
        }
    }

    fun setFilter(filter: SourceFilter) { _filter.value = filter }
    fun setQuery(query: String) { _query.value = query }

    fun play(path: String) = playback.play(path)
    fun pause() = playback.pause()

    fun delete(recording: RecordingUi) {
        viewModelScope.launch(Dispatchers.IO) {
            java.io.File(recording.path).delete()
            dao.deleteById(recording.id)
        }
    }

    override fun onCleared() {
        playback.release()
    }
}
