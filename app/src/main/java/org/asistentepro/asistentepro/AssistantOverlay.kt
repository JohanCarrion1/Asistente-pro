package org.asistentepro.asistentepro

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*

/** Native, touchable card. It reads no data from the app underneath it. */
class AssistantOverlay(private val context: Context, private val runtime: AureaRuntime, private val listen: () -> Unit) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var content: OverlayContent? = null
    fun show() {
        if (!Settings.canDrawOverlays(context)) { runtime.engine.error("Activa «Mostrar sobre otras apps» en Ajustes de Aurea."); return }
        if (root != null) return
        val card = OverlayContent(context, runtime, listen, ::hide)
        val container = FrameLayout(context).apply { setPadding(dp(14), dp(12), dp(14), dp(32)); addView(card) }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM }
        try { manager.addView(container, params); root = container; content = card; update(runtime.engine.state.value) }
        catch (_: Exception) { runtime.engine.error("Android no permitió mostrar la tarjeta. Revisa el permiso de superposición.") }
    }
    fun update(state: AssistantState) { content?.update(state) }
    fun hide() { root?.let { runCatching { manager.removeViewImmediate(it) } }; root = null; content = null }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}

class OverlayContent(context: Context, private val runtime: AureaRuntime, listen: () -> Unit, close: () -> Unit) : LinearLayout(context) {
    private val status: TextView
    private val response: TextView
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int = Color.WHITE) = TextView(context).apply { text = value; textSize = size; setTextColor(color) }
    init {
        orientation = VERTICAL; setPadding(dp(22), dp(18), dp(22), dp(18))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(31,34,53), Color.rgb(12,13,23))).apply { cornerRadius = dp(30).toFloat(); setStroke(dp(1), Color.rgb(110,125,213)) }
        elevation = dp(18).toFloat()
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(context).apply { setImageResource(R.drawable.assistant_brand); contentDescription = "Aurea" }, LayoutParams(dp(44), dp(44)))
        header.addView(text("Aurea", 23f).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setPadding(dp(12),0,0,0) }, LayoutParams(0,dp(48),1f))
        header.addView(Button(context).apply { text = "×"; contentDescription = "Cerrar superposición"; setTextColor(Color.WHITE); background = null; setOnClickListener { close() } }, LayoutParams(dp(46),dp(48)))
        addView(header)
        status = text("Te escucho", 12f, Color.rgb(161,183,255)).apply { setPadding(0,dp(12),0,dp(12)) }; addView(status)
        response = text("Di algo. Estoy aquí para ayudarte.", 18f).apply { setLineSpacing(dp(4).toFloat(),1f) }
        addView(ScrollView(context).apply { addView(response) }, LayoutParams(LayoutParams.MATCH_PARENT,dp(116)))
        val actions = LinearLayout(context).apply { gravity = Gravity.END; setPadding(0,dp(12),0,0) }
        fun action(label: String, click: () -> Unit) = Button(context).apply {
            text = label; isAllCaps = false; textSize = 13f; setTextColor(Color.rgb(182,199,255)); background = null; setOnClickListener { click() }
        }
        actions.addView(action("Abrir app") { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); close() })
        actions.addView(action("Hablar", listen)); actions.addView(action("Detener") { runtime.engine.stop(); runtime.stopSpeaking() })
        addView(actions)
    }
    fun update(state: AssistantState) {
        status.text = when (state.phase) { VoicePhase.Listening -> "Te escucho · sin registrar tu voz"; VoicePhase.Thinking -> "${state.provider.title} está preparando la respuesta…"; VoicePhase.Speaking -> "Aurea está respondiendo"; else -> "A tu lado · ${state.provider.title}" }
        response.text = state.error ?: state.messages.lastOrNull()?.text ?: state.notice ?: "Di «abre YouTube» o hazme una pregunta."
    }
}
