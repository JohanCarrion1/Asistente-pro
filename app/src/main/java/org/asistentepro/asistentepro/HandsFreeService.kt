package org.asistentepro.asistentepro

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

class HandsFreeService : Service(), RecognitionListener {
    private val runtime by lazy { XylonRuntime.get(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var speech: SpeechService? = null
    private var recognizer: Recognizer? = null
    private lateinit var overlay: AssistantOverlay
    private var waitingUntil = 0L
    private var starting = false
    private var destroyed = false
    private var lastCommand = ""
    private var lastTime = 0L
    private var muted = false
    private var muteUntil = 0L
    private val main = Handler(Looper.getMainLooper())
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate(); active = this
        overlay = AssistantOverlay(this, runtime, ::listenNow)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Xylon · manos libres", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Escucha local activada por ti. Puedes detenerla en cualquier momento."
        })
        scope.launch { runtime.engine.state.collect { state ->
            overlay.update(state)
            val shouldMute = state.phase == VoicePhase.Speaking || state.phase == VoicePhase.Thinking
            if (shouldMute != muted) {
                muted = shouldMute; if (!muted) muteUntil = SystemClock.elapsedRealtime() + 900
                speech?.setPause(muted)
            }
        } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || !Settings.canDrawOverlays(this)) {
            runtime.engine.error("Para activar Manos libres, concede micrófono y superposición desde Ajustes de Xylon."); stopSelf(); return START_NOT_STICKY
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, HandsFreeService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_brand).setContentTitle("Xylon te escucha")
            .setContentText("Di «Xylon» · reconocimiento local · toca Detener para apagar")
            .setContentIntent(open).setOngoing(true).setSilent(true).addAction(0, "Detener", stop).build()
        try { ServiceCompat.startForeground(this, 70, notification, if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0) }
        catch (_: Exception) { runtime.engine.error("Android no permitió iniciar el micrófono. Activa Manos libres con la app abierta."); stopSelf(); return START_NOT_STICKY }
        if (!starting && speech == null) {
            starting = true
            runtime.engine.handsFree(true, "Preparando reconocimiento local…")
            OfflineSpeech.load(this, { model ->
                if (destroyed) return@load
                runCatching {
                    recognizer = Recognizer(model, 16000f)
                    speech = SpeechService(recognizer, 16000f).also { check(it.startListening(this)) { "Micrófono ocupado" } }
                    starting = false; runtime.engine.handsFree(true, "Di «Xylon» · escucha local activa")
                }.onFailure { onError(it as? Exception ?: Exception(it)) }
            }, ::onError)
        }
        if (intent?.action == SHOW) { overlay.show(); listenNow() }
        return START_NOT_STICKY // Never restart a microphone silently after reboot/process death.
    }
    fun listenNow() {
        runtime.engine.stop(); runtime.stopSpeaking()
        waitingUntil = SystemClock.elapsedRealtime() + 12000
        runtime.engine.phase(VoicePhase.Listening)
        overlay.show()
        main.postDelayed({ if (waitingUntil != 0L && SystemClock.elapsedRealtime() >= waitingUntil) { waitingUntil = 0; runtime.engine.phase(VoicePhase.Idle) } }, 12500)
    }
    fun showForAction(action: () -> Unit) { overlay.show(); main.postDelayed({ if (!destroyed) action() }, 220) }
    private fun receive(json: String, partial: Boolean) {
        main.post {
            if (destroyed || muted || SystemClock.elapsedRealtime() < muteUntil) return@post
            val text = runCatching { JSONObject(json).optString(if (partial) "partial" else "text") }.getOrDefault("").trim()
            if (text.isEmpty()) return@post
            val extracted = WakePhrase.extract(text)
            if (partial) {
                if (extracted != null && waitingUntil == 0L) listenNow()
                return@post
            }
            val now = SystemClock.elapsedRealtime()
            val command = extracted ?: if (waitingUntil > now) text else return@post
            if (command.isBlank()) { listenNow(); return@post }
            waitingUntil = 0
            if (command == lastCommand && now - lastTime < 2500) return@post
            lastCommand = command; lastTime = now
            overlay.show(); runtime.engine.phase(VoicePhase.Idle)
            // Android 15 grants background launch access only after the overlay
            // is visible; allow its first frame before opening another app.
            main.postDelayed({ if (!destroyed) runtime.engine.send(command, spoken = true) }, 220)
        }
    }
    override fun onPartialResult(hypothesis: String) = receive(hypothesis, true)
    override fun onResult(hypothesis: String) = receive(hypothesis, false)
    override fun onFinalResult(hypothesis: String) = receive(hypothesis, false)
    override fun onError(exception: Exception) {
        main.post { if (!destroyed) { runtime.engine.error("No se pudo iniciar la escucha local. Revisa el permiso y que otra app no esté usando el micrófono."); stopSelf() } }
    }
    override fun onTimeout() { main.post { waitingUntil = 0; runtime.engine.phase(VoicePhase.Idle) } }
    override fun onDestroy() {
        destroyed = true; main.removeCallbacksAndMessages(null)
        speech?.stop(); speech?.shutdown(); recognizer?.close(); speech = null; recognizer = null
        overlay.hide(); scope.cancel(); runtime.stopSpeaking(); runtime.engine.handsFree(false, "Desactivado")
        if (runtime.engine.state.value.phase == VoicePhase.Listening) runtime.engine.phase(VoicePhase.Idle)
        if (active === this) active = null
        super.onDestroy()
    }
    companion object {
        const val STOP = "app.xylon.STOP"
        const val SHOW = "app.xylon.SHOW"
        private const val CHANNEL = "xylon-hands-free"
        var active: HandsFreeService? = null; private set
    }
}
