package com.neonsong.app.ui.player

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.PlayableTrack
import kotlin.math.abs

/**
 * Plays the selected music video through YouTube's adaptive IFrame player while
 * keeping the app's audio player as the master clock. Large sync corrections
 * are rate-limited so normal clock drift does not keep flushing the decoder.
 */
@Composable
fun SyncedVideoSurface(
    track: PlayableTrack,
    musicPlayer: MusicPlayer,
    modifier: Modifier = Modifier,
    videoId: String? = track.videoId,
) {
    val playerState by musicPlayer.state.collectAsStateWithLifecycle()
    val selectedVideoId = videoId?.takeIf(String::isNotBlank).orEmpty()
    var seekCommandSeconds by remember(track.title, track.artist, selectedVideoId) {
        mutableStateOf<Float?>(null)
    }
    var lastSyncSeekAtMs by remember(track.title, track.artist, selectedVideoId) {
        mutableLongStateOf(Long.MIN_VALUE)
    }

    if (selectedVideoId.isBlank()) return

    InAppMusicVideo(
        videoId = selectedVideoId,
        modifier = modifier,
        isPlaying = playerState.isPlaying,
        initialPositionSeconds = musicPlayer.currentPositionMs().coerceAtLeast(0L) / 1_000f,
        seekCommandSeconds = seekCommandSeconds,
        onProgressUpdate = { videoPositionMs, _ ->
            val audioPositionMs = musicPlayer.currentPositionMs().coerceAtLeast(0L)
            val nowMs = SystemClock.elapsedRealtime()
            val canSeek = lastSyncSeekAtMs == Long.MIN_VALUE || nowMs - lastSyncSeekAtMs >= SYNC_SEEK_COOLDOWN_MS
            if (canSeek && abs(videoPositionMs - audioPositionMs) >= SYNC_SEEK_THRESHOLD_MS) {
                lastSyncSeekAtMs = nowMs
                seekCommandSeconds = audioPositionMs / 1_000f
            }
        },
    )
}

private const val SYNC_SEEK_THRESHOLD_MS = 2_500L
private const val SYNC_SEEK_COOLDOWN_MS = 5_000L
