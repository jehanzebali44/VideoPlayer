package com.example.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.data.database.FavoriteVideo
import com.example.data.database.Playlist
import com.example.data.database.PlaylistVideo
import com.example.data.database.PlaybackHistory
import com.example.data.database.VideoDao
import com.example.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class VideoRepository(
    private val context: Context,
    private val videoDao: VideoDao
) {
    // Expose DB Flows
    val favorites: Flow<List<FavoriteVideo>> = videoDao.getFavorites()
    val playlists: Flow<List<Playlist>> = videoDao.getPlaylists()
    val playbackHistory: Flow<List<PlaybackHistory>> = videoDao.getPlaybackHistory()

    suspend fun insertPlaybackHistory(video: VideoItem, position: Long) = withContext(Dispatchers.IO) {
        val history = PlaybackHistory(
            uri = video.uri.toString(),
            title = video.title,
            duration = video.duration,
            size = video.size,
            path = video.path,
            folderName = video.folderName,
            lastPosition = position,
            lastPlayedTime = System.currentTimeMillis()
        )
        videoDao.insertPlaybackHistory(history)
    }

    suspend fun getPlaybackPosition(uri: String): Long = withContext(Dispatchers.IO) {
        videoDao.getPlaybackHistoryByUri(uri)?.lastPosition ?: 0L
    }

    suspend fun deletePlaybackHistory(uri: String) = withContext(Dispatchers.IO) {
        videoDao.deletePlaybackHistoryByUri(uri)
    }

    suspend fun deleteVideoFromDatabase(video: VideoItem) = withContext(Dispatchers.IO) {
        val uriStr = video.uri.toString()
        videoDao.deleteFavoriteByUri(uriStr)
        videoDao.deletePlaybackHistoryByUri(uriStr)
        videoDao.deletePlaylistVideoByUri(uriStr)
    }

    suspend fun clearPlaybackHistory() = withContext(Dispatchers.IO) {
        videoDao.clearPlaybackHistory()
    }

    fun isFavoriteFlow(uri: String): Flow<Boolean> = videoDao.isFavoriteFlow(uri)
    suspend fun isFavorite(uri: String): Boolean = videoDao.isFavoriteState(uri)

    suspend fun toggleFavorite(video: VideoItem) = withContext(Dispatchers.IO) {
        val uriStr = video.uri.toString()
        if (videoDao.isFavoriteState(uriStr)) {
            videoDao.deleteFavoriteByUri(uriStr)
        } else {
            videoDao.addFavorite(
                FavoriteVideo(
                    uri = uriStr,
                    title = video.title,
                    duration = video.duration,
                    size = video.size,
                    path = video.path,
                    dateAdded = video.dateAdded
                )
            )
        }
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        videoDao.createPlaylist(Playlist(name = name))
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        videoDao.deletePlaylist(playlistId)
    }

    fun getPlaylistVideos(playlistId: Long): Flow<List<PlaylistVideo>> {
        return videoDao.getPlaylistVideos(playlistId)
    }

    suspend fun addVideoToPlaylist(playlistId: Long, video: VideoItem) = withContext(Dispatchers.IO) {
        videoDao.addVideoToPlaylist(
            PlaylistVideo(
                playlistId = playlistId,
                uri = video.uri.toString(),
                title = video.title,
                duration = video.duration,
                size = video.size,
                path = video.path
            )
        )
    }

    suspend fun removeVideoFromPlaylist(playlistId: Long, uri: String) = withContext(Dispatchers.IO) {
        videoDao.removeVideoFromPlaylist(playlistId, uri)
    }

    suspend fun removeVideoFromPlaylistById(id: Long) = withContext(Dispatchers.IO) {
        videoDao.removeVideoFromPlaylistById(id)
    }

    // MediaStore Query
    suspend fun fetchLocalVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val videosList = mutableListOf<VideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.RESOLUTION
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val bucketColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val resolutionColumn = cursor.getColumnIndex(MediaStore.Video.Media.RESOLUTION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Video_$id"
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val path = cursor.getString(dataColumn) ?: ""
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val folderName = if (bucketColumn != -1) {
                        cursor.getString(bucketColumn) ?: "Internal Storage"
                    } else {
                        "Internal Storage"
                    }
                    val resolution = if (resolutionColumn != -1) cursor.getString(resolutionColumn) else null

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    videosList.add(
                        VideoItem(
                            id = id,
                            uri = contentUri,
                            title = name,
                            duration = duration,
                            size = size,
                            path = path,
                            resolution = resolution,
                            dateAdded = dateAdded,
                            folderName = folderName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error querying MediaStore", e)
        }

        videosList
    }

}
