package com.example.ui.screens.home.components.bottom_nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R


// CUSTOM BOTTOM NAVIGATION COMPOSABLE
@Composable
fun CustomBottomNavigation(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    themeColorAccent: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color.Transparent)
    ) {
        // The Bottom Bar Background with rounded top corners
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = Color.Black,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
        )

        // Symmetrical Flat Four-Tab Row
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomBottomNavItem(
                label = stringResource(R.string.my_library),
                icon = if (activeTab == 0) Icons.Filled.PlayArrow else Icons.Outlined.PlayArrow,
                isSelected = activeTab == 0,
                onClick = { onTabSelected(0) },
                themeColorAccent = themeColorAccent,
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_videos")
            )
            CustomBottomNavItem(
                label = stringResource(R.string.playlists_tab),
                icon = if (activeTab == 1) Icons.Filled.Folder else Icons.Outlined.Folder,
                isSelected = activeTab == 1,
                onClick = { onTabSelected(1) },
                themeColorAccent = themeColorAccent,
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_playlists")
            )
            CustomBottomNavItem(
                label = stringResource(R.string.favorites_tab),
                icon = if (activeTab == 2) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                isSelected = activeTab == 2,
                onClick = { onTabSelected(2) },
                themeColorAccent = themeColorAccent,
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_favorites")
            )
            CustomBottomNavItem(
                label = stringResource(R.string.settings_tab),
                icon = if (activeTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings,
                isSelected = activeTab == 3,
                onClick = { onTabSelected(3) },
                themeColorAccent = themeColorAccent,
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_settings")
            )
        }
    }
}