package org.asistentepro.asistentepro

import android.content.Context
import org.vosk.Model
import java.io.File
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

object OfflineSpeech {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    fun load(context: Context, ready: (Model) -> Unit, error: (Exception) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            try {
                val loaded = model ?: run {
                    val directory = File(app.noBackupFilesDir, "speech-es-0.42")
                    val complete = File(directory, ".complete")
                    if (!complete.exists()) {
                        directory.deleteRecursively(); directory.mkdirs()
                        fun unpack(asset: String, destination: File) {
                            val children = app.assets.list(asset).orEmpty()
                            if (children.isEmpty()) app.assets.open(asset).use { input -> destination.outputStream().use { input.copyTo(it) } }
                            else { destination.mkdirs(); children.forEach { unpack("$asset/$it", File(destination, it)) } }
                        }
                        unpack("vosk-model-small-es-0.42", directory)
                        complete.writeText("0.42")
                    }
                    Model(directory.absolutePath).also { model = it }
                }
                main.post { ready(loaded) }
            } catch (failure: Exception) { main.post { error(failure) } }
        }
    }
}
