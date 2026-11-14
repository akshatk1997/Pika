package com.example.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class MediaPlaybackService : Service() {

  companion object {
    const val CHANNEL_ID = "kai_media_stream"
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

    @Volatile
    var isServiceRunning: Boolean = false
      private set

    @Volatile
    var mediaControlListener: ((String) -> Unit)? = null

    fun start(context: Context, title: String, domain: String, isPlaying: Boolean) {
      val intent = Intent(context, MediaPlaybackService::class.java).apply {
        action = ACTION_UPDATE_STATE
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_DOMAIN, domain)
        putExtra(EXTRA_IS_PLAYING, isPlaying)
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
  private var wakeLock: PowerManager.WakeLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    isServiceRunning = true
    try {
      val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
      wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kai:media_playback")
      wakeLock?.acquire(4 * 60 * 60 * 1000L)
    } catch (_: Throwable) {
    }
    createNotificationChannel()
    val initialNotification = buildNotification()
    startForegroundCompat(initialNotification)
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
    }

    when (action) {
      ACTION_PLAY -> {
        isPlaying = true
        updateNotification()
        mediaControlListener?.invoke(ACTION_PLAY)
      }
      ACTION_PAUSE -> {
        isPlaying = false
        updateNotification()
        mediaControlListener?.invoke(ACTION_PAUSE)
      }
      ACTION_PREV -> {
        mediaControlListener?.invoke(ACTION_PREV)
      }
      ACTION_NEXT -> {
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

  override fun onDestroy() {
    isServiceRunning = false
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
    val subtitle = if (isPlaying) "$currentDomain • Playing" else "$currentDomain • Paused"

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(currentTitle)
      .setContentText(subtitle)
      .setContentIntent(contentPendingIntent)
      .setOngoing(isPlaying)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .addAction(R.drawable.ic_media_prev, "-10s", prevIntent)
      .addAction(playPauseIcon, playPauseText, playPauseIntent)
      .addAction(R.drawable.ic_media_next, "+10s", nextIntent)
      .addAction(R.drawable.ic_media_stop, "Stop", stopIntent)
      .build()
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
