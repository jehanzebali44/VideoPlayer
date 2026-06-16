package com.example.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class VideoPlayerManager private constructor(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var forwardingPlayer: androidx.media3.common.ForwardingPlayer? = null
    private var mediaSession: androidx.media3.session.MediaSession? = null
    private var equalizer: android.media.audiofx.Equalizer? = null
    private val currentBandValues = mutableMapOf<Int, Float>()
    
    enum class RepeatMode { OFF, ONE }

    // Core state flows
    private val _currentVideo = MutableStateFlow<VideoItem?>(null)
    val currentVideo: StateFlow<VideoItem?> = _currentVideo.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isMp3Mode = MutableStateFlow(false)
    val isMp3Mode: StateFlow<Boolean> = _isMp3Mode.asStateFlow()

    private val _isMiniPlayerActive = MutableStateFlow(false)
    val isMiniPlayerActive: StateFlow<Boolean> = _isMiniPlayerActive.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // PICTURE IN PICTURE STATEFLOW
    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    // REPEAT & SHUFFLE STATEFLOWS
    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    // SLEEP TIMER STATEFLOWS
    private var sleepTimerJob: Job? = null
    private val _sleepTimeRemaining = MutableStateFlow<Long?>(null) // in seconds
    val sleepTimeRemaining: StateFlow<Long?> = _sleepTimeRemaining.asStateFlow()

    // RESUME POSITION CACHE
    private val resumePositions = mutableMapOf<String, Long>()

    // REMEMBER BRIGHTNESS/VOLUME PER SESSION VARIABLES
    var sessionVolume: Float? = null
    var sessionBrightness: Float? = null

    // Playlist management inside player
    private val playlistQueue = mutableListOf<VideoItem>()
    private var currentQueueIndex = -1

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        if (exoPlayer == null) {
            val player = ExoPlayer.Builder(context.applicationContext).build()
            player.setRepeatMode(if (_repeatMode.value == RepeatMode.ONE) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            })
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) {
                        startProgressTracker()
                    } else {
                        stopProgressTracker()
                    }
                    updateBackgroundService()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _isLoading.value = (playbackState == Player.STATE_BUFFERING)
                    when (playbackState) {
                        Player.STATE_READY -> {
                            _duration.value = player.getDuration()
                            _playbackSpeed.value = player.getPlaybackParameters().speed
                        }
                        Player.STATE_ENDED -> {
                            handleVideoEnded()
                        }
                        else -> {}
                    }
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    setupEqualizer(audioSessionId)
                }
            })
            exoPlayer = player

            val fPlayer = object : androidx.media3.common.ForwardingPlayer(player) {
                override fun seekToNext() {
                    playNext()
                }

                override fun seekToNextMediaItem() {
                    playNext()
                }

                override fun seekToPrevious() {
                    playPrevious()
                }

                override fun seekToPreviousMediaItem() {
                    playPrevious()
                }

                override fun isCommandAvailable(command: Int): Boolean {
                    return when (command) {
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                        else -> super.isCommandAvailable(command)
                    }
                }

                override fun getAvailableCommands(): Player.Commands {
                    return super.getAvailableCommands().buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()
                }
            }
            forwardingPlayer = fPlayer

            try {
                mediaSession = androidx.media3.session.MediaSession.Builder(context.applicationContext, fPlayer).build()
            } catch (e: Exception) {
                Log.e("VideoPlayerManager", "Failed to build mediaSession", e)
            }
        }
    }

    fun getPlayerInstance(): Player {
        initializePlayer()
        return forwardingPlayer ?: exoPlayer!!
    }

    fun getMediaSession(): androidx.media3.session.MediaSession? {
        initializePlayer()
        return mediaSession
    }

    fun setInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        updateExoPlayerRepeatMode()
    }

    private fun updateExoPlayerRepeatMode() {
        exoPlayer?.repeatMode = if (_repeatMode.value == RepeatMode.ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        sleepTimerJob = scope.launch {
            var seconds = minutes * 60L
            while (seconds > 0) {
                _sleepTimeRemaining.value = seconds
                delay(1000)
                seconds--
            }
            _sleepTimeRemaining.value = null
            pause()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimeRemaining.value = null
    }

    fun savePosition(videoUri: String, positionMs: Long) {
        resumePositions[videoUri] = positionMs
    }

    fun getSavedPosition(videoUri: String): Long {
        return resumePositions[videoUri] ?: 0L
    }

    fun play(video: VideoItem, playlist: List<VideoItem> = emptyList()) {
        initializePlayer()
        
        // Setup playlist if provided safely (handle case when passing playlistQueue to self)
        if (playlist.isNotEmpty()) {
            if (playlist !== playlistQueue) {
                playlistQueue.clear()
                playlistQueue.addAll(playlist)
            }
            currentQueueIndex = playlistQueue.indexOfFirst { it.uri == video.uri }
            if (currentQueueIndex == -1) {
                playlistQueue.add(0, video)
                currentQueueIndex = 0
            }
        } else {
            playlistQueue.clear()
            playlistQueue.add(video)
            currentQueueIndex = 0
        }

        _currentVideo.value = video
        _isMiniPlayerActive.value = false // Expand when playing a brand new video
        _isLoading.value = true

        val mediaItem = MediaItem.fromUri(video.uri)
        val savedPos = getSavedPosition(video.uri.toString())

        exoPlayer?.apply {
            setMediaItem(mediaItem)
            if (savedPos > 3000L && savedPos < (video.duration - 5000L)) {
                seekTo(savedPos)
            } else {
                seekTo(0)
            }
            prepare()
            setPlaybackSpeed(_playbackSpeed.value)
            updateExoPlayerRepeatMode()
            play()
            
            val sessId = audioSessionId
            if (sessId != 0 && sessId != -1) {
                setupEqualizer(sessId)
            }
        }
        updateBackgroundService()
        Log.d("VideoPlayerManager", "Playing video: ${video.title}, isMp3Mode: ${_isMp3Mode.value}, resumeAt: $savedPos")
    }

    fun resume() {
        exoPlayer?.let { player ->
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
                player.prepare()
            }
            player.play()
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _currentPosition.value = positionMs
        _currentVideo.value?.let { video ->
            savePosition(video.uri.toString(), positionMs)
        }
    }

    fun skipForward() {
        exoPlayer?.let {
            val target = it.currentPosition + 10000L // 10s seek
            val finalPos = target.coerceAtMost(it.duration)
            seekTo(finalPos)
        }
    }

    fun skipBackward() {
        exoPlayer?.let {
            val target = it.currentPosition - 10000L // 10s back
            val finalPos = target.coerceAtLeast(0L)
            seekTo(finalPos)
        }
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun toggleMp3Mode() {
        _isMp3Mode.value = !_isMp3Mode.value
        Log.d("VideoPlayerManager", "MP3 Mode Toggled: ${_isMp3Mode.value}")
        updateBackgroundService()
    }

    fun setMp3Mode(enabled: Boolean) {
        if (_isMp3Mode.value != enabled) {
            _isMp3Mode.value = enabled
            Log.d("VideoPlayerManager", "MP3 Mode Set programmatically: $enabled")
            updateBackgroundService()
        }
    }

    fun setMiniPlayerActive(active: Boolean) {
        _isMiniPlayerActive.value = active
    }

    fun playNext() {
        if (playlistQueue.isEmpty()) return
        if (_isShuffleEnabled.value) {
            currentQueueIndex = (0 until playlistQueue.size).random()
            val nextVideo = playlistQueue[currentQueueIndex]
            play(nextVideo, playlistQueue)
        } else if (currentQueueIndex < playlistQueue.size - 1) {
            currentQueueIndex++
            val nextVideo = playlistQueue[currentQueueIndex]
            play(nextVideo, playlistQueue)
        }
    }

    fun playPrevious() {
        if (playlistQueue.isEmpty()) return
        if (_isShuffleEnabled.value) {
            currentQueueIndex = (0 until playlistQueue.size).random()
            val prevVideo = playlistQueue[currentQueueIndex]
            play(prevVideo, playlistQueue)
        } else if (currentQueueIndex > 0) {
            currentQueueIndex--
            val prevVideo = playlistQueue[currentQueueIndex]
            play(prevVideo, playlistQueue)
        }
    }

    fun hasNext(): Boolean {
        return playlistQueue.isNotEmpty() && currentQueueIndex < playlistQueue.size - 1
    }

    fun hasPrevious(): Boolean {
        return playlistQueue.isNotEmpty() && currentQueueIndex > 0
    }

    private fun handleVideoEnded() {
        _currentVideo.value?.let { video ->
            savePosition(video.uri.toString(), 0L)
        }
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                _currentVideo.value?.let { play(it, playlistQueue) }
            }
            else -> {
                if (hasNext()) {
                    playNext()
                } else {
                    _isPlaying.value = false
                    stopProgressTracker()
                }
            }
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    _currentPosition.value = player.currentPosition
                    _currentVideo.value?.let { video ->
                        if (player.duration > 0 && player.currentPosition < player.duration - 2000L) {
                            savePosition(video.uri.toString(), player.currentPosition)
                        } else {
                            savePosition(video.uri.toString(), 0L)
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun stopAndDismiss() {
        stopProgressTracker()
        exoPlayer?.apply {
            playWhenReady = false
            stop()
            clearMediaItems()
        }
        _currentVideo.value = null
        _isPlaying.value = false
        _isMiniPlayerActive.value = false
        updateBackgroundService()
    }

    private fun setupEqualizer(audioSessionId: Int) {
        if (audioSessionId == 0) return
        try {
            equalizer?.release()
            equalizer = android.media.audiofx.Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            applyEqualizerBands()
        } catch (e: Exception) {
            Log.e("VideoPlayerManager", "Failed to init Equalizer with audioSessionId $audioSessionId", e)
        }
    }

    fun setEqualizerBands(bandValues: Map<Int, Float>) {
        currentBandValues.clear()
        currentBandValues.putAll(bandValues)
        applyEqualizerBands()
    }

    private fun applyEqualizerBands() {
        if (equalizer == null) {
            val sessionId = exoPlayer?.audioSessionId ?: 0
            if (sessionId != 0 && sessionId != -1) {
                try {
                    equalizer?.release()
                    equalizer = android.media.audiofx.Equalizer(0, sessionId).apply {
                        enabled = true
                    }
                } catch (e: Exception) {
                    Log.e("VideoPlayerManager", "Lazy Equalizer init failed", e)
                }
            }
        }
        val eq = equalizer ?: return
        try {
            if (!eq.enabled) {
                eq.enabled = true
            }
            val numBands = eq.numberOfBands.toInt()
            val sliderValues = listOf(
                currentBandValues[60] ?: 0f,
                currentBandValues[230] ?: 0f,
                currentBandValues[910] ?: 0f,
                currentBandValues[4000] ?: 0f,
                currentBandValues[14000] ?: 0f
            )
            val range = eq.bandLevelRange
            val minLevel = range?.getOrNull(0) ?: -1500
            val maxLevel = range?.getOrNull(1) ?: 1500
            for (i in 0 until numBands) {
                val progress = sliderValues.getOrNull(i) ?: 0f
                val level = (progress * 100).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt())
                eq.setBandLevel(i.toShort(), level.toShort())
            }
        } catch (e: Exception) {
            Log.e("VideoPlayerManager", "Failed to apply equalizer bands", e)
        }
    }

    fun release() {
        stopProgressTracker()
        try {
            equalizer?.release()
            equalizer = null
        } catch (e: Exception) {}
        exoPlayer?.release()
        exoPlayer = null
        _currentVideo.value = null
        _isPlaying.value = false
    }

    private fun updateBackgroundService() {
        val video = _currentVideo.value
        val isMp3 = _isMp3Mode.value

        if (video != null && isMp3) {
            val serviceIntent = Intent(context, AudioPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            val serviceIntent = Intent(context, AudioPlaybackService::class.java)
            context.stopService(serviceIntent)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: VideoPlayerManager? = null

        fun getInstance(context: Context): VideoPlayerManager {
            return INSTANCE ?: synchronized(this) {
                val instance = VideoPlayerManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
