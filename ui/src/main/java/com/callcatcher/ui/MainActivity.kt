package com.callcatcher.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.callcatcher.ui.components.LegalNoticeDialog
import com.callcatcher.ui.theme.CallCatcherTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: LibraryViewModel by viewModels()

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.refreshStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("callcatcher", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTICE_ACCEPTED, false)) requestMissingPermissions()

        setContent {
            CallCatcherTheme {
                var noticeAccepted by remember {
                    mutableStateOf(prefs.getBoolean(KEY_NOTICE_ACCEPTED, false))
                }

                // Advance the position readout while something is playing.
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
                        requestMissingPermissions()
                    })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions can change in Settings, and the module install only takes effect after reboot.
        viewModel.refreshStatus()
    }

    private fun requestMissingPermissions() {
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionRequest.launch(missing.toTypedArray())
    }

    companion object {
        private const val KEY_NOTICE_ACCEPTED = "legal_notice_accepted"
    }
}
