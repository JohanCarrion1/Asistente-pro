package org.asistentepro.asistentepro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private val groq = GroqClient()
    private val mutable = MutableStateFlow(AssistantState(hasKey = store.apiKey().isNotBlank(), conversations = store.conversations(), readAloud = store.readAloud, model = store.model, draft = store.draft))
    val state = mutable.asStateFlow()
    private var conversationId = UUID.randomUUID().toString()
    private var request: Job? = null
    private var call: Call? = null
    var onDeviceAction: ((DeviceAction) -> Unit)? = null
    var onSpeak: ((String) -> Unit)? = null

    fun draft(value: String) { store.draft = value; mutable.update { it.copy(draft = value) } }
    fun keyDraft(value: String) { mutable.update { it.copy(keyDraft = value, error = null) } }
    fun page(value: Page) { mutable.update { it.copy(page = value, error = null, notice = null, keyDraft = "") } }
    fun phase(value: VoicePhase) { mutable.update { it.copy(phase = value) } }
    fun error(value: String) { mutable.update { it.copy(error = value, phase = VoicePhase.Idle) } }
    fun dismissError() { mutable.update { it.copy(error = null, notice = null) } }
    fun toggleReading(value: Boolean) { store.readAloud = value; mutable.update { it.copy(readAloud = value) } }
    fun model(value: String) { store.model = value; mutable.update { it.copy(model = value) } }
    fun torch(value: Boolean) { mutable.update { it.copy(torchOn = value) } }
    fun saveKey() {
        val key = state.value.keyDraft.trim()
        if (!key.startsWith("gsk_") || key.length < 24) { error("Pega una clave válida de Groq; normalmente empieza por gsk_."); return }
        runCatching { store.saveKey(key) }.onSuccess {
            mutable.update { it.copy(hasKey = true, keyDraft = "", notice = "Clave guardada de forma segura.", error = null) }
        }.onFailure { error("No se pudo guardar la clave en este dispositivo.") }
    }
    fun removeKey() { store.deleteKey(); mutable.update { it.copy(hasKey = false, keyDraft = "", notice = "Clave eliminada.") } }
    fun clearHistory() { stop(); store.draft = ""; store.saveConversations(emptyList()); conversationId = UUID.randomUUID().toString(); mutable.update { it.copy(conversations = emptyList(), messages = emptyList(), draft = "", notice = "Historial eliminado.") } }
    fun newConversation() { stop(); store.draft = ""; conversationId = UUID.randomUUID().toString(); mutable.update { it.copy(page = Page.Assistant, messages = emptyList(), draft = "", error = null, notice = null) } }
    fun openConversation(item: Conversation) { stop(); conversationId = item.id; mutable.update { it.copy(page = Page.Assistant, messages = item.messages, error = null) } }
    fun deleteConversation(item: Conversation) {
        val items = state.value.conversations.filterNot { it.id == item.id }
        store.saveConversations(items)
        mutable.update { it.copy(conversations = items) }
        if (conversationId == item.id) { newConversation(); page(Page.History) }
    }
    fun stop() { call?.cancel(); request?.cancel(); mutable.update { it.copy(phase = VoicePhase.Idle) } }
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
    }
    fun send(value: String = state.value.draft) {
        val text = value.trim().take(12000)
        if (text.isEmpty() || request?.isActive == true) return
        val action = ActionRouter.parse(text)
        if (action != null) {
            store.draft = ""
            mutable.update { it.copy(page = Page.Assistant, draft = "", error = null, phase = VoicePhase.Idle) }
            append(text, true); onDeviceAction?.invoke(action); return
        }
        if (!state.value.hasKey) { store.draft = text; mutable.update { it.copy(page = Page.Settings, draft = text, error = "Añade tu clave de Groq para conversar. Las acciones del teléfono no necesitan clave.") }; return }
        mutable.update { it.copy(page = Page.Assistant, draft = "", error = null, phase = VoicePhase.Thinking) }
        store.draft = ""
        append(text, true)
        val activeId = conversationId
        val history = state.value.messages
        val apiKey = store.apiKey()
        val model = state.value.model
        request = viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val newCall = groq.call(apiKey, model, history)
                    call = newCall
                    groq.execute(newCall)
                }
                if (activeId == conversationId) {
                    append(response, false)
                    phase(VoicePhase.Idle)
                    if (state.value.readAloud) onSpeak?.invoke(response)
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
    override fun onCleared() { call?.cancel(); super.onCleared() }
}
