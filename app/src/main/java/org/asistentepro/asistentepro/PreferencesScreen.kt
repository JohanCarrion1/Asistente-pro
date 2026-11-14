package org.asistentepro.asistentepro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun PreferencesScreen(state: AssistantState, events: AssistantCallbacks) {
    var visible by rememberSaveable { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text("Que se sienta tuyo.", color = Cream, fontSize = 29.sp, lineHeight = 36.sp, letterSpacing = (-.9).sp)
            Text("La forma de responder. La voz. Tu privacidad.", Modifier.padding(top = 10.dp), color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
        }
        item { AssistantCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Key, null, tint = Mint, modifier = Modifier.size(23.dp)); Text("  Conexión con Groq", color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
            Text(if (state.hasKey) "Clave guardada y cifrada en este dispositivo." else "Añade tu clave para consultar la IA.", Modifier.padding(top = 12.dp), color = if (state.hasKey) Mint else Muted, fontSize = 13.sp, lineHeight = 21.sp)
            OutlinedTextField(state.keyDraft, events.onKeyDraft, label = { Text(if (state.hasKey) "Reemplazar clave" else "Tu clave de Groq") }, placeholder = { Text("gsk_…") }, modifier = Modifier.fillMaxWidth().padding(top = 18.dp), singleLine = true, shape = RoundedCornerShape(17.dp), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Ocultar clave" else "Mostrar clave") } })
            Button(onClick = events.onSaveKey, enabled = state.keyDraft.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 50.dp), shape = RoundedCornerShape(17.dp)) { Text("Guardar clave") }
            TextButton(onClick = { uri.openUri("https://console.groq.com/keys") }) { Text("Obtener una clave de Groq", color = Mint, fontSize = 13.sp) }
            if (state.hasKey) TextButton(onClick = { remove = true }) { Text("Eliminar clave guardada", color = Muted, fontSize = 12.sp) }
        } }
        item { AssistantCard {
            Text("Respuestas a tu ritmo", color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(14.dp))
            ModelRow("Ágil", "Llama 3.1 · preguntas cotidianas", state.model == "llama-3.1-8b-instant") { events.onModel("llama-3.1-8b-instant") }
            ModelRow("Profundo", "Llama 3.3 · ideas complejas", state.model == "llama-3.3-70b-versatile") { events.onModel("llama-3.3-70b-versatile") }
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Line)
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Leer respuestas", color = Cream, fontSize = 14.sp); Text("Voz en español de Android", Modifier.padding(top = 5.dp), color = Muted, fontSize = 11.sp) }; Switch(state.readAloud, events.onReading) }
            Text("Para hablar, toca el micrófono. No hay escucha permanente ni palabra de activación. La disponibilidad depende del servicio de voz instalado en Android.", Modifier.padding(top = 15.dp), color = Muted, fontSize = 12.sp, lineHeight = 21.sp)
        } }
        item { AssistantCard {
            Icon(Icons.Outlined.Security, null, tint = Mint, modifier = Modifier.size(26.dp))
            Text("Privacidad, por diseño.", Modifier.padding(top = 14.dp), color = Cream, fontSize = 18.sp)
            Text("La clave se protege con Android Keystore. El historial se guarda localmente. Al consultar la IA, los últimos mensajes necesarios se envían a Groq. Tu voz utiliza el servicio de Android.", Modifier.padding(top = 12.dp), color = Muted, fontSize = 12.sp, lineHeight = 21.sp)
            TextButton(onClick = { clear = true }, enabled = state.conversations.isNotEmpty()) { Text("Borrar historial de este dispositivo", fontSize = 12.sp) }
        } }
        item { Text("ASISTENTE PRO  ·  3.0.1\nUna nueva identidad, completamente Android.", color = Muted, fontSize = 11.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("¿Borrar tu historial?") }, text = { Text("Se eliminarán las conversaciones guardadas en esta app.") }, confirmButton = { TextButton(onClick = { events.onClearHistory(); clear = false }) { Text("Borrar") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancelar") } })
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("¿Eliminar la clave?") }, text = { Text("Las acciones del teléfono seguirán disponibles.") }, confirmButton = { TextButton(onClick = { events.onRemoveKey(); remove = false }) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Cancelar") } })
}

@Composable private fun ModelRow(title: String, subtitle: String, selected: Boolean, click: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected, click); Column { Text(title, color = Cream, fontSize = 14.sp); Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 18.sp) } }
}
