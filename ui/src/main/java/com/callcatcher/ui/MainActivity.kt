package com.callcatcher.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import com.callcatcher.ui.components.LegalNoticeDialog
import com.callcatcher.ui.theme.CallCatcherTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("callcatcher", MODE_PRIVATE)

        setContent {
            CallCatcherTheme {
                var noticeAccepted by remember {
                    mutableStateOf(prefs.getBoolean(KEY_NOTICE_ACCEPTED, false))
                }

                // Drive the playback position readout while something is playing.
                val playback by viewModel.playback.state.collectAsState()
                LaunchedEffect(playback.isPlaying) {
                    while (playback.isPlaying) {
                        viewModel.playback.tick()
                        delay(200)
                    }
                }

                HomeScreen(viewModel)

                if (!noticeAccepted) {
                    LegalNoticeDialog(onAccept = {
                        prefs.edit().putBoolean(KEY_NOTICE_ACCEPTED, true).apply()
                        noticeAccepted = true
                    })
                }
            }
        }
    }

    companion object {
        private const val KEY_NOTICE_ACCEPTED = "legal_notice_accepted"
    }
}
