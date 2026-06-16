package com.example.ui.screens.player

import android.Manifest
import com.example.R

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import kotlin.OptIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.MainActivity
import com.example.data.model.VideoItem
import com.example.data.utils.VideoUtils.formatDuration
import com.example.ui.VideoViewModel
import com.example.ui.screens.home.components.PauseIcon
import com.example.ui.theme.BrightCyan
import com.example.ui.theme.CinemaBackground
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.player.VideoPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

enum class FullScreenAspectRatio {
    FIT, FILL, ZOOM
}

suspend fun extractFrameAtTime(context: Context, videoUri: Uri, positionMs: Long): Bitmap? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, videoUri)
        val frameTimeMicros = positionMs * 1000L
        retriever.getFrameAtTime(frameTimeMicros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    } finally {
        retriever.release()
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: VideoViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val playerManager = viewModel.playerManager

    BackHandler(enabled = true) {
        playerManager.setMiniPlayerActive(true) // Return to home with active miniplayer
        onNavigateBack()
    }

    val currentVideo by playerManager.currentVideo.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val currentPosition by playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()
    val playbackSpeed by playerManager.playbackSpeed.collectAsState()
    val isMp3Mode by playerManager.isMp3Mode.collectAsState()
    val isInPipMode by playerManager.isInPipMode.collectAsState()
    val isLoading by playerManager.isLoading.collectAsState()

    // Playlist states
    val repeatMode by playerManager.repeatMode.collectAsState()
    val isShuffleEnabled by playerManager.isShuffleEnabled.collectAsState()
    val sleepRemaining by playerManager.sleepTimeRemaining.collectAsState()

    val favorites by viewModel.favorites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val selectedThemeColor by viewModel.selectedThemeColor.collectAsState()
    val themeColorAccent = selectedThemeColor.primary
    var showPlaylistDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            playerManager.toggleMp3Mode()
        } else {
            Toast.makeText(context, "Notification permission is required for background MP3 controls", Toast.LENGTH_SHORT).show()
        }
    }

    // Screen controllers
    var currentAspectRatio by remember { mutableStateOf(FullScreenAspectRatio.FIT) }
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }

    // Pinch to zoom scaling state
    var scaleFactor by remember { mutableStateOf(1f) }

    // Floating screenshot states
    var screenshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showScreenshotIndicator by remember { mutableStateOf(false) }
    var screenFlashActive by remember { mutableStateOf(false) }

    // Equalizer State Variables
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var eqPresetSelected by remember { mutableStateOf("Flat") }
    val eqSliders = remember { mutableStateMapOf(60 to 0f, 230 to 0f, 910 to 0f, 4000 to 0f, 14000 to 0f) }

    // Sleep Timer Dialog state
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    // Speed Selector popup sheet
    var showSpeedSheet by remember { mutableStateOf(false) }

    // Video Details Dialog state
    var showDetailsDialog by remember { mutableStateOf(false) }

    // Gesture transient feedback indicators
    var showVolumeHUD by remember { mutableStateOf(false) }
    var volumeLevelHUD by remember { mutableStateOf(0f) } // 0.0 to 1.0

    var showBrightnessHUD by remember { mutableStateOf(false) }
    var brightnessLevelHUD by remember { mutableStateOf(0f) } // 0.0 to 1.0

    var showDoubleTapForwardHUD by remember { mutableStateOf(false) }
    var showDoubleTapBackwardHUD by remember { mutableStateOf(false) }

    // Audio & Brightness Android APIs
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }

    // Fetch initial device brightness or remember session brightness
    val activity = context as? Activity
    var currentBrightness by remember {
        mutableStateOf(
            playerManager.sessionBrightness ?: activity?.window?.attributes?.screenBrightness?.let {
                if (it < 0f) 0.5f else it
            } ?: 0.5f
        )
    }

    // Apply remembered volume session
    LaunchedEffect(Unit) {
        playerManager.sessionVolume?.let { savedVol ->
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (savedVol * maxVolume).toInt(), 0)
        }
        playerManager.sessionBrightness?.let { savedBright ->
            activity?.let { act ->
                val lp = act.window.attributes
                lp.screenBrightness = savedBright
                act.window.attributes = lp
            }
        }
        playerManager.setEqualizerBands(eqSliders.toMap())
    }

    // Auto Hide controls timer
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Capture Screen flash timer
    LaunchedEffect(screenFlashActive) {
        if (screenFlashActive) {
            delay(150)
            screenFlashActive = false
        }
    }

    // Screenshot autohide timer
    LaunchedEffect(showScreenshotIndicator) {
        if (showScreenshotIndicator) {
            delay(3500)
            showScreenshotIndicator = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_screen")
    ) {
        if (currentVideo == null) {
            onNavigateBack()
        } else {
            val video = currentVideo!!

            // BASE PLAYER CONTAINER OR AUDIO ONLY
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scaleFactor,
                        scaleY = scaleFactor
                    )
            ) {
                if (isMp3Mode) {
                    // Play as MP3 Soundwaves backdrop mode
                    SoundwaveEqualizerBackdrop(
                        video = video,
                        isPlaying = isPlaying,
                        primaryColor = themeColorAccent,
                        secondaryColor = selectedThemeColor.secondary,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Video rendering stream
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = playerManager.getPlayerInstance()
                                useController = false // Hide native controls
                                clipToOutline = true
                            }
                        },
                        update = { view ->
                            view.resizeMode = when (currentAspectRatio) {
                                FullScreenAspectRatio.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                FullScreenAspectRatio.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                FullScreenAspectRatio.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = NeonCyan,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            // GESTURE & TOUCH MULTIPLEXER LAYER (Disabled entirely in picture-in-picture)
            if (!isInPipMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isLocked) {
                            detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, rotation ->
                                if (!isLocked) {
                                    scaleFactor = (scaleFactor * zoom).coerceIn(1f, 4f)
                                }
                            }
                        }
                        .pointerInput(isLocked) {
                            detectTapGestures(
                                onTap = {
                                    if (!isLocked) {
                                        showControls = !showControls
                                    } else {
                                        // Lock overlay reveals floating unlock button
                                        showControls = true
                                    }
                                },
                                onDoubleTap = { offset ->
                                    if (!isLocked) {
                                        val screenWidth = size.width
                                        val partitionX = screenWidth / 2f
                                        if (offset.x < partitionX) {
                                            // Left double-tap: seek backward 10s
                                            playerManager.skipBackward()
                                            scope.launch {
                                                showDoubleTapBackwardHUD = true
                                                delay(600)
                                                showDoubleTapBackwardHUD = false
                                            }
                                        } else {
                                            // Right double-tap: seek forward 10s
                                            playerManager.skipForward()
                                            scope.launch {
                                                showDoubleTapForwardHUD = true
                                                delay(600)
                                                showDoubleTapForwardHUD = false
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(isLocked) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (!isLocked) {
                                        val screenWidth = size.width
                                        if (offset.x < screenWidth / 2f) {
                                            showBrightnessHUD = true
                                        } else {
                                            showVolumeHUD = true
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (!isLocked) {
                                        change.consume()
                                        val screenWidth = size.width
                                        val isLeftHalf = change.position.x < screenWidth / 2f

                                        // Drag sensitivity factor
                                        val sensitivity = 0.005f

                                        if (isLeftHalf) {
                                            // Adjust Brightness
                                            currentBrightness = (currentBrightness - dragAmount.y * sensitivity)
                                                .coerceIn(0.01f, 1.0f)
                                            
                                            // Apply to Device Window
                                            activity?.let { act ->
                                                val lp = act.window.attributes
                                                lp.screenBrightness = currentBrightness
                                                act.window.attributes = lp
                                            }
                                            playerManager.sessionBrightness = currentBrightness
                                            brightnessLevelHUD = currentBrightness
                                            showBrightnessHUD = true
                                        } else {
                                            // Adjust Audio Volume
                                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            val deltaVol = -dragAmount.y * sensitivity * maxVolume
                                            val newVol = (currentVol + deltaVol).coerceIn(0f, maxVolume)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol.toInt(), 0)

                                            val volVolumeFactor = newVol / maxVolume
                                            playerManager.sessionVolume = volVolumeFactor
                                            volumeLevelHUD = volVolumeFactor
                                            showVolumeHUD = true
                                        }
                                    }
                                },
                                onDragEnd = {
                                    showVolumeHUD = false
                                    showBrightnessHUD = false
                                }
                            )
                        }
                )
            }

            // PICTURE-IN-PICTURE MINIMAL CLEAN RUMBLE STATE
            if (!isInPipMode) {

                // TOP BAR OVERLAY CONTROL TRAY
                AnimatedVisibility(
                    visible = showControls && !isLocked,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -50 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -50 })
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                )
                            )
                            .padding(top = 16.dp, bottom = 24.dp, start = 12.dp, end = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Row 1: Back, Title, HW, More options list
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        playerManager.setMiniPlayerActive(true) // Return to home with active miniplayer
                                        onNavigateBack()
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = video.title,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Share video instead of HW
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/*"
                                            putExtra(Intent.EXTRA_STREAM, video.uri)
                                            putExtra(Intent.EXTRA_SUBJECT, video.title)
                                            putExtra(Intent.EXTRA_TEXT, "Check out this video: ${video.title}")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Video"))
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "Share video",
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Three-vertical dots Options Menu
                                Box {
                                    var showMenu by remember { mutableStateOf(false) }
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(Icons.Filled.MoreVert, "More options", tint = Color.White)
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false },
                                        modifier = Modifier.background(CinemaSurface)
                                    ) {
                                        val isFav = favorites.any { it.uri == video.uri.toString() }
                                        DropdownMenuItem(
                                            text = { Text(if (isFav) stringResource(R.string.remove_favorite) else stringResource(R.string.add_favorite), color = Color.White, fontSize = 13.sp) },
                                            onClick = {
                                                viewModel.toggleFavorite(video)
                                                showMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Favorite, null, tint = themeColorAccent, modifier = Modifier.size(18.dp)) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.add_to_playlist), color = Color.White, fontSize = 13.sp) },
                                            onClick = {
                                                showPlaylistDialog = true
                                                showMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Filled.List, null, tint = themeColorAccent, modifier = Modifier.size(18.dp)) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (isShuffleEnabled) stringResource(R.string.shuffle_enabled) else stringResource(R.string.shuffle_disabled), color = Color.White) },
                                            onClick = {
                                                playerManager.toggleShuffle()
                                                showMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Refresh, null, tint = themeColorAccent, modifier = Modifier.size(18.dp)) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (repeatMode == VideoPlayerManager.RepeatMode.ONE) stringResource(R.string.repeat_mode_on) else stringResource(R.string.repeat_mode_off), color = Color.White) },
                                            onClick = {
                                                playerManager.toggleRepeatMode()
                                                showMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Filled.PlayArrow, null, tint = themeColorAccent, modifier = Modifier.size(18.dp)) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sleep_timer), color = Color.White) },
                                            onClick = {
                                                showSleepTimerDialog = true
                                                showMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Notifications, null, tint = themeColorAccent, modifier = Modifier.size(18.dp)) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (isMp3Mode) stringResource(R.string.play_with_video) else stringResource(R.string.play_as_mp3), color = Color.White) },
                                            onClick = {
                                                if (!isMp3Mode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    val hasNotifPerm = ContextCompat.checkSelfPermission(
                                                        context,
                                                        Manifest.permission.POST_NOTIFICATIONS
                                                    ) == PackageManager.PERMISSION_GRANTED
                                                    if (hasNotifPerm) {
                                                        playerManager.toggleMp3Mode()
                                                    } else {
                                                        notificationPermissionLauncher.launch(
                                                            Manifest.permission.POST_NOTIFICATIONS)
                                                    }
                                                } else {
                                                    playerManager.toggleMp3Mode()
                                                }
                                                showMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Headphones, null, tint = themeColorAccent, modifier = Modifier.size(18.dp)) }
                                        )

                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.video_details), color = Color.White, fontSize = 13.sp) },
                                            onClick = {
                                                showDetailsDialog = true
                                                showMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Info, null, tint = themeColorAccent, modifier = Modifier.size(18.dp)) }
                                        )


                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Row 2: Translucent circle quick setting buttons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Equalizer/Tune circular button
                                IconButton(
                                    onClick = { showEqualizerDialog = true },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Tune,
                                        contentDescription = "Equalizer",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // 2. Playback Speed button
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                                        .clickable { showSpeedSheet = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${playbackSpeed}X",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 3. Screenshot Capture button
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            screenFlashActive = true
                                            val currentUri = currentVideo?.uri
                                            if (currentUri != null) {
                                                val bitmap = extractFrameAtTime(context, currentUri, currentPosition)
                                                if (bitmap != null) {
                                                    screenshotBitmap = bitmap
                                                    showScreenshotIndicator = true
                                                } else {
                                                    Toast.makeText(context, "Frame extraction error", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CameraAlt,
                                        contentDescription = "Screenshot",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // 4. Headset (Play sound only / MP3 Mode)
                                IconButton(
                                    onClick = { playerManager.toggleMp3Mode() },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(
                                            color = if (isMp3Mode) themeColorAccent.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Headphones,
                                        contentDescription = "Background Audio",
                                        tint = if (isMp3Mode) themeColorAccent else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // 5. Rotate Screen icon
                                IconButton(
                                    onClick = {
                                        val act = context as? Activity
                                        act?.let {
                                            val currentOrient = it.requestedOrientation
                                            it.requestedOrientation = if (currentOrient == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            } else {
                                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ScreenRotation,
                                        contentDescription = "Screen Rotation",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }


                            }
                        }
                    }
                }

                // GESTURES FLOAT HEAD OVERLAYS
                // Volume floating meter (right side centered)
                AnimatedVisibility(
                    visible = showVolumeHUD,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 40.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .width(60.dp)
                            .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp))
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val volumeIcon = if (volumeLevelHUD <= 0.01f) {
                            Icons.Filled.VolumeOff
                        } else {
                            Icons.Filled.VolumeUp
                        }
                        Icon(volumeIcon, null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .height(100.dp)
                                .width(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.DarkGray)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(volumeLevelHUD)
                                    .align(Alignment.BottomCenter)
                                    .background(NeonCyan)
                            )
                        }
                    }
                }

                // Brightness floating meter (left side centered)
                AnimatedVisibility(
                    visible = showBrightnessHUD,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 40.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .width(60.dp)
                            .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp))
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.WbSunny, null, tint = BrightCyan, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .height(100.dp)
                                .width(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.DarkGray)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(brightnessLevelHUD)
                                    .align(Alignment.BottomCenter)
                                    .background(BrightCyan)
                            )
                        }
                    }
                }

                // Pulsing double tap arrows notifications
                AnimatedVisibility(
                    visible = showDoubleTapBackwardHUD,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 60.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Replay10, null, tint = NeonCyan, modifier = Modifier.size(48.dp))
                        Text("-10 Seconds", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                AnimatedVisibility(
                    visible = showDoubleTapForwardHUD,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 60.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Forward10, null, tint = NeonCyan, modifier = Modifier.size(48.dp))
                        Text("+10 Seconds", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // MAIN PLAYER CONTROL PANEL (Locked elements hidden if isLocked is active)
                AnimatedVisibility(
                    visible = showControls && !isLocked,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { 50 }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                                )
                            )
                            .padding(top = 24.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Sleep countdown label
                            if (sleepRemaining != null) {
                                val minsLeft = sleepRemaining!! / 60
                                val secsLeft = sleepRemaining!! % 60
                                Box(
                                    modifier = Modifier
                                        .background(NeonMagenta.copy(alpha = 0.25f), shape = RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Notifications, null, tint = NeonMagenta, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Sleep Timer: ${String.format("%02d:%02d", minsLeft, secsLeft)}",
                                            color = NeonMagenta,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // 1. Beautiful timeline slider with real-time tracking tooltip
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Tooltip tracking the thumb position exactly matching the screenshot
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 42.dp)
                                ) {
                                    val fraction = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                                    val safeFraction = fraction.coerceIn(0f, 1f)
                                    val tooltipWidth = 64.dp
                                    
                                    val xOffset = (this.maxWidth - tooltipWidth) * safeFraction
                                    
                                    Column(
                                        modifier = Modifier.offset(x = xOffset),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF232428), shape = RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = formatDuration(currentPosition),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Canvas(modifier = Modifier.size(9.dp, 5.dp)) {
                                            val path = Path().apply {
                                                moveTo(0f, 0f)
                                                lineTo(size.width, 0f)
                                                lineTo(size.width / 2, size.height)
                                                close()
                                            }
                                            drawPath(path, color = Color(0xFF232428))
                                         }
                                     }
                                 }
                                 
                                 Spacer(modifier = Modifier.height(2.dp))

                                 Row(
                                     modifier = Modifier.fillMaxWidth(),
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                     Text(
                                         text = formatDuration(currentPosition),
                                         color = Color.White,
                                         fontSize = 12.sp,
                                         fontWeight = FontWeight.Bold
                                     )

                                     Slider(
                                         value = if (duration > 0) currentPosition.toFloat() else 0f,
                                         onValueChange = { playerManager.seekTo(it.toLong()) },
                                         valueRange = 0f..(if (duration > 0) duration.toFloat() else 100f),
                                         colors = SliderDefaults.colors(
                                             thumbColor = themeColorAccent,
                                             activeTrackColor = themeColorAccent,
                                             inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                                         ),
                                         thumb = {
                                             Box(
                                                 modifier = Modifier
                                                     .size(20.dp)
                                                     .background(
                                                         Brush.radialGradient(
                                                             colors = listOf(Color.White, themeColorAccent, themeColorAccent)
                                                         ),
                                                         shape = CircleShape
                                                     )
                                                     .drawBehind {
                                                         drawCircle(
                                                             color = themeColorAccent.copy(alpha = 0.45f),
                                                             radius = size.minDimension / 2 + 5.dp.toPx()
                                                         )
                                                     }
                                             )
                                         },
                                         modifier = Modifier
                                             .weight(1f)
                                             .padding(horizontal = 12.dp)
                                             .testTag("timeline_slider")
                                     )

                                     Text(
                                         text = formatDuration(duration),
                                         color = Color.White,
                                         fontSize = 12.sp,
                                         fontWeight = FontWeight.Bold
                                     )
                                 }
                             }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 2. Continuous streamline controller bar matching the screenshot
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Lock trigger
                                IconButton(
                                    onClick = { isLocked = true },
                                    modifier = Modifier.testTag("player_lock_trigger")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LockOpen,
                                        contentDescription = "Lock controls",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // 2. Skip Previous
                                IconButton(
                                    onClick = { playerManager.playPrevious() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipPrevious,
                                        contentDescription = "Previous Track",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                // Quick Rewind 10s
                                IconButton(
                                    onClick = { playerManager.skipBackward() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Replay10,
                                        contentDescription = "Rewind 10 Seconds",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                // 3. Play / Pause
                                IconButton(
                                    onClick = {
                                        if (isPlaying) playerManager.pause()
                                        else playerManager.resume()
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .testTag("play_pause_fab")
                                ) {
                                    if (isPlaying) {
                                        PauseIcon(modifier = Modifier.size(36.dp), tint = Color.White)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Playback Action",
                                            tint = Color.White,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                }

                                // Quick Forward 10s
                                IconButton(
                                    onClick = { playerManager.skipForward() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Forward10,
                                        contentDescription = "Forward 10 Seconds",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                // 4. Skip Next
                                IconButton(
                                    onClick = { playerManager.playNext() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipNext,
                                        contentDescription = "Next Track",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                // 5. Aspect Ratio Toggle
                                IconButton(
                                    onClick = {
                                        currentAspectRatio = when (currentAspectRatio) {
                                            FullScreenAspectRatio.FIT -> FullScreenAspectRatio.FILL
                                            FullScreenAspectRatio.FILL -> FullScreenAspectRatio.ZOOM
                                            FullScreenAspectRatio.ZOOM -> FullScreenAspectRatio.FIT
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AspectRatio,
                                        contentDescription = "Aspect Ratio",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // 6. Picture-in-Picture Mode / Share
                                IconButton(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            try {
                                                if (activity is MainActivity) {
                                                    activity.updatePictureInPictureParams(playerManager.isPlaying.value)
                                                }
                                                activity?.enterPictureInPictureMode(
                                                    PictureInPictureParams.Builder().build()
                                                )
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "PiP Mode not available", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            activity?.enterPictureInPictureMode()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PictureInPictureAlt,
                                        contentDescription = "Enter Picture in Picture",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Floating Lock overlay for unlocking when screen is locked
                if (isLocked) {
                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(24.dp)
                    ) {
                        FloatingActionButton(
                            onClick = { isLocked = false },
                            containerColor = CinemaSurface.copy(alpha = 0.7f),
                            contentColor = NeonCyan,
                            shape = CircleShape,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Unlock controls",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // SCREENSHOT SHUTTER FLASH OVERLAY
        AnimatedVisibility(
            visible = screenFlashActive,
            enter = fadeIn(animationSpec = tween(50)),
            exit = fadeOut(animationSpec = tween(100))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f))
            )
        }

        // ANIMATED SCREENSHOT THUMBNAIL FEEDBACK CONTAINER
        AnimatedVisibility(
            visible = showScreenshotIndicator && screenshotBitmap != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 90.dp, end = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, NeonCyan),
                modifier = Modifier
                    .width(190.dp)
                    .clickable { showScreenshotIndicator = false }
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    screenshotBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Captured file thumb",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Screenshot", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Saved to memory", color = NeonCyan, fontSize = 9.sp)
                        Text("Tap to dismiss", color = Color.Gray, fontSize = 8.sp)
                    }
                }
            }
        }
    }

    // SPEED SHEET DIALOG
    if (showSpeedSheet) {
        AlertDialog(
            onDismissRequest = { showSpeedSheet = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Settings, null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Playback Speed", color = Color.White)
                }
            },
            containerColor = CinemaSurface,
            text = {
                Column {
                    val list = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    list.forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerManager.setSpeed(speed)
                                    showSpeedSheet = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${speed}x", color = Color.White, fontWeight = FontWeight.Bold)
                            if (playbackSpeed == speed) {
                                Icon(Icons.Filled.Check, null, tint = NeonCyan)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedSheet = false }) {
                    Text("Close", color = NeonCyan)
                }
            }
        )
    }

    // STUDIO AUDIO EQUALIZER POPUP DIALOG
    if (showEqualizerDialog) {
        AlertDialog(
            onDismissRequest = { showEqualizerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.List, null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Audio Equalizer & Presets", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            },
            containerColor = CinemaSurface,
            text = {
                Column {
                    // Presets selector
                    Text("CHOOSE AUDIO PRESET", color = NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    val presets = listOf("Flat", "Bass Boost", "Pop", "Rock", "Vocal Highlight")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(presets) { preset ->
                            val isSelected = preset == eqPresetSelected
                            Surface(
                                onClick = {
                                    eqPresetSelected = preset
                                    // Update mocked audio frequencies
                                    when (preset) {
                                        "Flat" -> {
                                            eqSliders[60] = 0f; eqSliders[230] = 0f; eqSliders[910] = 0f; eqSliders[4000] = 0f; eqSliders[14000] = 0f
                                        }
                                        "Bass Boost" -> {
                                            eqSliders[60] = 9f; eqSliders[230] = 6f; eqSliders[910] = 2f; eqSliders[4000] = -1f; eqSliders[14000] = -2f
                                        }
                                        "Pop" -> {
                                            eqSliders[60] = -2f; eqSliders[230] = 1f; eqSliders[910] = 5f; eqSliders[4000] = 4f; eqSliders[14000] = 2f
                                        }
                                        "Rock" -> {
                                            eqSliders[60] = 6f; eqSliders[230] = 3f; eqSliders[910] = -1f; eqSliders[4000] = 4f; eqSliders[14000] = 6f
                                        }
                                        "Vocal Highlight" -> {
                                            eqSliders[60] = -5f; eqSliders[230] = -2f; eqSliders[910] = 4f; eqSliders[4000] = 8f; eqSliders[14000] = 3f
                                        }
                                    }
                                    playerManager.setEqualizerBands(eqSliders.toMap())
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) NeonCyan else CinemaBackground,
                                border = BorderStroke(1.dp, if (isSelected) NeonCyan else Color.DarkGray)
                            ) {
                                Text(
                                    text = preset,
                                    color = if (isSelected) CinemaBackground else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

                    Text("FINE GRAIN DECIBELS RANGE [-15dB to +15dB]", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Live sliders
                    val bands = listOf(60, 230, 910, 4000, 14000)
                    bands.forEach { freq ->
                        val value = eqSliders[freq] ?: 0f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (freq >= 1000) "${freq / 1000}kHz" else "${freq}Hz",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(55.dp)
                            )
                            Slider(
                                value = value,
                                onValueChange = { 
                                    eqSliders[freq] = it
                                    playerManager.setEqualizerBands(eqSliders.toMap())
                                },
                                valueRange = -15f..15f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = NeonCyan,
                                    inactiveTrackColor = Color.DarkGray
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${if (value >= 0) "+" else ""}${value.toInt()} dB",
                                color = if (value != 0f) NeonCyan else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(45.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    playerManager.setEqualizerBands(eqSliders.toMap())
                    showEqualizerDialog = false 
                }) {
                    Text("Apply & Close", color = NeonCyan)
                }
            }
        )
    }

    // SLEEP TIMER OPTIONS DIALOG
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, null, tint = NeonMagenta, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sleep Timer Options", color = Color.White)
                }
            },
            containerColor = CinemaSurface,
            text = {
                Column {
                    Text("Auto pause video playback after configured period.", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    val timers = listOf(
                        "Cancel Sleep Timer" to 0,
                        "5 Minutes" to 5,
                        "15 Minutes" to 15,
                        "30 Minutes" to 30,
                        "60 Minutes" to 60,
                        "120 Minutes" to 120
                    )

                    timers.forEach { (label, mins) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (mins == 0) {
                                        playerManager.cancelSleepTimer()
                                        Toast.makeText(context, "Sleep Timer Cancelled", Toast.LENGTH_SHORT).show()
                                    } else {
                                        playerManager.startSleepTimer(mins)
                                        Toast.makeText(context, "Sleep Timer Set to $mins mins", Toast.LENGTH_SHORT).show()
                                    }
                                    showSleepTimerDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = if (mins == 0) NeonMagenta else Color.White, fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.ArrowForward, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Dismiss", color = NeonCyan)
                }
            }
        )
    }

    // SELECT PLAYLIST DIALOG
    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, null, tint = themeColorAccent, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Playlist", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CinemaSurface,
            text = {
                Column {
                    if (playlists.isEmpty()) {
                        Text("No custom playlists found. Go to Collections to create one.", color = Color.LightGray)
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                        ) {
                            items(playlists) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentVideo?.let { viewModel.addVideoToPlaylist(playlist.id, it) }
                                            showPlaylistDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Folder, null, tint = themeColorAccent, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(playlist.name, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            }
        )
    }

    // VIDEO DETAILS DIALOG
    if (showDetailsDialog) {
        currentVideo?.let { video ->
            AlertDialog(
                onDismissRequest = { showDetailsDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.video_details),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item { VideoDetailRow(label = stringResource(R.string.detail_title), value = video.title) }
                        item { VideoDetailRow(label = stringResource(R.string.detail_folder), value = video.folderName) }
                        item { VideoDetailRow(label = stringResource(R.string.detail_file_path), value = video.path) }
                        item {
                            VideoDetailRow(
                                label = stringResource(R.string.detail_file_size),
                                value = if (video.size > 0) {
                                    Formatter.formatShortFileSize(context, video.size)
                                } else {
                                    stringResource(R.string.detail_unknown)
                                }
                            )
                        }
                        item { VideoDetailRow(label = stringResource(R.string.detail_duration), value = formatDuration(video.duration)) }
                        video.resolution?.let { res ->
                            if (res.isNotEmpty()) {
                                item { VideoDetailRow(label = stringResource(R.string.detail_resolution), value = res) }
                            }
                        }
                        item { VideoDetailRow(label = stringResource(R.string.detail_source), value = stringResource(R.string.detail_source_local)) }
                    }
                },
                containerColor = CinemaSurface,
                confirmButton = {
                    TextButton(
                        onClick = { showDetailsDialog = false }
                    ) {
                        Text(stringResource(R.string.dismiss_btn), color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }

}

@Composable
fun VideoDetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Color.LightGray.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// EQUALIZER MUSIC BACKDROP CANVAS GRAPHICS
@Composable
fun SoundwaveEqualizerBackdrop(
    video: VideoItem,
    isPlaying: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Soundwaves")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = Math.PI.toFloat() * 2,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Equalizer Offset"
    )

    Box(
        modifier = modifier.background(CinemaBackground),
        contentAlignment = Alignment.Center
    ) {
        // Glowing radial orbits
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.2f), Color.Transparent),
                    center = center,
                    radius = size.width / 2f
                ),
                radius = size.width / 2.2f,
                center = center
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.playing_audio_as_mp3_backstage),
                color = secondaryColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Waveform visualizer bars Canvas drawing
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                val numBars = 32
                val spacing = 8f
                val totalWidth = size.width
                val barWidth = (totalWidth - (spacing * (numBars - 1))) / numBars
                val centerY = size.height / 2f

                for (i in 0 until numBars) {
                    val sinFactor = sin(i.toFloat() * 0.3f + waveOffset).toFloat()
                    val scale = if (isPlaying) 0.6f + 0.4f * sinFactor else 0.1f
                    val barHeight = size.height * scale
                    val x = i * (barWidth + spacing)

                    // Draw vertical gradient bar
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(primaryColor, secondaryColor)
                        ),
                        topLeft = Offset(x, centerY - barHeight / 2f),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }
    }
}
