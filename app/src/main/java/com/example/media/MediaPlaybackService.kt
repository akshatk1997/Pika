package com.example.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.example.MainActivity
import com.example.R
import java.net.HttpURLConnection
import java.net.URL

class MediaPlaybackService : Service() {

  companion object {
    const val CHANNEL_ID = "kio_media_stream"
    const val CHANNEL_NAME = "Media Playback"
    const val NOTIFICATION_ID = 4040

    const val ACTION_START = "com.example.media.ACTION_START"
    const val ACTION_PLAY = "com.example.media.ACTION_PLAY"
    const val ACTION_PAUSE = "com.example.media.ACTION_PAUSE"
    const val ACTION_PREV = "com.example.media.ACTION_PREV"
    const val ACTION_NEXT = "com.example.media.ACTION_NEXT"
    const val ACTION_STOP = "com.example.media.ACTION_STOP"
    const val ACTION_UPDATE_STATE = "com.example.media.ACTION_UPDATE_STATE"

    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_DOMAIN = "extra_domain"
    const val EXTRA_IS_PLAYING = "extra_is_playing"
    const val EXTRA_ARTWORK_URL = "extra_artwork_url"
    const val EXTRA_DURATION_MS = "extra_duration_ms"
    const val EXTRA_POSITION_MS = "extra_position_ms"

    @Volatile
    var isServiceRunning: Boolean = false
      private set

    @Volatile
    var mediaControlListener: ((String) -> Unit)? = null

    @Volatile
    var seekListener: ((Long) -> Unit)? = null

    fun start(
      context: Context,
      title: String,
      domain: String,
      isPlaying: Boolean,
      artworkUrl: String = "",
      durationMs: Long = 0L,
      positionMs: Long = 0L
    ) {
      val intent = Intent(context, MediaPlaybackService::class.java).apply {
        action = ACTION_UPDATE_STATE
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_DOMAIN, domain)
        putExtra(EXTRA_IS_PLAYING, isPlaying)
        if (artworkUrl.isNotBlank()) {
          putExtra(EXTRA_ARTWORK_URL, artworkUrl)
        }
        if (durationMs > 0L) {
          putExtra(EXTRA_DURATION_MS, durationMs)
          putExtra(EXTRA_POSITION_MS, positionMs.coerceAtLeast(0L))
        }
      }
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (_: Throwable) {
      }
    }

    fun stop(context: Context) {
      if (!isServiceRunning) return
      val intent = Intent(context, MediaPlaybackService::class.java).apply {
        action = ACTION_STOP
      }
      try {
        context.startService(intent)
      } catch (_: Throwable) {
      }
    }
  }

  private var currentTitle: String = "Web Stream"
  private var currentDomain: String = "Streaming"
  private var isPlaying: Boolean = true
  private var durationMs: Long = 0L
  private var positionMs: Long = 0L
  private var lastArtworkUrl: String = ""
  private var currentArtwork: Bitmap? = null
  private var mediaSession: MediaSession? = null
  private var wakeLock: PowerManager.WakeLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    isServiceRunning = true
    try {
      val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
      wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kio:media_playback")
      wakeLock?.acquire(4 * 60 * 60 * 1000L)
    } catch (_: Throwable) {
    }
    initMediaSession()
    createNotificationChannel()
    val initialNotification = buildNotification()
    startForegroundCompat(initialNotification)
  }

  private fun initMediaSession() {
    try {
      mediaSession = MediaSession(this, "KioMediaSession").apply {
        setCallback(object : MediaSession.Callback() {
          override fun onPlay() {
            isPlaying = true
            mediaControlListener?.invoke(ACTION_PLAY)
            updateMediaSessionState()
            updateNotification()
          }
          override fun onPause() {
            isPlaying = false
            mediaControlListener?.invoke(ACTION_PAUSE)
            updateMediaSessionState()
            updateNotification()
          }
          override fun onSkipToPrevious() {
            mediaControlListener?.invoke(ACTION_PREV)
          }
          override fun onSkipToNext() {
            mediaControlListener?.invoke(ACTION_NEXT)
          }
          override fun onSeekTo(pos: Long) {
            positionMs = pos.coerceAtLeast(0L)
            seekListener?.invoke(positionMs)
            updateMediaSessionState()
          }
          override fun onStop() {
            isPlaying = false
            mediaControlListener?.invoke(ACTION_STOP)
            stopForegroundCompat()
            stopSelf()
          }
        })
        isActive = true
      }
      updateMediaSessionState()
    } catch (_: Throwable) {
    }
  }

  private fun updateMediaSessionState() {
    val session = mediaSession ?: return
    try {
      val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
      val actions = PlaybackState.ACTION_PLAY or
        PlaybackState.ACTION_PAUSE or
        PlaybackState.ACTION_PLAY_PAUSE or
        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
        PlaybackState.ACTION_SKIP_TO_NEXT or
        PlaybackState.ACTION_SEEK_TO or
        PlaybackState.ACTION_STOP
      val pos = if (durationMs > 0L) positionMs else PlaybackState.PLAYBACK_POSITION_UNKNOWN
      session.setPlaybackState(
        PlaybackState.Builder()
          .setActions(actions)
          .setState(state, pos, if (isPlaying) 1.0f else 0f)
          .build()
      )
      val metaBuilder = MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, currentDomain)
      if (durationMs > 0L) {
        metaBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
      }
      currentArtwork?.let { bmp ->
        metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bmp)
        metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, bmp)
      }
      session.setMetadata(metaBuilder.build())
    } catch (_: Throwable) {
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    isServiceRunning = true
    val action = intent?.action

    if (intent != null) {
      if (intent.hasExtra(EXTRA_TITLE)) {
        currentTitle = intent.getStringExtra(EXTRA_TITLE)?.ifBlank { null } ?: currentTitle
      }
      if (intent.hasExtra(EXTRA_DOMAIN)) {
        currentDomain = intent.getStringExtra(EXTRA_DOMAIN)?.ifBlank { null } ?: currentDomain
      }
      if (intent.hasExtra(EXTRA_IS_PLAYING)) {
        isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying)
      }
      if (intent.hasExtra(EXTRA_DURATION_MS)) {
        durationMs = intent.getLongExtra(EXTRA_DURATION_MS, durationMs).coerceAtLeast(0L)
      }
      if (intent.hasExtra(EXTRA_POSITION_MS)) {
        positionMs = intent.getLongExtra(EXTRA_POSITION_MS, positionMs).coerceAtLeast(0L)
      }
      if (intent.hasExtra(EXTRA_ARTWORK_URL)) {
        val artUrl = intent.getStringExtra(EXTRA_ARTWORK_URL).orEmpty()
        if (artUrl.isNotBlank() && artUrl != lastArtworkUrl) {
          lastArtworkUrl = artUrl
          fetchArtworkAsync(artUrl)
        }
      }
    }
    updateMediaSessionState()

    when (action) {
      ACTION_PLAY -> {
        isPlaying = true
        updateMediaSessionState()
        updateNotification()
        mediaControlListener?.invoke(ACTION_PLAY)
      }
      ACTION_PAUSE -> {
        isPlaying = false
        updateMediaSessionState()
        updateNotification()
        mediaControlListener?.invoke(ACTION_PAUSE)
      }
      ACTION_PREV -> {
        positionMs = (positionMs - 10_000L).coerceAtLeast(0L)
        updateMediaSessionState()
        updateNotification()
        mediaControlListener?.invoke(ACTION_PREV)
      }
      ACTION_NEXT -> {
        positionMs = if (durationMs > 0L) (positionMs + 10_000L).coerceAtMost(durationMs) else positionMs + 10_000L
        updateMediaSessionState()
        updateNotification()
        mediaControlListener?.invoke(ACTION_NEXT)
      }
      ACTION_STOP -> {
        isPlaying = false
        mediaControlListener?.invoke(ACTION_STOP)
        stopForegroundCompat()
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_UPDATE_STATE, ACTION_START -> {
        updateNotification()
      }
    }

    return START_NOT_STICKY
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    isPlaying = false
    isServiceRunning = false
    mediaControlListener?.invoke(ACTION_STOP)
    stopForegroundCompat()
    stopSelf()
    super.onTaskRemoved(rootIntent)
  }

  override fun onDestroy() {
    isServiceRunning = false
    try {
      mediaSession?.isActive = false
      mediaSession?.release()
      mediaSession = null
    } catch (_: Throwable) {
    }
    try {
      wakeLock?.let {
        if (it.isHeld) it.release()
      }
    } catch (_: Throwable) {
    }
    super.onDestroy()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      val channel = NotificationChannel(
        CHANNEL_ID,
        CHANNEL_NAME,
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Background web media playback and notifications"
        setShowBadge(false)
        setSound(null, null)
        enableVibration(false)
      }
      notificationManager?.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(): Notification {
    val openIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val contentPendingIntent = PendingIntent.getActivity(
      this,
      0,
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun createActionPendingIntent(action: String, requestCode: Int): PendingIntent {
      val intent = Intent(this, MediaPlaybackService::class.java).apply {
        this.action = action
      }
      return PendingIntent.getService(
        this,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
    }

    val prevIntent = createActionPendingIntent(ACTION_PREV, 1)
    val playPauseIntent = createActionPendingIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY, 2)
    val nextIntent = createActionPendingIntent(ACTION_NEXT, 3)
    val stopIntent = createActionPendingIntent(ACTION_STOP, 4)

    val playPauseIcon = if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
    val playPauseText = if (isPlaying) "Pause" else "Play"
    val subtitle = if (isPlaying) currentDomain else "$currentDomain • Paused"

    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(this, CHANNEL_ID)
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(this)
    }

    builder
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(currentTitle)
      .setContentText(subtitle)
      .setContentIntent(contentPendingIntent)
      .setOngoing(isPlaying)
      .setOnlyAlertOnce(true)
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .addAction(Notification.Action.Builder(R.drawable.ic_media_prev, "Previous", prevIntent).build())
      .addAction(Notification.Action.Builder(playPauseIcon, playPauseText, playPauseIntent).build())
      .addAction(Notification.Action.Builder(R.drawable.ic_media_next, "Next", nextIntent).build())
      .addAction(Notification.Action.Builder(R.drawable.ic_media_stop, "Close", stopIntent).build())

    currentArtwork?.let { builder.setLargeIcon(it) }
    mediaSession?.let { session ->
      builder.style = Notification.MediaStyle()
        .setMediaSession(session.sessionToken)
        .setShowActionsInCompactView(0, 1, 2)
    }

    return builder.build()
  }

  private fun fetchArtworkAsync(urlString: String) {
    Thread {
      try {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
          connectTimeout = 4000
          readTimeout = 4000
          requestMethod = "GET"
          setRequestProperty("User-Agent", "Kio-Browser")
        }
        try {
          if (conn.responseCode in 200..299) {
            val raw = conn.inputStream.use { BitmapFactory.decodeStream(it) }
            if (raw != null) {
              val maxDim = 512
              val scaled = if (raw.width > maxDim || raw.height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(raw.width, raw.height)
                Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
              } else {
                raw
              }
              currentArtwork = scaled
              updateMediaSessionState()
              updateNotification()
            }
          }
        } finally {
          conn.disconnect()
        }
      } catch (_: Throwable) {
      }
    }.start()
  }

  private fun updateNotification() {
    val notification = buildNotification()
    startForegroundCompat(notification)
  }

  private fun startForegroundCompat(notification: Notification) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
    } catch (_: Throwable) {
    }
  }

  private fun stopForegroundCompat() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        stopForeground(STOP_FOREGROUND_REMOVE)
      } else {
        @Suppress("DEPRECATION")
        stopForeground(true)
      }
    } catch (_: Throwable) {
    }
  }
}
