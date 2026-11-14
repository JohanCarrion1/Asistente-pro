package org.asistentepro.asistentepro

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/** Supplies the local recognizer required by Android's default-assistant role. */
class AureaRecognitionService : RecognitionService() {
    private val main = Handler(Looper.getMainLooper())
    private var callback: Callback? = null
    private var speech: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var generation = 0
    override fun onStartListening(intent: Intent?, listener: Callback) {
        if (callback != null || HandsFreeService.active != null) { listener.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY); return }
        callback = listener; val current = ++generation
        OfflineSpeech.load(this, { model ->
            if (generation != current || callback == null) return@load
            runCatching {
                recognizer = Recognizer(model, 16000f)
                speech = SpeechService(recognizer, 16000f)
                check(speech!!.startListening(object : RecognitionListener {
                    override fun onPartialResult(hypothesis: String) {
                        val text = JSONObject(hypothesis).optString("partial")
                        main.post { if (generation == current) callback?.partialResults(bundle(text)) }
                    }
                    override fun onResult(hypothesis: String) = finish(hypothesis, current)
                    override fun onFinalResult(hypothesis: String) = finish(hypothesis, current)
                    override fun onError(exception: Exception) { main.post { if (generation == current) fail() } }
                    override fun onTimeout() { main.post { if (generation == current) fail(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) } }
                }, 15000))
                listener.readyForSpeech(Bundle())
            }.onFailure { fail() }
        }, { if (generation == current) fail() })
    }
    private fun bundle(text: String) = Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text)) }
    private fun finish(json: String, current: Int) {
        val text = runCatching { JSONObject(json).optString("text") }.getOrDefault("")
        if (text.isBlank()) return
        main.post { if (generation == current && callback != null) { callback?.results(bundle(text)); cleanup() } }
    }
    private fun fail(code: Int = SpeechRecognizer.ERROR_AUDIO) { callback?.error(code); cleanup() }
    private fun cleanup() { generation++; callback = null; speech?.stop(); speech?.shutdown(); recognizer?.close(); speech = null; recognizer = null }
    override fun onStopListening(listener: Callback) { speech?.stop() }
    override fun onCancel(listener: Callback) { cleanup() }
    override fun onDestroy() { cleanup(); super.onDestroy() }
}
