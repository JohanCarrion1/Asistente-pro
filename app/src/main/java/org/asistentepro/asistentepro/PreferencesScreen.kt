package org.asistentepro.asistentepro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun PreferencesScreen(state: AssistantState, events: AssistantCallbacks) {
    var visible by rememberSaveable(state.provider) { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize().testTag("preferences"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text("A tu manera.", color = Cream, fontSize = 29.sp, lineHeight = 36.sp, letterSpacing = (-.9).sp)
            Text("Tu inteligencia favorita. Tu voz, sin entrenamientos.", Modifier.padding(top = 10.dp), color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
        }
        item { AssistantCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.AutoAwesome, null, tint = Mint, modifier = Modifier.size(23.dp)); Spacer(Modifier.width(8.dp)); Text("Tu conexión de IA", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
            Text("Elige quién responde. Cada proveedor conserva su propia clave y modelo.", Modifier.padding(top = 12.dp), color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
            AiProvider.entries.chunked(3).forEach { providers ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    providers.forEach { provider ->
                        FilterChip(selected = state.provider == provider, onClick = { events.onProvider(provider) }, label = { Text(provider.title, fontSize = 11.sp) },
                            leadingIcon = if (provider in state.configuredProviders) { { Icon(Icons.Outlined.Check, null, Modifier.size(12.dp)) } } else null)
                    }
                }
            }
            Text("Conexión con ${state.provider.title}", Modifier.padding(top = 12.dp), color = Mint, fontSize = 14.sp)
            Text(if (state.hasKey) "Clave cifrada guardada. El acceso se comprueba al enviar una consulta." else "Añade tu clave de ${state.provider.title}. Los comandos del teléfono funcionan sin ella.", Modifier.padding(top = 10.dp), color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
            OutlinedTextField(state.keyDraft, events.onKeyDraft, label = { Text(if (state.hasKey) "Reemplazar clave" else "Tu clave de ${state.provider.title}") },
                modifier = Modifier.fillMaxWidth().padding(top = 15.dp).testTag("api-key"), singleLine = true, shape = RoundedCornerShape(17.dp),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Ocultar clave" else "Mostrar clave") } })
            Button(onClick = events.onSaveKey, enabled = state.keyDraft.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 50.dp), shape = RoundedCornerShape(17.dp)) { Text("Guardar clave") }
            TextButton(onClick = { uri.openUri(state.provider.keysUrl) }) { Text("Obtener clave de ${state.provider.title}", color = Mint, fontSize = 12.sp) }
            OutlinedTextField(state.model, events.onModel, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag("ai-model"), singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), shape = RoundedCornerShape(17.dp))
            Text("Usa el identificador exacto de un modelo disponible en tu cuenta. Para interpretar acciones, el modelo debe admitir funciones. Puede requerir saldo; la app no incluye una suscripción.", Modifier.padding(top = 10.dp), color = Muted, fontSize = 11.sp, lineHeight = 19.sp)
            TextButton(onClick = { events.onModel(state.provider.defaultModel) }) { Text("Restablecer modelo sugerido", fontSize = 11.sp) }
            if (state.hasKey) TextButton(onClick = { remove = true }) { Text("Eliminar clave de ${state.provider.title}", color = Muted, fontSize = 12.sp) }
        } }
        item { AssistantCard {
            Icon(Icons.Outlined.GraphicEq, null, tint = Mint, modifier = Modifier.size(28.dp))
            Text("Xylon, a tu lado.", Modifier.padding(top = 13.dp), color = Cream, fontSize = 21.sp)
            Text("Llama a Xylon y habla con tus propias palabras, incluso desde otra app. No necesitas frases exactas ni entrenar tu voz.", Modifier.padding(top = 12.dp), color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Manos libres", color = Cream, fontSize = 16.sp); Text(state.handsFreeStatus, Modifier.padding(top = 5.dp), color = Mint, fontSize = 11.sp, lineHeight = 18.sp) }
                Switch(state.handsFreeActive, events.onHandsFree, Modifier.testTag("hands-free-toggle"))
            }
            Text("Necesita micrófono y permiso para mostrarse sobre otras apps. Mientras esté activo verás una notificación con Detener. Consume batería; no se reactiva solo tras reiniciar o forzar el cierre.", Modifier.padding(top = 12.dp), color = Muted, fontSize = 11.sp, lineHeight = 20.sp)
            OutlinedButton(onClick = events.onOverlayPermission, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(16.dp)) { Text(if (state.overlayGranted) "Superposición permitida" else "Permitir superposición", fontSize = 12.sp) }
            OutlinedButton(onClick = events.onDefaultAssistant, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(if (state.assistantSelected) "Xylon es tu asistente" else "Elegir como asistente de Android", fontSize = 12.sp) }
            Text("También podrás abrir Xylon con el gesto de asistencia de tu móvil. Desbloquea el teléfono para abrir aplicaciones. El ahorro de batería del fabricante puede detener la escucha.", Modifier.padding(top = 10.dp), color = Muted, fontSize = 11.sp, lineHeight = 20.sp)
            Button(onClick = events.onShowOverlay, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(16.dp)) { Text("Probar tarjeta flotante") }
        } }
        item { AssistantCard {
            Text("Una respuesta con voz", color = Cream, fontSize = 18.sp)
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Leer también los chats escritos", color = Cream, fontSize = 14.sp); Text("Los comandos hablados reciben respuesta hablada.", Modifier.padding(top = 5.dp), color = Muted, fontSize = 11.sp) }; Switch(state.readAloud, events.onReading) }
            Text("Utiliza una voz en español instalada en Android. Si no está disponible, verás la respuesta en pantalla.", Modifier.padding(top = 12.dp), color = Muted, fontSize = 12.sp, lineHeight = 21.sp)
        } }
        item { AssistantCard {
            Icon(Icons.Outlined.Security, null, tint = Mint, modifier = Modifier.size(26.dp))
            Text("Tu privacidad importa.", Modifier.padding(top = 14.dp), color = Cream, fontSize = 18.sp)
            Text("Las claves se cifran con Android Keystore y el historial se guarda en este dispositivo. Solo el proveedor elegido recibe el contexto del chat al consultar la IA. El reconocimiento de voz utiliza el modelo local y no guarda grabaciones. La superposición no lee ni captura la pantalla de otras apps.", Modifier.padding(top = 12.dp), color = Muted, fontSize = 12.sp, lineHeight = 21.sp)
            TextButton(onClick = { clear = true }, enabled = state.conversations.isNotEmpty()) { Text("Borrar historial de este dispositivo", fontSize = 12.sp) }
        } }
        item { Text("XYLON  ·  4.0.0\nInteligencia a tu manera.", color = Muted, fontSize = 11.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("¿Borrar tu historial?") }, text = { Text("Se eliminarán las conversaciones guardadas en esta app.") }, confirmButton = { TextButton(onClick = { events.onClearHistory(); clear = false }) { Text("Borrar") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancelar") } })
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("¿Eliminar la clave de ${state.provider.title}?") }, text = { Text("Las otras claves y las acciones del teléfono seguirán disponibles.") }, confirmButton = { TextButton(onClick = { events.onRemoveKey(); remove = false }) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Cancelar") } })
}
