package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    // Favorites
    @Query("SELECT * FROM favorite_videos ORDER BY dateAdded DESC")
    fun getFavorites(): Flow<List<FavoriteVideo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(video: FavoriteVideo)

    @Delete
    suspend fun removeFavorite(video: FavoriteVideo)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_videos WHERE uri = :uri)")
    suspend fun isFavoriteState(uri: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_videos WHERE uri = :uri)")
    fun isFavoriteFlow(uri: String): Flow<Boolean>

    @Query("DELETE FROM favorite_videos WHERE uri = :uri")
    suspend fun deleteFavoriteByUri(uri: String)

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    // Playlist Videos
    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY id ASC")
    fun getPlaylistVideos(playlistId: Long): Flow<List<PlaylistVideo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addVideoToPlaylist(video: PlaylistVideo)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND uri = :uri")
    suspend fun removeVideoFromPlaylist(playlistId: Long, uri: String)

    @Query("DELETE FROM playlist_videos WHERE id = :id")
    suspend fun removeVideoFromPlaylistById(id: Long)

    @Query("DELETE FROM playlist_videos WHERE uri = :uri")
    suspend fun deletePlaylistVideoByUri(uri: String)

    // Playback History & Continue Watching Operations
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedTime DESC")
    fun getPlaybackHistory(): Flow<List<PlaybackHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackHistory(history: PlaybackHistory)

    @Query("DELETE FROM playback_history WHERE uri = :uri")
    suspend fun deletePlaybackHistoryByUri(uri: String)

    @Query("SELECT * FROM playback_history WHERE uri = :uri LIMIT 1")
    suspend fun getPlaybackHistoryByUri(uri: String): PlaybackHistory?

    @Query("DELETE FROM playback_history")
    suspend fun clearPlaybackHistory()
}
