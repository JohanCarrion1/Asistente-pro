package org.asistentepro.asistentepro

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** One application-scoped engine keeps app, overlay and assistant session in sync. */
class AureaRuntime private constructor(context: Context) {
    private val app = context.applicationContext as Application
    val engine = AssistantEngine(app)
    var foregroundLauncher: ((Intent) -> Unit)? = null
    var sessionLauncher: ((Intent) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private var tts: TextToSpeech? = null
    private var pending: String? = null
    init {
        engine.onDeviceAction = DeviceCommander(app, engine) { intent ->
            check(!app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) { "Desbloquea el teléfono" }
            val launcher = foregroundLauncher ?: sessionLauncher
            if (launcher != null) launcher(intent) else app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }::execute
        engine.onSpeak = ::speak
    }
    fun speak(text: String) {
        if (tts == null) {
            pending = text
            tts = TextToSpeech(app) { result -> handler.post {
                ready = result == TextToSpeech.SUCCESS && tts?.setLanguage(Locale("es", "ES"))?.let { it >= 0 } == true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { handler.post { engine.phase(VoicePhase.Speaking) } }
                    override fun onDone(id: String?) { handler.post { engine.phase(VoicePhase.Idle) } }
                    @Deprecated("Deprecated in Java") override fun onError(id: String?) { handler.post { engine.phase(VoicePhase.Idle) } }
                })
                val queued = pending; pending = null
                if (ready && queued != null) speak(queued)
                else if (!ready) { engine.phase(VoicePhase.Idle); engine.notice("Instala una voz en español en los ajustes de texto a voz de Android. La respuesta aparece en pantalla.") }
            } }
        } else if (ready) {
            engine.phase(VoicePhase.Speaking)
            tts?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "aurea-response")
        } else pending = text
    }
    fun stopSpeaking() { pending = null; tts?.stop(); if (engine.state.value.phase == VoicePhase.Speaking) engine.phase(VoicePhase.Idle) }
    companion object {
        @Volatile private var instance: AureaRuntime? = null
        fun get(context: Context): AureaRuntime = instance ?: synchronized(this) { instance ?: AureaRuntime(context).also { instance = it } }
    }
}
