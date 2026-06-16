package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_videos")
data class FavoriteVideo(
    @PrimaryKey val uri: String,
    val title: String,
    val duration: Long,
    val size: Long,
    val path: String,
    val dateAdded: Long
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_videos")
data class PlaylistVideo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val uri: String,
    val title: String,
    val duration: Long,
    val size: Long,
    val path: String
)

@Entity(tableName = "playback_history")
data class PlaybackHistory(
    @PrimaryKey val uri: String,
    val title: String,
    val duration: Long,
    val size: Long,
    val path: String,
    val folderName: String,
    val lastPosition: Long,
    val lastPlayedTime: Long
)
