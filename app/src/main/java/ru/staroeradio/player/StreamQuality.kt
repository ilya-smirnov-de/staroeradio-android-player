package ru.staroeradio.player

import android.content.Context

object StreamQuality {
    const val PREF_QUALITY = "stream_quality"
    val options = listOf(128, 64, 32)

    fun selected(context: Context): Int {
        val value = context.getSharedPreferences(PlaybackService.PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_QUALITY, 128)
        return value.takeIf { it in options } ?: 128
    }

    fun save(context: Context, bitrate: Int) {
        require(bitrate in options)
        context.getSharedPreferences(PlaybackService.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(PREF_QUALITY, bitrate).apply()
    }
}
