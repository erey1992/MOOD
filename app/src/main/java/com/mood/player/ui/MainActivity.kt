package com.mood.player.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.mood.player.download.youtubeLink

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private var sharedUrl by mutableStateOf<String?>(null)
    private var openDownloads by mutableStateOf(false)

    private val audioPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val requestedPermissions: Array<String>
        get() = buildList {
            add(audioPermission)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { !isGranted(it) }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readDownloadIntent(intent)
        // La intro del logo solo se muestra al abrir la app, no al rotar ni al recrear la actividad.
        val coldStart = savedInstanceState == null
        setContent {
            MoodTheme {
                var showIntro by rememberSaveable { mutableStateOf(coldStart) }
                var hasAudioAccess by remember { mutableStateOf(isGranted(audioPermission)) }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    hasAudioAccess = isGranted(audioPermission)
                }

                LaunchedEffect(Unit) {
                    val missing = requestedPermissions
                    if (missing.isNotEmpty()) launcher.launch(missing)
                }
                LaunchedEffect(hasAudioAccess) {
                    if (hasAudioAccess) viewModel.loadMusicIfNeeded()
                }

                Box(Modifier.fillMaxSize()) {
                    if (hasAudioAccess) {
                        MoodApp(viewModel, sharedUrl, openDownloads, onDownloadIntentConsumed = ::consumeDownloadIntent)
                    } else {
                        PermissionScreen(onRequest = { launcher.launch(requestedPermissions) })
                    }
                    if (showIntro) LogoIntro(onFinished = { showIntro = false })
                }
            }
        }
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readDownloadIntent(intent)
    }

    private fun readDownloadIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedUrl = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let(::youtubeLink)?.url
            openDownloads = sharedUrl != null
        } else if (intent?.getBooleanExtra("show_downloads", false) == true) {
            openDownloads = true
        }
    }

    private fun consumeDownloadIntent() {
        sharedUrl = null
        openDownloads = false
        // A configuration change must not replay a link the download screen already received.
        intent?.removeExtra(Intent.EXTRA_TEXT)
        intent?.removeExtra("show_downloads")
    }
}
