package org.asistentepro.asistentepro

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import java.util.UUID

class AssistantEngine(application: Application) {
    private val store = LocalStore(application)
    private val ai = AiClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(AssistantState(hasKey = store.apiKey().isNotBlank(), provider = store.provider,
        keyMask = store.keyMask(), keyUnreadable = store.keyUnreadable(),
        configuredProviders = store.configuredProviders(), conversations = store.conversations(), readAloud = store.readAloud, model = store.model, draft = store.draft))
    val state = mutable.asStateFlow()
    private var conversationId = UUID.randomUUID().toString()
    private var request: Job? = null
    private var call: Call? = null
    private var spokenTurn = false
    private var connectionGeneration = 0
    var onDeviceAction: ((DeviceAction) -> Unit)? = null
    var onSpeak: ((String) -> Unit)? = null
    fun handsFree(active: Boolean, status: String) { mutable.update { it.copy(handsFreeActive = active, handsFreeStatus = status) } }
    fun permissions(overlay: Boolean, assistant: Boolean) { mutable.update { it.copy(overlayGranted = overlay, assistantSelected = assistant) } }
    fun systemPermissions(control: Boolean, settings: Boolean) { mutable.update { it.copy(controlEnabled = control, writeSettingsGranted = settings) } }
    fun provider(value: AiProvider) {
        stop(); store.provider = value
        mutable.update { it.copy(provider = value, model = store.model, hasKey = store.apiKey().isNotBlank(), keyMask = store.keyMask(), keyUnreadable = store.keyUnreadable(), keyDraft = "", connectionStatus = "", error = null, notice = null) }
    }

    fun draft(value: String) { store.draft = value; mutable.update { it.copy(draft = value) } }
    fun keyDraft(value: String) { mutable.update { it.copy(keyDraft = value, error = null) } }
    fun page(value: Page) { mutable.update { it.copy(page = value, error = null, notice = null, keyDraft = "") } }
    fun phase(value: VoicePhase) { mutable.update { it.copy(phase = value) } }
    fun error(value: String) { mutable.update { it.copy(error = value, phase = VoicePhase.Idle) } }
    fun notice(value: String) { mutable.update { it.copy(notice = value) } }
    fun dismissError() { mutable.update { it.copy(error = null, notice = null) } }
    fun toggleReading(value: Boolean) { store.readAloud = value; mutable.update { it.copy(readAloud = value) } }
    fun model(value: String) { stop(); store.model = value; mutable.update { it.copy(model = value, connectionStatus = "") } }
    fun torch(value: Boolean) { mutable.update { it.copy(torchOn = value) } }
    fun saveKey() {
        val key = state.value.keyDraft.trim()
        if (key.length < 12 || key.any { it.isWhitespace() }) { error("Pega la clave completa de ${state.value.provider.title}, sin espacios."); return }
        stop()
        runCatching { store.saveKey(key) }.onSuccess {
            mutable.update { it.copy(hasKey = true, keyMask = store.keyMask(), keyUnreadable = false, connectionStatus = "", configuredProviders = store.configuredProviders(), keyDraft = "", notice = "Clave de ${it.provider.title} guardada y recuperada correctamente.", error = null) }
        }.onFailure { mutable.update { it.copy(hasKey = store.apiKey().isNotBlank(), keyMask = store.keyMask(), keyUnreadable = store.keyUnreadable(), error = "Android no pudo guardar o recuperar la clave. Inténtalo otra vez; tu clave anterior no se elimina al cambiar de pantalla.") } }
    }
    fun removeKey() { stop(); store.deleteKey(); mutable.update { it.copy(hasKey = false, keyMask = "", keyUnreadable = false, connectionStatus = "", configuredProviders = store.configuredProviders(), keyDraft = "", notice = "Clave eliminada.") } }
    fun checkConnection() {
        if (!state.value.hasKey) { error("Guarda una clave para comprobar la conexión."); return }
        stop()
        val generation = connectionGeneration
        val provider = state.value.provider
        val key = store.apiKey()
        val model = state.value.model
        mutable.update { it.copy(checkingConnection = true, connectionStatus = "Comprobando acceso a ${provider.title}…", error = null) }
        request = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val probe = ai.call(provider, key, model, listOf(Message("connection-check", "Responde solo OK. No realices ninguna acción del teléfono.", true)))
                    call = probe
                    ai.reply(provider, probe) // A connection check never executes tool calls.
                }
                if (generation == connectionGeneration) mutable.update { it.copy(connectionStatus = "Conexión con ${provider.title} comprobada. Tu clave y modelo tienen acceso.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val reason = (failure as? AssistantException)?.message ?: "No pude conectar. Revisa Internet y el identificador del modelo."
                if (generation == connectionGeneration) mutable.update { it.copy(connectionStatus = "Tu clave sigue guardada. $reason") }
            } finally { if (generation == connectionGeneration) mutable.update { it.copy(checkingConnection = false) } }
        }
    }
    fun clearHistory() { stop(); store.draft = ""; store.saveConversations(emptyList()); conversationId = UUID.randomUUID().toString(); mutable.update { it.copy(conversations = emptyList(), messages = emptyList(), draft = "", notice = "Historial eliminado.") } }
    fun newConversation() { stop(); store.draft = ""; conversationId = UUID.randomUUID().toString(); mutable.update { it.copy(page = Page.Assistant, messages = emptyList(), draft = "", error = null, notice = null) } }
    fun openConversation(item: Conversation) { stop(); conversationId = item.id; mutable.update { it.copy(page = Page.Assistant, messages = item.messages, error = null) } }
    fun deleteConversation(item: Conversation) {
        val items = state.value.conversations.filterNot { it.id == item.id }
        store.saveConversations(items)
        mutable.update { it.copy(conversations = items) }
        if (conversationId == item.id) { newConversation(); page(Page.History) }
    }
    fun stop() { connectionGeneration++; call?.cancel(); request?.cancel(); mutable.update { it.copy(phase = VoicePhase.Idle, checkingConnection = false) } }
    private fun append(text: String, user: Boolean) {
        mutable.update { it.copy(messages = it.messages + Message(UUID.randomUUID().toString(), text, user)) }
        persist()
    }
    private fun persist() {
        val messages = state.value.messages
        if (messages.isEmpty()) return
        val title = messages.firstOrNull { it.user }?.text?.take(60) ?: "Nueva conversación"
        val item = Conversation(conversationId, title, messages)
        val items = (listOf(item) + state.value.conversations.filterNot { it.id == conversationId }).take(40)
        store.saveConversations(items)
        mutable.update { it.copy(conversations = items) }
    }
    fun action(action: DeviceAction) { onDeviceAction?.invoke(action) }
    fun deviceResult(text: String) {
        if (state.value.messages.isEmpty()) mutable.update { it.copy(notice = text) }
        else append(text, false)
        if (spokenTurn || state.value.readAloud) onSpeak?.invoke(text)
    }
    fun send(value: String = state.value.draft, spoken: Boolean = false) {
        val text = value.trim().take(12000)
        if (text.isEmpty() || request?.isActive == true) return
        spokenTurn = spoken
        // Connected conversations use the provider's native function calling.
        // Local shortcuts are only an offline fallback, never required phrasing.
        val local = ActionRouter.parse(text)
        val action = if (!state.value.hasKey || local == DeviceAction.CurrentTime || local == DeviceAction.CurrentDate) local else null
        if (action != null) {
            store.draft = ""
            mutable.update { it.copy(page = Page.Assistant, draft = "", error = null, phase = VoicePhase.Idle) }
            append(text, true); onDeviceAction?.invoke(action); return
        }
        if (!state.value.hasKey) { store.draft = text; mutable.update { it.copy(page = Page.Settings, draft = text, error = "Añade tu clave de ${it.provider.title} para conversar. Las acciones del teléfono no necesitan clave.") }; return }
        mutable.update { it.copy(page = Page.Assistant, draft = "", error = null, phase = VoicePhase.Thinking) }
        store.draft = ""
        append(text, true)
        val activeId = conversationId
        val history = state.value.messages
        val apiKey = store.apiKey()
        val model = state.value.model
        val provider = state.value.provider
        request = scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val newCall = ai.call(provider, apiKey, model, history)
                    call = newCall
                    ai.reply(provider, newCall)
                }
                if (activeId == conversationId) {
                    phase(VoicePhase.Idle)
                    if (response.action != null) onDeviceAction?.invoke(response.action)
                    else {
                        append(response.text, false)
                        if (state.value.readAloud || spoken) onSpeak?.invoke(response.text)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (activeId == conversationId) {
                    val message = if (error is AssistantException) error.message!! else "No pude conectar. Comprueba tu conexión e inténtalo de nuevo."
                    store.draft = text
                    mutable.update { it.copy(error = message, draft = text, phase = VoicePhase.Idle) }
                }
            }
        }
    }
}
