package com.woody.booklauncher

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent

/** What the active audio app is playing right now. */
data class NowPlaying(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val canPlay: Boolean,
    val canSkip: Boolean,
) {
    /** 0..1, or -1 when the app does not report a duration. */
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else -1f
}

/**
 * Reads and controls the active media session.
 *
 * `AudioManager.dispatchMediaKeyEvent` only works when the audio app owns an
 * active media session, and it cannot tell several sessions apart. With the
 * notification-listener access granted by the user, [MediaWatchService] lets us
 * list the real sessions and call the one that is playing directly. That is what
 * makes audiobook and TTS controls work while another app also holds audio focus.
 *
 * Without that access we fall back to media keys, so the buttons still work for
 * ordinary music players.
 */
object MediaNow {
    /** Notification access is needed before the system returns active sessions. */
    fun hasAccess(context: Context): Boolean = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.isNotificationListenerAccessGranted(ComponentName(context, MediaWatchService::class.java))
    }.getOrDefault(false)

    fun requestAccess(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** The session to control: a playing one first, then the most recently active. */
    private fun controller(context: Context): MediaController? {
        if (!hasAccess(context)) return null
        return runCatching {
            val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
            val sessions = manager.getActiveSessions(ComponentName(context, MediaWatchService::class.java))
            sessions
                .filter { it.playbackState != null || it.metadata != null }
                .sortedByDescending { session ->
                    val state = session.playbackState
                    val playing = state != null && state.state == PlaybackState.STATE_PLAYING
                    (if (playing) 2 else 0) + (if (state != null && state.actions != 0L) 1 else 0)
                }
                .firstOrNull()
        }.getOrNull()
    }

    fun info(context: Context): NowPlaying? {
        val c = controller(context) ?: return null
        val metadata = c.metadata
        val state = c.playbackState
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            .ifEmpty { metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty() }
        if (title.isEmpty() && artist.isEmpty() && state == null) return null
        val actions = state?.actions ?: 0L
        val position = runCatching {
            if (state?.state == PlaybackState.STATE_PLAYING) {
                state.position + ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
            } else state?.position ?: 0L
        }.getOrDefault(0L)
        return NowPlaying(
            packageName = c.packageName,
            appLabel = appLabel(context, c.packageName),
            title = title,
            artist = artist,
            playing = state?.state == PlaybackState.STATE_PLAYING,
            positionMs = position.coerceAtLeast(0L),
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            canPlay = actions == 0L || actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE) != 0L,
            canSkip = actions == 0L || actions and (PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L,
        )
    }

    fun isPlaying(context: Context): Boolean = info(context)?.playing == true

    fun toggle(context: Context) {
        val c = controller(context)
        if (c != null && supports(c, PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)) {
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            runCatching { if (playing) c.transportControls.pause() else c.transportControls.play() }
                .onSuccess { return }
        }
        mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun next(context: Context) {
        val c = controller(context)
        if (c != null && supports(c, PlaybackState.ACTION_SKIP_TO_NEXT) &&
            runCatching { c.transportControls.skipToNext() }.isSuccess) return
        mediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous(context: Context) {
        val c = controller(context)
        if (c != null && supports(c, PlaybackState.ACTION_SKIP_TO_PREVIOUS) &&
            runCatching { c.transportControls.skipToPrevious() }.isSuccess) return
        mediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    /** Skip [deltaMs] inside the current track (positive = forward). */
    fun seek(context: Context, deltaMs: Long) {
        val key = if (deltaMs >= 0) KeyEvent.KEYCODE_MEDIA_FAST_FORWARD else KeyEvent.KEYCODE_MEDIA_REWIND
        val c = controller(context)?.takeIf { supports(it, PlaybackState.ACTION_SEEK_TO) } ?: return mediaKey(context, key)
        val state = c.playbackState
        val base = state?.position ?: 0L
        val duration = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val to = (base + deltaMs).coerceAtLeast(0L).let { if (duration > 0) it.coerceAtMost(duration) else it }
        if (runCatching { c.transportControls.seekTo(to) }.isFailure) mediaKey(context, key)
    }

    /** A session with no reported actions is treated as capable (some older apps omit them). */
    private fun supports(c: MediaController, actions: Long): Boolean {
        val reported = c.playbackState?.actions ?: return true
        return reported == 0L || reported and actions != 0L
    }

    @Suppress("DEPRECATION")
    private fun mediaKey(context: Context, code: Int) {
        runCatching {
            val audio = context.getSystemService(AudioManager::class.java) ?: return
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    private fun appLabel(context: Context, pkg: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(pkg)
}

/** App summary in the media card: `Libby · Chapter 4` / `Paused` / `Ready`. */
fun NowPlaying.subtitle(): String = when {
    title.isNotEmpty() && artist.isNotEmpty() -> "$title · $artist"
    title.isNotEmpty() -> title
    artist.isNotEmpty() -> artist
    playing -> "Playing"
    else -> "Ready"
}

fun NowPlaying.minuteText(): String {
    if (durationMs <= 0) return ""
    fun fmt(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }
    return "${fmt(positionMs)} / ${fmt(durationMs)}"
}
