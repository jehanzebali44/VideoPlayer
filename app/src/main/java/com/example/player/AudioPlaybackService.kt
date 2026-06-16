package com.example.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AudioPlaybackService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var mediaSession: androidx.media3.session.MediaSession? = null

    companion object {
        const val CHANNEL_ID = "audio_playback_channel"
        const val NOTIFICATION_ID = 40401

        const val ACTION_PLAY = "com.example.player.PLAY"
        const val ACTION_PAUSE = "com.example.player.PAUSE"
        const val ACTION_STOP = "com.example.player.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val playerManager = VideoPlayerManager.getInstance(applicationContext)
        mediaSession = playerManager.getMediaSession()

        // Observe player states to dynamically update notification or stop if needed
        serviceScope.launch {
            playerManager.currentVideo.collect { video ->
                if (video == null) {
                    stopSelf()
                } else {
                    updateNotification()
                }
            }
        }

        serviceScope.launch {
            playerManager.isPlaying.collect {
                updateNotification()
            }
        }

        serviceScope.launch {
            playerManager.isMp3Mode.collect { isMp3 ->
                if (!isMp3) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val playerManager = VideoPlayerManager.getInstance(applicationContext)

        when (action) {
            ACTION_PLAY -> playerManager.resume()
            ACTION_PAUSE -> playerManager.pause()
            ACTION_STOP -> {
                playerManager.stopAndDismiss()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        updateNotification()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for background audio playback of videos"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val playerManager = VideoPlayerManager.getInstance(applicationContext)
        val currentVideo = playerManager.currentVideo.value
        if (currentVideo == null) {
            stopSelf()
            return
        }
        val isPlaying = playerManager.isPlaying.value

        // Open app main activity on click and navigate to active video player
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            action = "com.example.player.OPEN_PLAYER"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Play/Pause intent
        val playPauseActionIntent = if (isPlaying) {
            Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_PAUSE }
        } else {
            Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_PLAY }
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            1,
            playPauseActionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Stop intent
        val stopActionIntent = Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopActionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseText = if (isPlaying) "Pause" else "Play"

        // Load active theme accent color from SharedPreferences
        val sharedPrefs = getSharedPreferences("video_player_prefs", Context.MODE_PRIVATE)
        val savedThemeName = sharedPrefs.getString("theme_color", "PURPLE") ?: "PURPLE"
        val themeColorInt = when (savedThemeName) {
            "GOLD" -> 0xFFFFB300.toInt()
            "CYAN" -> 0xFF00E5FF.toInt()
            "PURPLE" -> 0xFFD500F9.toInt()
            "MAGENTA" -> 0xFFFF4B5C.toInt()
            "GREEN" -> 0xFF00E676.toInt()
            "ORANGE" -> 0xFFFF6D00.toInt()
            "BLUE" -> 0xFF2979FF.toInt()
            "RED" -> 0xFFFF1744.toInt()
            "TEAL" -> 0xFF00BFA5.toInt()
            "PINK" -> 0xFFF50057.toInt()
            else -> 0xFFD500F9.toInt()
        }

        // Dynamically load high-quality video thumbnail bitmap as notification backdrop
        val thumbnailBitmap = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(currentVideo.uri, android.util.Size(120, 120), null)
            } else {
                android.media.ThumbnailUtils.createVideoThumbnail(
                    currentVideo.path,
                    android.provider.MediaStore.Images.Thumbnails.MINI_KIND
                )
            }
        } catch (e: Exception) {
            null
        }

        // Elegant fallback using customized branded icon drawable
        val largeIconBitmap = thumbnailBitmap ?: try {
            val drawable = ContextCompat.getDrawable(this, R.mipmap.ic_launcher)
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val b = Bitmap.createBitmap(
                    drawable?.intrinsicWidth?.coerceAtLeast(1) ?: 100,
                    drawable?.intrinsicHeight?.coerceAtLeast(1) ?: 100,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(b)
                drawable?.setBounds(0, 0, canvas.width, canvas.height)
                drawable?.draw(canvas)
                b
            }
        } catch (e: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentVideo.title)
            .setContentText("Playing Backstage • Audio Mode")
            .setSubText("MP3 Background Playback")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(largeIconBitmap)
            .setColor(themeColorInt)
            .setColorized(true)
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .addAction(playPauseIcon, playPauseText, playPausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPendingIntent)

        mediaSession?.let { session ->
            try {
                builder.setStyle(
                    androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(session)
                        .setShowActionsInCompactView(0)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val notification = builder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession = null
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
