package com.example.ui.screens.home.components.tabs

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.VideoItem
import com.example.ui.VideoViewModel
import com.example.ui.screens.home.components.ListVideoCard
import com.example.ui.theme.CinemaSurface

@Composable
fun FavoritesTabContent(
    viewModel: VideoViewModel,
    onVideoClick: (VideoItem, List<VideoItem>) -> Unit
) {
    val favorites by viewModel.favorites.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("favorites_tab")
    ) {
        if (favorites.isEmpty()) {
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
                            stringResource(R.string.no_favorites_yet),
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(favorites) { fav ->
                val videoItem = VideoItem(
                    id = fav.uri.hashCode().toLong(),
                    uri = Uri.parse(fav.uri),
                    title = fav.title,
                    duration = fav.duration,
                    size = fav.size,
                    path = fav.path,
                    resolution = null,
                    dateAdded = fav.dateAdded,
                    folderName = "Favorites"
                )
                ListVideoCard(
                    video = videoItem,
                    onClick = {
                        val mappedList = favorites.map { item ->
                            VideoItem(
                                id = item.uri.hashCode().toLong(),
                                uri = Uri.parse(item.uri),
                                title = item.title,
                                duration = item.duration,
                                size = item.size,
                                path = item.path,
                                resolution = null,
                                dateAdded = item.dateAdded,
                                folderName = "Favorites"
                            )
                        }
                        onVideoClick(videoItem, mappedList)
                    },
                    viewModel = viewModel
                )
            }
        }
    }
}