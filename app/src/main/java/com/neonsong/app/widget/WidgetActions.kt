package com.neonsong.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.provider.Settings
import com.neonsong.app.MainActivity
import kotlinx.coroutines.delay

object WidgetActions {
    const val ACTION_TOGGLE = "com.neonsong.app.widget.action.TOGGLE"
    const val ACTION_NEXT = "com.neonsong.app.widget.action.NEXT"
    const val ACTION_PREV = "com.neonsong.app.widget.action.PREV"

    private const val REQ_TOGGLE = 101
    private const val REQ_NEXT = 102
    private const val REQ_PREV = 103
    private const val REQ_OPEN_APP = 104
    private const val REQ_OPEN_ACCESS = 105

    private fun flags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun scopedRequestCode(base: Int, receiver: Class<*>): Int =
        (base * 31) xor receiver.name.hashCode()

    fun togglePending(context: Context, receiver: Class<*>): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            scopedRequestCode(REQ_TOGGLE, receiver),
            Intent(context, receiver).setAction(ACTION_TOGGLE),
            flags(),
        )

    fun nextPending(context: Context, receiver: Class<*>): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            scopedRequestCode(REQ_NEXT, receiver),
            Intent(context, receiver).setAction(ACTION_NEXT),
            flags(),
        )

    fun prevPending(context: Context, receiver: Class<*>): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            scopedRequestCode(REQ_PREV, receiver),
            Intent(context, receiver).setAction(ACTION_PREV),
            flags(),
        )

    fun openAppPending(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQ_OPEN_APP,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags(),
        )

    fun openAccessPending(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQ_OPEN_ACCESS,
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags(),
        )

    internal fun resolveController(context: Context): MediaController? {
        val held = ActiveMediaSessionHolder.controller
        held?.let {
            val state = runCatching { it.playbackState?.state }.getOrNull()
            if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) return it
        }
        return held ?: ActiveMediaSessionHolder.ownToken?.let { token ->
            runCatching { MediaController(context, token) }.getOrNull()
        }
    }

    suspend fun performToggle(context: Context) {
        val controller = resolveController(context) ?: return
        val currentState = runCatching { controller.playbackState?.state }.getOrNull()
        val wasPlaying = currentState == PlaybackState.STATE_PLAYING ||
            currentState == PlaybackState.STATE_BUFFERING
        val succeeded = runCatching {
            if (wasPlaying) controller.transportControls.pause()
            else controller.transportControls.play()
        }.isSuccess
        if (!succeeded) return
        WidgetUpdater.setPlaying(context, !wasPlaying)
        delay(300)
        val confirmed = runCatching { controller.playbackState?.state }.getOrNull()
        val confirmedPlaying = when (confirmed) {
            PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING -> true
            PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED, PlaybackState.STATE_NONE -> false
            else -> null
        } ?: !wasPlaying
        WidgetUpdater.setPlaying(context, confirmedPlaying)
    }

    suspend fun performSkip(context: Context, next: Boolean) {
        val controller = resolveController(context) ?: return
        runCatching {
            if (next) controller.transportControls.skipToNext()
            else controller.transportControls.skipToPrevious()
        }
    }
}
