package com.example.data.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import com.example.R
import com.example.data.database.PlaybackHistory
import com.example.data.model.VideoItem

object VideoUtils {

    // FORMAT TIME UTILITY
    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val sec = ms / 1000
        val hrs = sec / 3600
        val mins = (sec % 3600) / 60
        val secs = sec % 60
        return if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }


    // FORMAT RELATIVE TIME UTILITY
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        if (diff < 0) return "Just now"
        val minutes = diff / 60000
        if (minutes < 1) return "Just now"
        if (minutes < 60) return "Played ${minutes}m ago"
        val hours = minutes / 60
        if (hours < 24) return "Played ${hours}h ago"
        val days = hours / 24
        if (days < 30) return "Played ${days}d ago"
        return "Played some time ago"
    }

    fun getRelativeTimeAgo(context: Context, dateSeconds: Long): String {
        val now = System.currentTimeMillis() / 1000
        val diff = now - dateSeconds
        if (diff < 0) return context.getString(R.string.time_just_now)
        return when {
            diff < 60 -> context.getString(R.string.time_just_now)
            diff < 3600 -> context.getString(R.string.time_min_ago)
                .replace("{count}", (diff / 60).toString())

            diff < 86400 -> context.getString(R.string.time_h_ago)
                .replace("{count}", (diff / 3600).toString())

            diff < 2592000 -> context.getString(R.string.time_d_ago)
                .replace("{count}", (diff / 86400).toString())

            diff < 31536000 -> context.getString(R.string.time_mo_ago)
                .replace("{count}", (diff / 2592000).toString())

            else -> context.getString(R.string.time_y_ago)
                .replace("{count}", (diff / 31536000).toString())
        }
    }

    @Composable
    fun getFolderIcon(folderName: String): androidx.compose.ui.graphics.vector.ImageVector {
        val name = folderName.lowercase()
        return when {
            name == "all" -> Icons.Filled.GridView
            name.contains("camera") -> Icons.Filled.Camera
            name.contains("whatsapp") || name.contains("chat") -> Icons.Filled.Forum
            name.contains("download") -> Icons.Filled.ArrowDownward
            else -> Icons.Filled.Folder
        }
    }

    fun shareVideo(context: Context, video: VideoItem) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, video.uri)
                putExtra(Intent.EXTRA_SUBJECT, video.title)
                putExtra(Intent.EXTRA_TEXT, "Check out this video: ${video.title}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Video"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing video", Toast.LENGTH_SHORT).show()
        }
    }
}

// PLAYBACK HISTORY EXPANSION CONVERTOR
fun PlaybackHistory.toVideoItem(): VideoItem {
    return VideoItem(
        id = this.uri.hashCode().toLong(),
        uri = this.uri.toUri(),
        title = this.title,
        duration = this.duration,
        size = this.size,
        path = this.path,
        resolution = null,
        dateAdded = this.lastPlayedTime / 1000,
        folderName = this.folderName
    )
}