package com.example.ui.screens.home

import android.app.Activity
import android.app.RecoverableSecurityException
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.VideoItem
import com.example.ui.VideoViewModel
import com.example.ui.screens.components.MiniPlayerBar
import com.example.ui.screens.home.components.bottom_nav.CustomBottomNavigation
import com.example.ui.screens.home.components.tabs.FavoritesTabContent
import com.example.ui.screens.home.components.tabs.PlaylistsTabContent
import com.example.ui.screens.home.components.tabs.SettingsTab
import com.example.ui.screens.home.components.tabs.VideosTabContent
import com.example.ui.theme.CinemaBackground
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import java.io.File

@Composable
fun HomeScreen(
    viewModel: VideoViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylistDetail: (Long, String) -> Unit,
    onNavigateToLanguagePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by rememberSaveable { mutableStateOf(0) }
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val selectedThemeColor by viewModel.selectedThemeColor.collectAsState()
    val themeColorAccent = selectedThemeColor.primary
    val context = LocalContext.current
    val activity = context as Activity
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (activeTab != 0) {
            activeTab = 0
        } else {
            if (selectedFolder != null) {
                viewModel.selectFolder(null)
            } else {
                showExitDialog = true
            }
        }
    }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }

    var isSearchActive by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState()

    val currentVideo by viewModel.playerManager.currentVideo.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val currentPosition by viewModel.playerManager.currentPosition.collectAsState()
    val duration by viewModel.playerManager.duration.collectAsState()

    var pendingDeleteVideo by remember { mutableStateOf<VideoItem?>(null) }
    val executeDelete: (VideoItem) -> Unit = { video ->
        viewModel.deleteVideoFromDatabase(video)
        viewModel.removeVideoFromState(video)
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(
                context,
                context.getString(R.string.deleted_from_disk),
                Toast.LENGTH_SHORT
            ).show()
            pendingDeleteVideo?.let { executeDelete(it) }
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.deletion_cancelled),
                Toast.LENGTH_SHORT
            ).show()
        }
        pendingDeleteVideo = null
    }

    val handleDeleteVideo: (VideoItem) -> Unit = { video ->
        try {
            val file = File(video.path)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    try {
                        context.contentResolver.delete(video.uri, null, null)
                    } catch (e: Exception) {
                    }
                    executeDelete(video)
                    Toast.makeText(
                        context,
                        context.getString(R.string.deleted_title).replace("{title}", video.title),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            context.contentResolver.delete(video.uri, null, null)
                            executeDelete(video)
                            Toast.makeText(
                                context,
                                context.getString(R.string.deleted_title)
                                    .replace("{title}", video.title),
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        } catch (securityException: SecurityException) {
                            val recoverable =
                                securityException as? RecoverableSecurityException
                            if (recoverable != null) {
                                pendingDeleteVideo = video
                                val intentSenderRequest =
                                    IntentSenderRequest.Builder(
                                        recoverable.userAction.actionIntent.intentSender
                                    ).build()
                                deleteLauncher.launch(intentSenderRequest)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                pendingDeleteVideo = video
                                val pendingIntent = MediaStore.createDeleteRequest(
                                    context.contentResolver,
                                    listOf(video.uri)
                                )
                                val intentSenderRequest =
                                    IntentSenderRequest.Builder(
                                        pendingIntent.intentSender
                                    ).build()
                                deleteLauncher.launch(intentSenderRequest)
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.permissions_restricted_deletion),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.could_not_delete_physical_file),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                try {
                    context.contentResolver.delete(video.uri, null, null)
                } catch (e: Exception) {
                }
                executeDelete(video)
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted_from_memory).replace("{title}", video.title),
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    context.contentResolver.delete(video.uri, null, null)
                    executeDelete(video)
                    Toast.makeText(
                        context,
                        context.getString(R.string.deleted_title).replace("{title}", video.title),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (securityException: SecurityException) {
                    val recoverable = securityException as? RecoverableSecurityException
                    if (recoverable != null) {
                        pendingDeleteVideo = video
                        val intentSenderRequest =
                            IntentSenderRequest.Builder(
                                recoverable.userAction.actionIntent.intentSender
                            ).build()
                        deleteLauncher.launch(intentSenderRequest)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        pendingDeleteVideo = video
                        val pendingIntent = MediaStore.createDeleteRequest(
                            context.contentResolver,
                            listOf(video.uri)
                        )
                        val intentSenderRequest =
                            IntentSenderRequest.Builder(
                                pendingIntent.intentSender
                            ).build()
                        deleteLauncher.launch(intentSenderRequest)
                    }
                }
            } else {
                executeDelete(video)
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted_from_memory).replace("{title}", video.title),
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CinemaBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (activeTab == 0 && isSearchActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isSearchActive = false
                                viewModel.setSearchQuery("")
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = {
                                Text(
                                    stringResource(R.string.search_videos),
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = CinemaSurface,
                                unfocusedContainerColor = CinemaSurface,
                                disabledContainerColor = CinemaSurface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            stringResource(R.string.clear),
                                            tint = Color.LightGray
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("toolbar_search_field")
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (activeTab) {
                                    0 -> stringResource(R.string.my_library)
                                    1 -> stringResource(R.string.playlists_tab)
                                    2 -> stringResource(R.string.favorites_tab)
                                    3 -> stringResource(R.string.settings_tab)
                                    else -> stringResource(R.string.my_library)
                                },
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (activeTab == 0) {
                                val localVids by viewModel.localVideos.collectAsState()
                                val totalSize = localVids.sumOf { it.size }
                                val formattedSize =
                                    Formatter.formatShortFileSize(context, totalSize)
                                Text(
                                    text = stringResource(R.string.videos_used_stat)
                                        .replace("{count}", localVids.size.toString())
                                        .replace("{size}", formattedSize),
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (activeTab == 1) {
                                val playlists by viewModel.playlists.collectAsState()
                                Text(
                                    text = if (playlists.size == 1) {
                                        stringResource(R.string.playlist_count_singular)
                                    } else {
                                        stringResource(R.string.playlist_count_plural)
                                            .replace("{count}", playlists.size.toString())
                                    },
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (activeTab == 2) {
                                val favorites by viewModel.favorites.collectAsState()
                                Text(
                                    text = if (favorites.size == 1) {
                                        stringResource(R.string.saved_count_singular)
                                    } else {
                                        stringResource(R.string.saved_count_plural)
                                            .replace("{count}", favorites.size.toString())
                                    },
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Toolbar Action Buttons on the Right
                        when (activeTab) {
                            0 -> {
                                // Search
                                IconButton(
                                    onClick = { isSearchActive = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = stringResource(R.string.search),
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = { showPremiumDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = themeColorAccent.copy(
                                            alpha = 0.15f
                                        )
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        themeColorAccent.copy(alpha = 0.8f)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = stringResource(R.string.premium_icon),
                                        tint = themeColorAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.premium),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            1 -> {
                                IconButton(
                                    onClick = { showCreatePlaylistDialog = true },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(CinemaSurface, shape = CircleShape)
                                        .testTag("toolbar_add_playlist")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.create_playlist),
                                        tint = NeonCyan
                                    )
                                }
                            }

                            2 -> {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = stringResource(R.string.favorites_logo),
                                    tint = NeonMagenta,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            3 -> {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(NeonCyan, shape = CircleShape)
                                )
                            }

                            else -> {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(NeonCyan, shape = CircleShape)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(CinemaSurfaceVariant.copy(alpha = 0.3f))
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier.navigationBarsPadding()
            ) {
                // Persistent Mini Player above NavigationBar
                AnimatedVisibility(
                    visible = currentVideo != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    currentVideo?.let { video ->
                        MiniPlayerBar(
                            video = video,
                            isPlaying = isPlaying,
                            position = currentPosition,
                            duration = duration,
                            onPlayPauseToggle = {
                                if (isPlaying) viewModel.playerManager.pause()
                                else viewModel.playerManager.resume()
                            },
                            onPrevClick = {
                                viewModel.playerManager.playPrevious()
                            },
                            onNextClick = {
                                viewModel.playerManager.playNext()
                            },
                            onClose = {
                                viewModel.playerManager.stopAndDismiss()
                            },
                            onClick = onNavigateToPlayer
                        )
                    }
                }

                // Beautiful custom flat bottom navigation bar
                CustomBottomNavigation(
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it },
                    themeColorAccent = themeColorAccent
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isSearchActive) {
                        isSearchActive = false
                    }
                }
        ) {
            when (activeTab) {
                0 -> VideosTabContent(
                    viewModel = viewModel,
                    onVideoClick = { video, list ->
                        viewModel.playVideo(video, list)
                        onNavigateToPlayer()
                    },
                    onDeleteVideo = handleDeleteVideo
                )

                1 -> PlaylistsTabContent(
                    viewModel = viewModel,
                    onNavigateToPlaylistDetail = onNavigateToPlaylistDetail,
                    showCreateDialog = showCreatePlaylistDialog,
                    onShowCreateDialogChange = { showCreatePlaylistDialog = it }
                )

                2 -> FavoritesTabContent(viewModel, onVideoClick = { video, list ->
                    viewModel.playVideo(video, list)
                    onNavigateToPlayer()
                })

                3 -> SettingsTab(
                    viewModel = viewModel,
                    onUpgradeClick = { showPremiumDialog = true },
                    onNavigateToLanguagePicker = onNavigateToLanguagePicker
                )
            }

            // EXIT CONFIRMATION DIALOG
            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    title = {
                        Text(
                            stringResource(R.string.exit_app_title),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            stringResource(R.string.exit_app_confirm),
                            color = Color.LightGray
                        )
                    },
                    containerColor = CinemaSurface,
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showExitDialog = false
                                activity.finish()
                            }
                        ) {
                            Text(
                                stringResource(R.string.exit_btn),
                                color = themeColorAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showExitDialog = false }
                        ) {
                            Text(stringResource(R.string.cancel), color = NeonCyan)
                        }
                    }
                )
            }

            // PREMIUM MEMBERSHIP DIALOG
            if (showPremiumDialog) {
                //Show Premium dialog here
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconBgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(104.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(iconBgColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconBgColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Column {
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = title,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


