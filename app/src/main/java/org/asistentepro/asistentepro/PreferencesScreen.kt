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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    var editingKey by rememberSaveable(state.provider, state.hasKey) { mutableStateOf(false) }
    LaunchedEffect(state.keyDraft, state.hasKey) { if (state.hasKey && state.keyDraft.isEmpty()) editingKey = false }
    val uri = LocalUriHandler.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
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
            if (state.keyUnreadable) Text("Hay una clave guardada, pero Android no puede descifrarla. Vuelve a pegarla para recuperarla. No confundas este problema con una clave rechazada por el proveedor.", Modifier.padding(top = 12.dp).testTag("saved-key-error"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 20.sp)
            if (state.hasKey) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp).testTag("saved-api-key"), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, tint = Mint, modifier = Modifier.size(22.dp))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("Clave de ${state.provider.title} guardada", color = Cream, fontSize = 14.sp)
                        Text(state.keyMask, Modifier.padding(top = 5.dp), color = Mint, fontSize = 15.sp)
                    }
                }
                Text("Sigue guardada al cerrar la app. Solo mostramos su terminación para protegerla; el campo de reemplazo vacío no la borra.", Modifier.padding(top = 10.dp), color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
                OutlinedButton(onClick = events.onCheckConnection, enabled = !state.checkingConnection, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("check-connection")) { Text(if (state.checkingConnection) "Comprobando…" else "Comprobar conexión") }
                Text("Envía una consulta breve al proveedor con el modelo elegido.", color = Muted, fontSize = 11.sp, lineHeight = 18.sp)
                if (state.connectionStatus.isNotBlank()) Text(state.connectionStatus, Modifier.padding(top = 10.dp).testTag("connection-status"), color = Mint, fontSize = 12.sp, lineHeight = 20.sp)
                if (!editingKey) TextButton(onClick = { editingKey = true }) { Text("Cambiar clave", fontSize = 12.sp) }
            } else Text("Añade tu clave de ${state.provider.title}. Las funciones locales del teléfono no necesitan una clave.", Modifier.padding(top = 10.dp), color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
            if (!state.hasKey || editingKey) {
            OutlinedTextField(state.keyDraft, events.onKeyDraft, label = { Text(if (state.hasKey) "Reemplazar clave" else "Tu clave de ${state.provider.title}") },
                modifier = Modifier.fillMaxWidth().padding(top = 15.dp).testTag("api-key"), singleLine = true, shape = RoundedCornerShape(17.dp),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Ocultar clave" else "Mostrar clave") } })
            Button(onClick = { focus.clearFocus(); keyboard?.hide(); events.onSaveKey() }, enabled = state.keyDraft.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 50.dp), shape = RoundedCornerShape(17.dp)) { Text("Guardar clave") }
            if (state.hasKey) TextButton(onClick = { events.onKeyDraft(""); editingKey = false }) { Text("Cancelar reemplazo") }
            }
            TextButton(onClick = { uri.openUri(state.provider.keysUrl) }) { Text("Obtener clave de ${state.provider.title}", color = Mint, fontSize = 12.sp) }
            OutlinedTextField(state.model, events.onModel, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag("ai-model"), singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), shape = RoundedCornerShape(17.dp))
            Text("Usa el identificador exacto de un modelo disponible en tu cuenta. Para interpretar acciones, el modelo debe admitir funciones. Puede requerir saldo; la app no incluye una suscripción.", Modifier.padding(top = 10.dp), color = Muted, fontSize = 11.sp, lineHeight = 19.sp)
            TextButton(onClick = { events.onModel(state.provider.defaultModel) }) { Text("Restablecer modelo sugerido", fontSize = 11.sp) }
            if (state.hasKey) TextButton(onClick = { remove = true }) { Text("Eliminar clave de ${state.provider.title}", color = Muted, fontSize = 12.sp) }
        } }
        item { AssistantCard {
            Icon(Icons.Outlined.Tune, null, tint = Mint, modifier = Modifier.size(28.dp))
            Text("Control del sistema", Modifier.padding(top = 12.dp), color = Cream, fontSize = 21.sp)
            Text("Pide lo que necesitas con tus palabras. Puedes navegar por Android, cambiar brillo y rotación, ajustar volumen y controlar el reproductor activo.", Modifier.padding(top = 12.dp), color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
            Text("Accesibilidad permite Inicio, Atrás, recientes, notificaciones, ajustes rápidos y bloqueo. Xylon no lee la pantalla ni actúa por su cuenta. Tú activas este acceso en Android y puedes revocarlo cuando quieras.", Modifier.padding(top = 12.dp), color = Muted, fontSize = 12.sp, lineHeight = 21.sp)
            OutlinedButton(onClick = events.onControlPermission, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("permission-accessibility")) { Text(if (state.controlEnabled) "Accesibilidad activada" else "Configurar Accesibilidad", fontSize = 12.sp) }
            Text("Si Android muestra Ajuste restringido: Información de la app → menú ⋮ → Permitir ajustes restringidos. Después vuelve a Accesibilidad.", color = Muted, fontSize = 11.sp, lineHeight = 20.sp)
            OutlinedButton(onClick = events.onWriteSettingsPermission, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("permission-system-settings")) { Text(if (state.writeSettingsGranted) "Modificar ajustes permitido" else "Permitir modificar ajustes", fontSize = 12.sp) }
            Text("Modificar ajustes permite cambiar brillo y rotación. Wi-Fi, Bluetooth y ubicación se abren en sus paneles para que confirmes el cambio. El reloj y la fecha funcionan sin permisos adicionales.", Modifier.padding(top = 10.dp), color = Muted, fontSize = 11.sp, lineHeight = 20.sp)
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
        item { Text("XYLON  ·  4.1.0\nInteligencia a tu manera.", color = Muted, fontSize = 11.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("¿Borrar tu historial?") }, text = { Text("Se eliminarán las conversaciones guardadas en esta app.") }, confirmButton = { TextButton(onClick = { events.onClearHistory(); clear = false }) { Text("Borrar") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancelar") } })
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("¿Eliminar la clave de ${state.provider.title}?") }, text = { Text("Las otras claves y las acciones del teléfono seguirán disponibles.") }, confirmButton = { TextButton(onClick = { events.onRemoveKey(); remove = false }) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Cancelar") } })
}
