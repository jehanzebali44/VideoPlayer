package com.example.ui.screens.home.components.tabs

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.VideoViewModel
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant

@Composable
fun PlaylistsTabContent(
    viewModel: VideoViewModel,
    onNavigateToPlaylistDetail: (Long, String) -> Unit,
    showCreateDialog: Boolean,
    onShowCreateDialogChange: (Boolean) -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    var playlistNameInput by remember { mutableStateOf("") }
    val selectedThemeColor by viewModel.selectedThemeColor.collectAsState()
    val themeColorAccent = selectedThemeColor.primary

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("playlists_tab")
    ) {
        if (playlists.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CinemaSurface.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.create_playlists_curate),
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(playlists) { playlist ->
                val videosInPlaylist by viewModel.getPlaylistVideosFlow(playlist.id)
                    .collectAsState()
                val videoCount = videosInPlaylist.size
                val subtitleText = if (videoCount == 1) {
                    stringResource(R.string.video_count_singular)
                } else {
                    stringResource(R.string.video_count_plural)
                        .replace("{count}", videoCount.toString())
                }
                val selectedThemeColor by viewModel.selectedThemeColor.collectAsState()
                val themeColorAccent = selectedThemeColor.primary

                Card(
                    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPlaylistDetail(playlist.id, playlist.name) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Custom Playlist Thumbnail block with themed gradient
                        Box(
                            modifier = Modifier
                                .size(width = 110.dp, height = 68.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            themeColorAccent,
                                            themeColorAccent.copy(alpha = 0.4f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clip(RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                contentDescription = "Playlist representation",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Playlist Metadata titles
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = playlist.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = subtitleText,
                                color = Color.LightGray.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Options / Delete icon button
                        IconButton(
                            onClick = { viewModel.deletePlaylist(playlist.id) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete playlist",
                                tint = themeColorAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { onShowCreateDialogChange(false) },
            title = { Text(stringResource(R.string.create_new_playlist), color = Color.White) },
            containerColor = CinemaSurface,
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    label = {
                        Text(
                            stringResource(R.string.playlist_name),
                            color = themeColorAccent
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeColorAccent,
                        unfocusedBorderColor = CinemaSurfaceVariant,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistNameInput.isNotBlank()) {
                            viewModel.createPlaylist(playlistNameInput)
                            playlistNameInput = ""
                            onShowCreateDialogChange(false)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColorAccent,
                        contentColor = Color.Black
                    )
                ) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowCreateDialogChange(false) }) {
                    Text(
                        stringResource(R.string.cancel),
                        color = Color.LightGray
                    )
                }
            }
        )
    }
}