package com.motornav.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Suara petunjuk belokan (teks-ke-ucapan bawaan Android, bahasa Indonesia). */
object Voice {
    private const val PREF = "motornav"
    private const val K = "voice"
    private var tts: TextToSpeech? = null
    private var app: Context? = null
    @Volatile private var ready = false
    @Volatile var langOk = true
    @Volatile var enabled = true
    private var focus: AudioFocusRequest? = null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun init(ctx: Context) {
        if (tts != null) return
        val a = ctx.applicationContext
        app = a
        enabled = a.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(K, true)
        tts = TextToSpeech(a) { st ->
            val t = tts
            if (st == TextToSpeech.SUCCESS && t != null) {
                val r = t.setLanguage(Locale("id", "ID"))
                langOk = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                t.setAudioAttributes(attrs)
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { release() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { release() }
                })
                ready = true
            }
        }
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        app?.getSharedPreferences(PREF, Context.MODE_PRIVATE)?.edit()?.putBoolean(K, on)?.apply()
        if (!on) tts?.stop()
    }

    /** Ucapkan teks; yang sedang diucapkan dipotong supaya petunjuk terbaru yang terdengar. */
    fun speak(text: String) {
        if (!enabled || !ready || !langOk || text.isBlank()) return
        val t = tts ?: return
        val am = app?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am != null) {
            val f = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs).build()
            focus = f
            am.requestAudioFocus(f)
        }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav")
    }

    private fun release() {
        val am = app?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        focus?.let { am?.abandonAudioFocusRequest(it) }
        focus = null
    }
}
