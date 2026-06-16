package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.RemoteAction
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.VideoViewModel
import com.example.ui.VideoViewModelFactory
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.PermissionScreen
import com.example.ui.screens.player.PlayerScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.LanguagePickerScreen
import com.example.ui.LanguageManager
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: VideoViewModel
    private var pipReceiver: BroadcastReceiver? = null

    private fun registerPipReceiver() {
        val filter = IntentFilter().apply {
            addAction("com.example.player.PIP_PLAY")
            addAction("com.example.player.PIP_PAUSE")
            addAction("com.example.player.PIP_NEXT")
            addAction("com.example.player.PIP_PREV")
        }
        pipReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "com.example.player.PIP_PLAY" -> {
                        viewModel.playerManager.resume()
                        updatePictureInPictureParams(isPlaying = true)
                    }
                    "com.example.player.PIP_PAUSE" -> {
                        viewModel.playerManager.pause()
                        updatePictureInPictureParams(isPlaying = false)
                    }
                    "com.example.player.PIP_NEXT" -> {
                        viewModel.playerManager.playNext()
                    }
                    "com.example.player.PIP_PREV" -> {
                        viewModel.playerManager.playPrevious()
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(pipReceiver, filter)
            }
        }
    }

    private fun unregisterPipReceiver() {
        pipReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore
            }
            pipReceiver = null
        }
    }

    fun updatePictureInPictureParams(isPlaying: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val actions = ArrayList<RemoteAction>()

            // 1. Prev Action
            val prevIntent = PendingIntent.getBroadcast(
                this,
                111,
                Intent("com.example.player.PIP_PREV"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val prevIcon = Icon.createWithResource("android", android.R.drawable.ic_media_previous)
            val prevAction = RemoteAction(
                prevIcon,
                "Previous",
                "Previous track",
                prevIntent
            )
            actions.add(prevAction)

            // 2. Play/Pause Action
            val playPauseActionIntent = if (isPlaying) {
                Intent("com.example.player.PIP_PAUSE")
            } else {
                Intent("com.example.player.PIP_PLAY")
            }
            val playPauseIntent = PendingIntent.getBroadcast(
                this,
                122,
                playPauseActionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val playPauseIconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val playPauseIcon = Icon.createWithResource("android", playPauseIconRes)
            val playPauseTitle = if (isPlaying) "Pause" else "Play"
            val playPauseAction = RemoteAction(
                playPauseIcon,
                playPauseTitle,
                playPauseTitle,
                playPauseIntent
            )
            actions.add(playPauseAction)

            // 3. Next Action
            val nextIntent = PendingIntent.getBroadcast(
                this,
                133,
                Intent("com.example.player.PIP_NEXT"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextIcon = Icon.createWithResource("android", android.R.drawable.ic_media_next)
            val nextAction = RemoteAction(
                nextIcon,
                "Next",
                "Next track",
                nextIntent
            )
            actions.add(nextAction)

            val params = PictureInPictureParams.Builder()
                .setActions(actions)
                .build()
            setPictureInPictureParams(params)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        LanguageManager.init(this)
        
        // Feed View Model via standard Android Factory
        val factory = VideoViewModelFactory(this)
        viewModel = ViewModelProvider(this, factory)[VideoViewModel::class.java]

        handleVideoIntent(intent)

        // Enforce full bleed edge-to-edge navigation offsets with solid black status/navigation bars
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )

        // Register custom Picture-in-Picture action receiver dynamically
        registerPipReceiver()

        // Monitor playing state variations to dynamically redraw Picture-in-Picture buttons
        lifecycleScope.launch {
            viewModel.playerManager.isPlaying.collectLatest { playing ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
                    updatePictureInPictureParams(playing)
                }
            }
        }

        setContent {
            // Read runtime customizations from Settings
            val isDarkTheme by viewModel.isDarkModeEnabled.collectAsState()
            val useDynamicColor by viewModel.isDynamicColorEnabled.collectAsState()

            MyApplicationTheme(
                darkTheme = isDarkTheme,
                dynamicColor = useDynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVideoIntent(intent)
    }

    private fun handleVideoIntent(intent: android.content.Intent?) {
        if (intent == null) return
        if (intent.action == android.content.Intent.ACTION_VIEW) {
            val uri: android.net.Uri? = intent.data
            if (uri != null) {
                viewModel.playExternalVideo(uri)
            }
        } else if (intent.action == "com.example.player.OPEN_PLAYER") {
            viewModel.navigateToPlayerScreen()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.playerManager.setInPipMode(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            updatePictureInPictureParams(viewModel.playerManager.isPlaying.value)
        }
    }

    private fun hasActualStoragePermission(): Boolean {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_VIDEO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return androidx.core.content.ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        val isGranted = hasActualStoragePermission()
        viewModel.setPermissionGranted(isGranted)
        if (isGranted) {
            viewModel.refreshVideos()
        }
    }

    override fun onStop() {
        super.onStop()
        // If background MP3 playback is enabled and a video is playing, continuation starts
        val isBackgroundPlayerEnabled = viewModel.isBackgroundPlaybackMp3Enabled.value
        val isVideoPlaying = viewModel.playerManager.isPlaying.value

        if (isBackgroundPlayerEnabled && isVideoPlaying) {
            viewModel.playerManager.setMp3Mode(true)
        } else {
            // If not in MP3 mode, pause standard video playback
            if (!viewModel.playerManager.isMp3Mode.value) {
                viewModel.playerManager.pause()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterPipReceiver()
        // Release Media Player instance when Activity is fully dismantled
        viewModel.playerManager.release()
    }
}

@SuppressLint("NewApi")
@Composable
fun AppNavigation(viewModel: VideoViewModel) {
    val navController = rememberNavController()
    val isPermissionGranted by viewModel.isPermissionGranted.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val navEvent by viewModel.navEvents.collectAsState()

    LaunchedEffect(navEvent) {
        navEvent?.let { destination ->
            navController.navigate(destination)
            viewModel.clearNavEvent()
        }
    }

    // Helper helper to check actual runtime permission status
    val hasStoragePermission = {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    NavHost(
        navController = navController, 
        startDestination = "splash"
    ) {
        // SCREEN 1: ANIMATED SPLASH
        composable("splash") {
            SplashScreen(
                onAnimationEnd = {
                    val pGranted = hasStoragePermission()
                    viewModel.setPermissionGranted(pGranted)
                    
                    if (!LanguageManager.isLanguageSelected(context)) {
                        navController.navigate("language_picker") {
                            popUpTo("splash") { inclusive = true }
                        }
                    } else if (pGranted) {
                        val navEventPending = viewModel.navEvents.value
                        if (navEventPending != null) {
                            navController.navigate("home") {
                                popUpTo("splash") { inclusive = true }
                            }
                            navController.navigate(navEventPending)
                            viewModel.clearNavEvent()
                        } else {
                            navController.navigate("home") {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                    } else {
                        navController.navigate("permission") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                }
            )
        }

        // SCREEN 1A: FIRST TIME LANGUAGE PICKER
        composable("language_picker") {
            LanguagePickerScreen(
                onLanguageSelected = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        val pGranted = hasStoragePermission()
                        if (pGranted) {
                            navController.navigate("home") {
                                popUpTo("language_picker") { inclusive = true }
                            }
                        } else {
                            navController.navigate("permission") {
                                popUpTo("language_picker") { inclusive = true }
                            }
                        }
                    }
                },
                onBack = if (navController.previousBackStackEntry != null) {
                    { navController.popBackStack() }
                } else null
            )
        }

        // SCREEN 2: DYNAMIC PERMISSION REQUESTOR
        composable("permission") {
            PermissionScreen(
                onPermissionGranted = {
                    viewModel.setPermissionGranted(true)
                    navController.navigate("home") {
                        popUpTo("permission") { inclusive = true }
                    }
                }
            )
        }

        // SCREEN 3: TAB MULTIPLEX HOME INDEX
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToPlayer = {
                    navController.navigate("player")
                },
                onNavigateToPlaylistDetail = { id, name ->
                    navController.navigate("playlist_detail/$id/$name")
                },
                onNavigateToLanguagePicker = {
                    navController.navigate("language_picker")
                }
            )
        }

        // SCREEN 4: GESTURES VIDEO FULL-VIEWPORT PLAYER
        composable("player") {
            PlayerScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // SCREEN 5: PLAYLIST DETAILS SELECTION
        composable(
            route = "playlist_detail/{playlistId}/{playlistName}",
            arguments = listOf(
                navArgument("playlistId") { type = NavType.LongType },
                navArgument("playlistName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("playlistId") ?: 0L
            val name = backStackEntry.arguments?.getString("playlistName") ?: "Playlist"
            PlaylistDetailScreen(
                playlistId = id,
                playlistName = name,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { navController.navigate("player") }
            )
        }
    }
}
