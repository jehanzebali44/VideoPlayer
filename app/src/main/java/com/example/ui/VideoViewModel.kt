package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.FavoriteVideo
import com.example.data.database.Playlist
import com.example.data.database.PlaylistVideo
import com.example.data.database.PlaybackHistory
import com.example.data.database.VideoDatabase
import com.example.data.model.VideoItem
import com.example.data.repository.VideoRepository
import com.example.player.VideoPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.Color

enum class SortBy {
    NAME, DATE, SIZE, DURATION
}

enum class ThemeColor(val displayName: String, val primary: Color, val secondary: Color) {
    GOLD("Obsidian Gold", Color(0xFFFFB300), Color(0xFFFFD54F)),
    CYAN("Neon Cyan", Color(0xFF00E5FF), Color(0xFF80DEEA)),
    PURPLE("Electric Purple", Color(0xFFD500F9), Color(0xFFE040FB)),
    MAGENTA("Rose Magenta", Color(0xFFFF4B5C), Color(0xFFFF7986)),
    GREEN("Emerald Green", Color(0xFF00E676), Color(0xFF69F0AE)),
    ORANGE("Sunset Orange", Color(0xFFFF6D00), Color(0xFFFF9E40)),
    BLUE("Royal Blue", Color(0xFF2979FF), Color(0xFF82B1FF)),
    RED("Volcano Red", Color(0xFFFF1744), Color(0xFFFF8A80)),
    TEAL("Ocean Teal", Color(0xFF00BFA5), Color(0xFF64FFDA)),
    PINK("Hot Pink", Color(0xFFF50057), Color(0xFFFF4081)),
    LIME("Lime Spark", Color(0xFFAEEA00), Color(0xFFF4FF81)),
    AMBER("Deep Amber", Color(0xFFFFAB00), Color(0xFFFFD740)),
    VIOLET("Neon Violet", Color(0xFF7C4DFF), Color(0xFFB388FF)),
    SKY("Sky Blue", Color(0xFF00B0FF), Color(0xFF80D8FF)),
    MINT("Glacial Mint", Color(0xFF1DE9B6), Color(0xFFA7FFEB))
}

class VideoViewModel(
    application: Application,
    private val repository: VideoRepository,
    val playerManager: VideoPlayerManager
) : AndroidViewModel(application) {

    // Permission state
    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    // View layout preference (Grid vs List)
    private val _isGridLayout = MutableStateFlow(false)
    val isGridLayout: StateFlow<Boolean> = _isGridLayout.asStateFlow()

    // Sort order
    private val _sortBy = MutableStateFlow(SortBy.DATE)
    val sortBy: StateFlow<SortBy> = _sortBy.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Local discovered videos
    private val _localVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val localVideos: StateFlow<List<VideoItem>> = _localVideos.asStateFlow()

    // Loading state for video scan
    private val _isLoadingVideos = MutableStateFlow(true)
    val isLoadingVideos: StateFlow<Boolean> = _isLoadingVideos.asStateFlow()

    // Active folder filter
    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    // SharedPreferences for saving user custom styles
    private val sharedPrefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _selectedThemeColor = MutableStateFlow(ThemeColor.PURPLE)
    val selectedThemeColor: StateFlow<ThemeColor> = _selectedThemeColor.asStateFlow()

    fun setThemeColor(color: ThemeColor) {
        _selectedThemeColor.value = color
        sharedPrefs.edit().putString("theme_color", color.name).apply()
        // Update top-level dynamically read properties so all Composables immediately update
        com.example.ui.theme.AppThemeState.primaryColor = color.primary
        com.example.ui.theme.AppThemeState.secondaryColor = color.secondary
    }

    // UI State for Settings
    private val _isDarkModeEnabled = MutableStateFlow(true) // Dynamic theme defaults to Dark Mode is customizable
    val isDarkModeEnabled: StateFlow<Boolean> = _isDarkModeEnabled.asStateFlow()

    private val _isDynamicColorEnabled = MutableStateFlow(true)
    val isDynamicColorEnabled: StateFlow<Boolean> = _isDynamicColorEnabled.asStateFlow()

    private val _showContinueWatching = MutableStateFlow(true)
    val showContinueWatching: StateFlow<Boolean> = _showContinueWatching.asStateFlow()

    private val _showRecentlyPlayed = MutableStateFlow(true)
    val showRecentlyPlayed: StateFlow<Boolean> = _showRecentlyPlayed.asStateFlow()

    private val _isBackgroundPlaybackMp3Enabled = MutableStateFlow(false)
    val isBackgroundPlaybackMp3Enabled: StateFlow<Boolean> = _isBackgroundPlaybackMp3Enabled.asStateFlow()

    // Room Database Flows
    val favorites: StateFlow<List<FavoriteVideo>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackHistory: StateFlow<List<PlaybackHistory>> = repository.playbackHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined Videos flowing (Purely local discovered/gallery videos)
    val filteredVideos: StateFlow<List<VideoItem>> = combine(
        _localVideos,
        _searchQuery,
        _sortBy,
        _selectedFolder
    ) { local, query, sort, folder ->
        val list = mutableListOf<VideoItem>()
        // Add local
        list.addAll(local)

        // Apply folder filter
        var filtered = if (folder != null) {
            list.filter { it.folderName.equals(folder, ignoreCase = true) }
        } else {
            list
        }

        // Apply Search query
        if (query.isNotBlank()) {
            filtered = filtered.filter { it.title.contains(query, ignoreCase = true) }
        }

        // Apply Sort order
        when (sort) {
            SortBy.NAME -> filtered.sortedBy { it.title.lowercase() }
            SortBy.DATE -> filtered.sortedByDescending { it.dateAdded }
            SortBy.SIZE -> filtered.sortedByDescending { it.size }
            SortBy.DURATION -> filtered.sortedByDescending { it.duration }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Group items by folder (Purely local discovered/gallery videos)
    val folders: StateFlow<Map<String, List<VideoItem>>> = combine(
        _localVideos,
        _searchQuery
    ) { local, _ ->
        local.groupBy { it.folderName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private var mediaObserver: android.database.ContentObserver? = null

    private fun registerContentObserver() {
        try {
            val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    android.util.Log.d("VideoViewModel", "MediaStore video modification auto-scan triggered")
                    refreshVideos()
                }
            }
            mediaObserver = observer
            getApplication<Application>().contentResolver.registerContentObserver(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            android.util.Log.e("VideoViewModel", "Error registering content observer", e)
        }
    }

    private fun unregisterContentObserver() {
        mediaObserver?.let {
            getApplication<Application>().contentResolver.unregisterContentObserver(it)
        }
    }

    init {
        // Load persistable premium themes
        val savedThemeName = sharedPrefs.getString("theme_color", ThemeColor.PURPLE.name) ?: ThemeColor.PURPLE.name
        val activeTheme = try {
            ThemeColor.valueOf(savedThemeName)
        } catch (e: Exception) {
            ThemeColor.PURPLE
        }
        _selectedThemeColor.value = activeTheme
        com.example.ui.theme.AppThemeState.primaryColor = activeTheme.primary
        com.example.ui.theme.AppThemeState.secondaryColor = activeTheme.secondary

        _showContinueWatching.value = sharedPrefs.getBoolean("show_continue_watching", true)
        _showRecentlyPlayed.value = sharedPrefs.getBoolean("show_recently_played", true)
        _isBackgroundPlaybackMp3Enabled.value = sharedPrefs.getBoolean("continue_playback_background_mp3", false)

        // Auto-register MediaStore scan updater
        registerContentObserver()
        
        // Initial loading of videos
        refreshVideos()

        // Persistent periodic automatic playback progress saver
        viewModelScope.launch {
            var lastSavedVideoUri: String? = null
            var lastSavedPosition: Long = 0L

            combine(playerManager.currentVideo, playerManager.currentPosition) { video, pos ->
                Pair(video, pos)
            }.collect { (video, pos) ->
                if (video != null) {
                    val uriStr = video.uri.toString()
                    // Save progress if the video changes, or if current progression advances >= 5 seconds
                    if (uriStr != lastSavedVideoUri || Math.abs(pos - lastSavedPosition) >= 5000) {
                        lastSavedVideoUri = uriStr
                        lastSavedPosition = pos
                        repository.insertPlaybackHistory(video, pos)
                    }
                }
            }
        }
    }

    fun playVideo(video: VideoItem, playlist: List<VideoItem> = emptyList()) {
        viewModelScope.launch {
            val lastPos = repository.getPlaybackPosition(video.uri.toString())
            playerManager.play(video, playlist)
            // Restore playback position if played > 3 seconds, and some buffer remains from total duration
            if (lastPos > 3000 && lastPos < (video.duration - 5000)) {
                playerManager.seekTo(lastPos)
            }
        }
    }

    fun deletePlaybackHistoryByUri(uri: String) {
        viewModelScope.launch {
            repository.deletePlaybackHistory(uri)
        }
    }

    fun deleteVideoFromDatabase(video: VideoItem) {
        viewModelScope.launch {
            repository.deleteVideoFromDatabase(video)
        }
    }

    fun clearPlaybackHistory() {
        viewModelScope.launch {
            repository.clearPlaybackHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        unregisterContentObserver()
    }

    fun setPermissionGranted(granted: Boolean) {
        _isPermissionGranted.value = granted
        if (granted) {
            refreshVideos()
        }
    }

    fun refreshVideos() {
        viewModelScope.launch {
            if (_isPermissionGranted.value) {
                _isLoadingVideos.value = true
                try {
                    val local = repository.fetchLocalVideos()
                    _localVideos.value = local
                } catch (e: Exception) {
                    android.util.Log.e("VideoViewModel", "Error fetching local videos: ${e.message}", e)
                } finally {
                    _isLoadingVideos.value = false
                }
            } else {
                _isLoadingVideos.value = false
            }
        }
    }

    fun removeVideoFromState(video: VideoItem) {
        val current = _localVideos.value
        _localVideos.value = current.filter { it.id != video.id && it.uri.toString() != video.uri.toString() }
    }

    fun setGridLayout(isGrid: Boolean) {
        _isGridLayout.value = isGrid
    }

    fun setSortBy(sort: SortBy) {
        _sortBy.value = sort
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFolder(folderName: String?) {
        _selectedFolder.value = folderName
    }

    fun toggleDarkMode() {
        _isDarkModeEnabled.value = !_isDarkModeEnabled.value
    }

    fun toggleDynamicColor() {
        _isDynamicColorEnabled.value = !_isDynamicColorEnabled.value
    }

    fun toggleShowContinueWatching() {
        _showContinueWatching.value = !_showContinueWatching.value
        sharedPrefs.edit().putBoolean("show_continue_watching", _showContinueWatching.value).apply()
    }

    fun toggleShowRecentlyPlayed() {
        _showRecentlyPlayed.value = !_showRecentlyPlayed.value
        sharedPrefs.edit().putBoolean("show_recently_played", _showRecentlyPlayed.value).apply()
    }

    fun toggleBackgroundPlaybackMp3() {
        _isBackgroundPlaybackMp3Enabled.value = !_isBackgroundPlaybackMp3Enabled.value
        sharedPrefs.edit().putBoolean("continue_playback_background_mp3", _isBackgroundPlaybackMp3Enabled.value).apply()
    }

    // Favorite video action
    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            repository.toggleFavorite(video)
        }
    }

    // Playlist actions
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addVideoToPlaylist(playlistId: Long, video: VideoItem) {
        viewModelScope.launch {
            repository.addVideoToPlaylist(playlistId, video)
        }
    }

    fun removeVideoFromPlaylist(playlistId: Long, uri: String) {
        viewModelScope.launch {
            repository.removeVideoFromPlaylist(playlistId, uri)
        }
    }

    fun removeVideoFromPlaylistById(id: Long) {
        viewModelScope.launch {
            repository.removeVideoFromPlaylistById(id)
        }
    }

    private val playlistFlowsCache = java.util.concurrent.ConcurrentHashMap<Long, StateFlow<List<com.example.data.database.PlaylistVideo>>>()

    fun getPlaylistVideosFlow(playlistId: Long): StateFlow<List<com.example.data.database.PlaylistVideo>> {
        return playlistFlowsCache.getOrPut(playlistId) {
            repository.getPlaylistVideos(playlistId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }
    }

    private val _navEvents = MutableStateFlow<String?>(null)
    val navEvents: StateFlow<String?> = _navEvents.asStateFlow()

    fun clearNavEvent() {
        _navEvents.value = null
    }

    fun playExternalVideo(uri: android.net.Uri) {
        viewModelScope.launch {
            val videoItem = createVideoItemFromUri(getApplication(), uri)
            playVideo(videoItem)
            _navEvents.value = "player"
        }
    }

    fun navigateToPlayerScreen() {
        if (playerManager.currentVideo.value != null) {
            playerManager.setMp3Mode(false)
            _navEvents.value = "player"
        }
    }

    private fun createVideoItemFromUri(context: Context, uri: android.net.Uri): VideoItem {
        var title = "External Video"
        var size = 0L
        var duration = 0L
        val path = uri.toString()
        
        try {
            if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val name = it.getString(nameIndex)
                            if (!name.isNullOrEmpty()) {
                                title = name
                            }
                        }
                        val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIndex != -1) {
                            size = it.getLong(sizeIndex)
                        }
                    }
                }
            } else if (uri.scheme == "file") {
                val file = java.io.File(uri.path ?: "")
                if (file.exists()) {
                    title = file.name
                    size = file.length()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoViewModel", "Error querying uri metadata", e)
        }

        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration = durationStr?.toLongOrNull() ?: 0L
            retriever.release()
        } catch (e: Exception) {
            android.util.Log.e("VideoViewModel", "Error getting duration from uri", e)
        }

        return VideoItem(
            id = -1L,
            uri = uri,
            title = title,
            duration = duration,
            size = size,
            path = path,
            resolution = null,
            dateAdded = System.currentTimeMillis() / 1000,
            folderName = "External"
        )
    }
}

class VideoViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VideoViewModel::class.java)) {
            val app = context.applicationContext as Application
            val database = VideoDatabase.getDatabase(app)
            val repository = VideoRepository(app, database.videoDao())
            val playerManager = VideoPlayerManager.getInstance(app)
            @Suppress("UNCHECKED_CAST")
            return VideoViewModel(app, repository, playerManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
