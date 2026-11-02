package ru.staroeradio.player

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.PopupMenu
import android.content.ComponentName
import android.os.Bundle
import android.graphics.Typeface
import android.text.Annotation
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AlignmentSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

/** Three stations and a single Play/Pause button. Playback lives in PlaybackService. */
class MainActivity : Activity() {

    private lateinit var stationGroup: RadioGroup
    private lateinit var playPauseButton: ImageButton
    private lateinit var statusText: TextView

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var updatingUi = false

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refreshUi()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.localized(newBase))
    }

    private fun showAbout() {
        val text = SpannableString(getText(R.string.about_message))
        text.getSpans(0, text.length, Annotation::class.java)
            .filter { it.key == "role" && it.value == "project_name" }
            .forEach { annotation ->
                val start = text.getSpanStart(annotation)
                val end = text.getSpanEnd(annotation)
                text.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.setSpan(RelativeSizeSpan(1.2f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val paragraphEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it + 1 }
                text.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                    start, paragraphEnd, Spanned.SPAN_PARAGRAPH)
            }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.about)
            .setMessage(text)
            .setPositiveButton(R.string.button_close, null)
            .show()
        dialog.findViewById<TextView>(android.R.id.message)?.let { message ->
            Linkify.addLinks(message, Linkify.WEB_URLS)
            message.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun showSettings() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setItems(arrayOf(getString(R.string.language), getString(R.string.stream_quality))) { _, index ->
                if (index == 0) showLanguageSettings() else showQualitySettings()
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun showQualitySettings() {
        val options = StreamQuality.options
        val labels = options.map { getString(R.string.quality_option, it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.stream_quality)
            .setSingleChoiceItems(labels, options.indexOf(StreamQuality.selected(this))) { dialog, index ->
                StreamQuality.save(this, options[index])
                dialog.dismiss()
                refreshUi()
            }
            .setNeutralButton(R.string.quality_info) { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage(R.string.quality_description)
                    .setPositiveButton(R.string.button_close, null)
                    .show()
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun showLanguageSettings() {
        val choices = arrayOf("ru", "en")
        val labels = arrayOf(
            getString(R.string.language_russian),
            getString(R.string.language_english)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.language)
            .setSingleChoiceItems(labels, choices.indexOf(AppLanguage.selection(this)).coerceAtLeast(0)) { dialog, index ->
                dialog.dismiss()
                if (choices[index] != AppLanguage.selection(this)) {
                    AppLanguage.save(this, choices[index])
                    recreate()
                }
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = getString(R.string.app_name)
        findViewById<ImageButton>(R.id.menuButton).setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                menu.add(R.string.settings).setOnMenuItemClickListener {
                    showSettings()
                    true
                }
                menu.add(R.string.about).setOnMenuItemClickListener {
                    showAbout()
                    true
                }
                show()
            }
        }

        stationGroup = findViewById(R.id.stationGroup)
        playPauseButton = findViewById(R.id.playPauseButton)
        statusText = findViewById(R.id.statusText)
        val savedStation = getSharedPreferences(PlaybackService.PREFS_NAME, MODE_PRIVATE)
            .getString(PlaybackService.PREF_STATION, RadioStation.OLD.id)
        findViewById<TextView>(R.id.bitrateLabel).text =
            getString(R.string.bitrate_label, RadioStation.fromId(savedStation).bitrate(this))

        playPauseButton.isEnabled = false
        findViewById<android.widget.RadioButton>(R.id.stationOld).isEnabled = false
        findViewById<android.widget.RadioButton>(R.id.stationChildren).isEnabled = false
        findViewById<android.widget.RadioButton>(R.id.stationMusic).isEnabled = false

        stationGroup.setOnCheckedChangeListener { _, checkedId ->
            if (updatingUi) return@setOnCheckedChangeListener
            val player = controller ?: return@setOnCheckedChangeListener
            val station = when (checkedId) {
                R.id.stationOld -> RadioStation.OLD
                R.id.stationChildren -> RadioStation.CHILDREN
                R.id.stationMusic -> RadioStation.MUSIC
                else -> return@setOnCheckedChangeListener
            }

            if (player.currentMediaItem?.mediaId == station.id) return@setOnCheckedChangeListener

            getSharedPreferences(PlaybackService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PlaybackService.PREF_STATION, station.id)
                .apply()

            val keepPlaying = player.playWhenReady
            player.setMediaItem(station.asMediaItem(this))
            if (keepPlaying) {
                player.prepare()
                player.play()
            }
            refreshUi()
        }

        playPauseButton.setOnClickListener {
            val player = controller ?: return@setOnClickListener
            if (player.playerError == null && player.playWhenReady) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_IDLE ||
                    player.playbackState == Player.STATE_ENDED ||
                    player.playerError != null
                ) {
                    player.prepare()
                }
                player.play()
            }
            refreshUi()
        }
    }

    override fun onStart() {
        super.onStart()

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future

        // The callback must run on the main thread because it updates Views.
        future.addListener({
            if (controllerFuture !== future) return@addListener
            try {
                val connected = future.get()
                controller = connected
                connected.addListener(playerListener)
                refreshUi()
            } catch (e: Exception) {
                statusText.setText(R.string.status_connection_failed)
                playPauseButton.isEnabled = false
            }
        }, Executor { command -> runOnUiThread(command) })
    }

    override fun onStop() {
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    private fun setPlaybackAction(icon: Int, label: Int) {
        playPauseButton.setImageResource(icon)
        playPauseButton.contentDescription = getString(label)
        playPauseButton.tooltipText = getString(label)
    }

    private fun refreshUi() {
        val player = controller ?: return
        val station = RadioStation.fromId(player.currentMediaItem?.mediaId)
        findViewById<TextView>(R.id.bitrateLabel).text =
            getString(R.string.bitrate_label, station.bitrate(this))

        updatingUi = true
        stationGroup.check(
            when (station) {
                RadioStation.OLD -> R.id.stationOld
                RadioStation.CHILDREN -> R.id.stationChildren
                RadioStation.MUSIC -> R.id.stationMusic
            }
        )
        updatingUi = false

        playPauseButton.isEnabled = true
        findViewById<android.widget.RadioButton>(R.id.stationOld).isEnabled = true
        findViewById<android.widget.RadioButton>(R.id.stationChildren).isEnabled = true
        findViewById<android.widget.RadioButton>(R.id.stationMusic).isEnabled = true

        when {
            player.playerError != null -> {
                setPlaybackAction(R.drawable.ic_play, R.string.button_retry)
                statusText.setText(R.string.status_connection_failed)
            }
            player.playWhenReady && player.playbackState == Player.STATE_BUFFERING -> {
                setPlaybackAction(R.drawable.ic_pause, R.string.button_pause)
                statusText.setText(R.string.status_buffering)
            }
            player.isPlaying -> {
                setPlaybackAction(R.drawable.ic_pause, R.string.button_pause)
                statusText.setText(R.string.status_playing)
            }
            player.playWhenReady -> {
                setPlaybackAction(R.drawable.ic_pause, R.string.button_pause)
                statusText.setText(R.string.status_connecting)
            }
            else -> {
                setPlaybackAction(R.drawable.ic_play, R.string.button_play)
                statusText.setText(R.string.status_paused)
            }
        }
    }
}
