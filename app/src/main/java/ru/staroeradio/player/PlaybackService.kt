package ru.staroeradio.player

import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Owns playback independently of the Activity, including when the screen is off. */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val languageListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            AppLanguage.PREF_LANGUAGE -> refreshMetadata()
            StreamQuality.PREF_QUALITY -> refreshStreamQuality()
        }
    }

    private fun refreshStreamQuality() {
        val player = mediaSession?.player ?: return
        val current = player.currentMediaItem ?: return
        val replacement = RadioStation.fromId(current.mediaId)
            .asMediaItem(AppLanguage.localized(this))
        if (current.localConfiguration?.uri == replacement.localConfiguration?.uri) return
        val keepPlaying = player.playWhenReady
        player.setMediaItem(replacement)
        if (keepPlaying) {
            player.prepare()
            player.play()
        }
    }

    private fun refreshMetadata() {
        val player = mediaSession?.player ?: return
        val current = player.currentMediaItem ?: return
        val station = RadioStation.fromId(current.mediaId)
        val metadata = station.asMediaItem(AppLanguage.localized(this)).mediaMetadata
        // Metadata-only replacement retains the current stream and playback position.
        player.replaceMediaItem(player.currentMediaItemIndex,
            current.buildUpon().setMediaMetadata(metadata).build())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshMetadata()
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        val stationId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(PREF_STATION, RadioStation.OLD.id)
        player.setMediaItem(RadioStation.fromId(stationId).asMediaItem(AppLanguage.localized(this)))
        // No autoplay: only start streaming when the user presses Play.

        mediaSession = MediaSession.Builder(this, player).build()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(languageListener)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(languageListener)
        mediaSession?.let {
            it.player.release()
            it.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val PREFS_NAME = "radio_preferences"
        const val PREF_STATION = "selected_station"
    }
}
