package com.example.ui.screens

import com.example.ui.LanguageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VideoItem
import com.example.data.utils.VideoUtils.formatDuration
import com.example.data.utils.VideoUtils.getRelativeTimeAgo
import com.example.ui.VideoViewModel
import com.example.ui.screens.components.MiniPlayerBar
import com.example.ui.screens.home.components.VideoThumbnail
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import androidx.core.net.toUri
import com.example.R

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playlistName: String,
    viewModel: VideoViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Intercept default back press key/gesture securely
    BackHandler(enabled = true) {
        onNavigateBack()
    }

    val playlistVideos by viewModel.getPlaylistVideosFlow(playlistId).collectAsState()

    // Map DB PlaylistVideo structures to VideoItems
    val videoItems = playlistVideos.map { pv ->
        VideoItem(
            id = pv.id,
            uri = pv.uri.toUri(),
            title = pv.title,
            duration = pv.duration,
            size = pv.size,
            path = pv.path,
            resolution = null,
            dateAdded = System.currentTimeMillis() / 1000,
            folderName = playlistName
        )
    }

    val selectedThemeColor by viewModel.selectedThemeColor.collectAsState()
    val themeColorAccent = selectedThemeColor.primary
    val context = LocalContext.current

    val playerManager = viewModel.playerManager
    val currentVideo by playerManager.currentVideo.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val currentPosition by playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .testTag("playlist_detail_screen"),
        bottomBar = {
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
                            if (isPlaying) playerManager.pause()
                            else playerManager.resume()
                        },
                        onPrevClick = {
                            playerManager.playPrevious()
                        },
                        onNextClick = {
                            playerManager.playNext()
                        },
                        onClose = {
                            playerManager.stopAndDismiss()
                        },
                        onClick = onNavigateToPlayer
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // TOP HEADER BAR Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.background(CinemaSurface, shape = CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back", tint = themeColorAccent)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = playlistName,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitleText = if (videoItems.size == 1) {
                        stringResource(R.string.video_count_singular)
                    } else {
                        stringResource(R.string.video_count_plural).replace("{count}", videoItems.size.toString())
                    }
                    Text(
                        text = subtitleText,
                        color = themeColorAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (videoItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = themeColorAccent.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_videos_in_playlist),
                            color = Color.LightGray.copy(alpha = 0.6f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(videoItems, key = { it.id }) { video ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.playVideo(video, videoItems)
                                    onNavigateToPlayer()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Video Thumbnail on the left (matching list library video items exactly)
                                Box(
                                    modifier = Modifier
                                        .size(width = 110.dp, height = 68.dp)
                                        .background(CinemaSurfaceVariant, shape = RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    VideoThumbnail(video = video, modifier = Modifier.fillMaxSize())
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = formatDuration(video.duration),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Custom detailed text content block
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = video.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    val displayResolution = "HD"
                                    val formattedSize = if (video.size > 0) {
                                        android.text.format.Formatter.formatShortFileSize(context, video.size)
                                    } else {
                                        stringResource(R.string.size_unknown)
                                    }
                                    val timeAgoStr = getRelativeTimeAgo(context,video.dateAdded)

                                    Text(
                                        text = "$displayResolution  •  $formattedSize  •  $timeAgoStr",
                                        color = Color.LightGray.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = { viewModel.removeVideoFromPlaylistById(video.id) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Remove from playlist",
                                        tint = themeColorAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
