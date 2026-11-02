package ru.staroeradio.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes

/** Channels from the Staroe Radio project. */
enum class RadioStation(val id: String, val titleRes: Int, val url: String) {
    OLD(
        "old",
        R.string.station_old,
        "http://server.audiopedia.su:8000/ices128"
    ),
    CHILDREN(
        "children",
        R.string.station_children,
        "http://server.audiopedia.su:8000/detskoe128"
    ),
    MUSIC(
        "music",
        R.string.station_music,
        "http://server.audiopedia.su:8000/music128"
    );

    fun bitrate(context: Context): Int =
        StreamQuality.selected(context)

    fun asMediaItem(context: Context): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(url.removeSuffix("128") + bitrate(context))
        .setMimeType(MimeTypes.AUDIO_MPEG)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(context.getString(titleRes))
                .setArtist(context.getString(R.string.media_artist, bitrate(context)))
                .setIsPlayable(true)
                .build()
        )
        .build()

    companion object {
        fun fromId(id: String?): RadioStation =
            entries.firstOrNull { it.id == id } ?: OLD
    }
}
