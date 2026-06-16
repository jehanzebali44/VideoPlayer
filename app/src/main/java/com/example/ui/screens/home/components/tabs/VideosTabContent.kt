package com.example.ui.screens.home.components.tabs

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.VideoItem
import com.example.data.utils.VideoUtils.formatDuration
import com.example.data.utils.VideoUtils.formatRelativeTime
import com.example.data.utils.VideoUtils.getFolderIcon
import com.example.data.utils.toVideoItem
import com.example.ui.SortBy
import com.example.ui.VideoViewModel
import com.example.ui.screens.home.components.VideoThumbnail
import com.example.ui.screens.home.StatCard
import com.example.ui.screens.home.components.GridVideoCard
import com.example.ui.screens.home.components.ListVideoCard
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.NeonCyan
import kotlinx.coroutines.delay
import kotlin.collections.chunked


// TAB 1: VIDEOS TAB CONTENT (Unified Scrolling Layout)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosTabContent(
    viewModel: VideoViewModel,
    onVideoClick: (VideoItem, List<VideoItem>) -> Unit,
    onDeleteVideo: (VideoItem) -> Unit
) {
    val videos by viewModel.filteredVideos.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isGrid by viewModel.isGridLayout.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val isLoading by viewModel.isLoadingVideos.collectAsState()

    val historyList by viewModel.playbackHistory.collectAsState()
    val showContinueWatchingSection by viewModel.showContinueWatching.collectAsState()
    val showRecentlyPlayedSection by viewModel.showRecentlyPlayed.collectAsState()

    val continueWatching = remember(historyList) {
        historyList.filter { it.lastPosition > 3000 && it.lastPosition < (it.duration - 5000) }
    }

    val recentlyPlayed = historyList

    val selectedThemeColor by viewModel.selectedThemeColor.collectAsState()
    val themeColorAccent = selectedThemeColor.primary

    Column(modifier = Modifier.fillMaxSize()) {
        val selectedFolder by viewModel.selectedFolder.collectAsState()
        val foldersMap by viewModel.folders.collectAsState()
        val folderList = remember(foldersMap) {
            listOf("All") + foldersMap.keys.toList().sorted()
        }

        val allLocalVideos by viewModel.localVideos.collectAsState()
        val totalSize = allLocalVideos.sumOf { it.size }
        val formattedSize = Formatter.formatShortFileSize(LocalContext.current, totalSize)
        val favoritesList by viewModel.favorites.collectAsState()
        val totalFavorites = favoritesList.size


        val videosListState = rememberLazyListState()

        var hasResetCompleted by remember { mutableStateOf(false) }
        LaunchedEffect(isLoading, historyList, videos) {
            if (!isLoading && !hasResetCompleted && (historyList.isNotEmpty() || videos.isNotEmpty())) {
                delay(120)
                try {
                    videosListState.scrollToItem(0)
                    hasResetCompleted = true
                } catch (e: Exception) {
                }
            }
        }

        // UNIFIED NON-NESTED SCROLLING CONTAINER
        LazyColumn(
            state = videosListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("videos_list_parent"),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // STAT CARDS (Localizable & Horizontally Scrollable)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatCard(
                        title = stringResource(R.string.videos_tab),
                        value = allLocalVideos.size.toString(),
                        icon = Icons.Filled.PlayArrow,
                        iconBgColor = themeColorAccent,
                        onClick = { viewModel.selectFolder(null) },
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = stringResource(R.string.storage_used),
                        value = formattedSize,
                        icon = Icons.Filled.Folder,
                        iconBgColor = themeColorAccent,
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = stringResource(R.string.favorites_tab),
                        value = totalFavorites.toString(),
                        icon = Icons.Filled.Favorite,
                        iconBgColor = themeColorAccent,
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // FOLDER CAPSULE CHIPS ROW
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 16.dp)
                ) {
                    items(folderList) { folder ->
                        val isSelected =
                            (folder == "All" && selectedFolder == null) || (folder == selectedFolder)
                        val count = if (folder == "All") {
                            allLocalVideos.size
                        } else {
                            allLocalVideos.count { it.folderName == folder }
                        }

                        val displayName =
                            if (folder == "All") stringResource(R.string.all_videos) else folder

                        Surface(
                            onClick = {
                                if (folder == "All") {
                                    viewModel.selectFolder(null)
                                } else {
                                    viewModel.selectFolder(folder)
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            color = if (isSelected) CinemaSurfaceVariant else CinemaSurface,
                            border = BorderStroke(
                                1.2.dp,
                                if (isSelected) themeColorAccent else Color.Transparent
                            ),
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .testTag("folder_chip_$folder")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = getFolderIcon(folder),
                                    contentDescription = null,
                                    tint = if (isSelected) themeColorAccent else Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = displayName,
                                        color = if (isSelected) Color.White else Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = count.toString(),
                                        color = if (isSelected) themeColorAccent else Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. CONTINUE WATCHING PROGRESS SECTIONS
            if (showContinueWatchingSection && continueWatching.isNotEmpty() && searchQuery.isBlank() && selectedFolder == null) {
                item {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Continue Watching",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            items(continueWatching) { history ->
                                val videoItem = history.toVideoItem()
                                Card(
                                    modifier = Modifier
                                        .width(280.dp)
                                        .height(154.dp)
                                        .clickable { onVideoClick(videoItem, videos) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        // Background Thumbnail
                                        VideoThumbnail(videoItem, modifier = Modifier.fillMaxSize())

                                        // Dark vertical gradient shade overlay for pristine readability
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.Transparent,
                                                            Color.Black.copy(alpha = 0.85f)
                                                        ),
                                                        startY = 50f
                                                    )
                                                )
                                        )

                                        // Foreground elements
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            // Empty top
                                            Spacer(modifier = Modifier.height(1.dp))

                                            // Bottom text contents with play button
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Mini Translucent Circular Play icon
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(
                                                            Color.Black.copy(alpha = 0.6f),
                                                            shape = CircleShape
                                                        )
                                                        .border(
                                                            1.dp,
                                                            Color.White.copy(alpha = 0.2f),
                                                            shape = CircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.PlayArrow,
                                                        contentDescription = "Play icon",
                                                        tint = themeColorAccent,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(10.dp))

                                                Column {
                                                    val progress =
                                                        if (history.duration > 0) history.lastPosition.toFloat() / history.duration else 0f
                                                    val progressPct =
                                                        (progress * 100).toInt().coerceIn(0, 100)

                                                    // Watch Capsule Badge
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                Color.Black.copy(alpha = 0.5f),
                                                                shape = RoundedCornerShape(10.dp)
                                                            )
                                                            .border(
                                                                0.5.dp,
                                                                themeColorAccent.copy(alpha = 0.4f),
                                                                shape = RoundedCornerShape(10.dp)
                                                            )
                                                            .padding(
                                                                horizontal = 6.dp,
                                                                vertical = 2.dp
                                                            )
                                                    ) {
                                                        Text(
                                                            text = "$progressPct% Watched",
                                                            color = themeColorAccent,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(3.dp))

                                                    // Video Title
                                                    Text(
                                                        text = history.title,
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        // Current Stopped Time / Full Duration on the bottom-right
                                        val progressVal =
                                            if (history.duration > 0) history.lastPosition.toFloat() / history.duration else 0f
                                        val formattedPos = formatDuration(history.lastPosition)
                                        val formattedDur = formatDuration(history.duration)

                                        Text(
                                            text = "$formattedPos / $formattedDur",
                                            color = Color.LightGray,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(bottom = 12.dp, end = 14.dp)
                                        )

                                        // Custom Sleek Theme Progress Track
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .align(Alignment.BottomCenter)
                                                .background(Color.Gray.copy(alpha = 0.3f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(progressVal)
                                                    .background(themeColorAccent)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. RECENTLY PLAYED HISTORY SECTION
            if (showRecentlyPlayedSection && recentlyPlayed.isNotEmpty() && searchQuery.isBlank() && selectedFolder == null) {
                item {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.recently_played),
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.clear_all),
                                color = themeColorAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { viewModel.clearPlaybackHistory() }
                                    .padding(4.dp)
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            items(recentlyPlayed) { history ->
                                val videoItem = history.toVideoItem()
                                Column(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .clickable { onVideoClick(videoItem, videos) },
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(74.dp)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(CinemaSurfaceVariant)
                                    ) {
                                        VideoThumbnail(videoItem, modifier = Modifier.fillMaxSize())
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                                .background(
                                                    Color.Black.copy(alpha = 0.7f),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                formatDuration(history.duration),
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = history.title,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatRelativeTime(history.lastPlayedTime),
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. HEADER LABEL (Show sort and grid list option above list)
            item {
                var showSortMenuInHeader by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedFolder ?: "All Videos",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("videos_header_title")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Sort Button with dropdown
                        Box {
                            Row(
                                modifier = Modifier
                                    .clickable { showSortMenuInHeader = true }
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Sort",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = "Sort Options",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenuInHeader,
                                onDismissRequest = { showSortMenuInHeader = false },
                                modifier = Modifier.background(CinemaSurface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sort by Name", color = Color.White) },
                                    trailingIcon = if (sortBy == SortBy.NAME) {
                                        {
                                            Icon(
                                                Icons.Filled.Check,
                                                null,
                                                tint = themeColorAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        viewModel.setSortBy(SortBy.NAME); showSortMenuInHeader =
                                        false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by Date Added", color = Color.White) },
                                    trailingIcon = if (sortBy == SortBy.DATE) {
                                        {
                                            Icon(
                                                Icons.Filled.Check,
                                                null,
                                                tint = themeColorAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        viewModel.setSortBy(SortBy.DATE); showSortMenuInHeader =
                                        false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by Size", color = Color.White) },
                                    trailingIcon = if (sortBy == SortBy.SIZE) {
                                        {
                                            Icon(
                                                Icons.Filled.Check,
                                                null,
                                                tint = themeColorAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        viewModel.setSortBy(SortBy.SIZE); showSortMenuInHeader =
                                        false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by Duration", color = Color.White) },
                                    trailingIcon = if (sortBy == SortBy.DURATION) {
                                        {
                                            Icon(
                                                Icons.Filled.Check,
                                                null,
                                                tint = themeColorAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        viewModel.setSortBy(SortBy.DURATION); showSortMenuInHeader =
                                        false
                                    }
                                )
                            }
                        }

                        // Vertical Divider Line
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(14.dp)
                                .background(Color.White.copy(alpha = 0.15f))
                        )

                        // Grid/List toggle icon button
                        IconButton(
                            onClick = { viewModel.setGridLayout(!isGrid) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isGrid) Icons.Filled.List else Icons.Filled.GridView,
                                contentDescription = if (isGrid) "Switch to List View" else "Switch to Grid View",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 5. VIDEOS DISPLAY LIST OR GRID
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = NeonCyan,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            } else if (videos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Info,
                                null,
                                tint = NeonCyan.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No Video Found!",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                if (isGrid) {
                    val rows = videos.chunked(2)
                    items(rows, key = { row -> row.map { it.id }.joinToString("-") }) { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (index in 0 until 2) {
                                if (index < rowItems.size) {
                                    val video = rowItems[index]
                                    Box(modifier = Modifier.weight(1f)) {
                                        GridVideoCard(
                                            video = video,
                                            onClick = { onVideoClick(video, videos) },
                                            viewModel = viewModel,
                                            onDeleteVideo = onDeleteVideo
                                        )
                                    }
                                } else {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    items(videos, key = { video -> video.id }) { video ->
                        Box(
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            ListVideoCard(
                                video = video,
                                onClick = { onVideoClick(video, videos) },
                                viewModel = viewModel,
                                onDeleteVideo = onDeleteVideo
                            )
                        }
                    }
                }
            }
        }
    }
}