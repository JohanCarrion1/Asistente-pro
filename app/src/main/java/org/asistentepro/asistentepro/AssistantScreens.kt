package org.asistentepro.asistentepro

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable fun AssistantHome(state: AssistantState, events: AssistantCallbacks) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("A TU LADO", color = Mint, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
                    Text("Tu voz.\nTus posibilidades.", Modifier.padding(top = 14.dp), color = Cream, fontSize = 31.sp, lineHeight = 39.sp, letterSpacing = (-1.1).sp)
                }
                BrandImage(Modifier.size(84.dp))
            }
        }
        item {
            AssistantCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Mint, modifier = Modifier.size(25.dp))
                    Text("Sin guiones. Como tú hablas.", Modifier.padding(start = 12.dp).weight(1f), color = Cream, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium)
                }
                Text(if (state.hasKey) "Habla, pregunta o pide lo que necesitas con tus propias palabras. ${state.provider.title} interpreta tu intención y Xylon se encarga de las acciones disponibles en Android."
                    else "Conecta tu IA favorita y conversa con naturalidad. Sin memorizar comandos ni registrar tu voz.", Modifier.padding(top = 18.dp), color = Muted, fontSize = 14.sp, lineHeight = 24.sp)
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { events.onPage(Page.Settings) }, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF526DFF), contentColor = Color.White)) {
                        Text(if (state.hasKey) "Mi conexión · ${state.provider.title}" else "Conectar mi IA", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp))
                    }
                }
            }
        }
        item {
            AssistantCard {
                Icon(Icons.Outlined.GraphicEq, null, tint = Mint, modifier = Modifier.size(28.dp))
                Text("También fuera de aquí.", Modifier.padding(top = 16.dp), color = Cream, fontSize = 20.sp)
                Text(if (state.handsFreeActive) state.handsFreeStatus else "Activa Manos libres para llamar a Xylon desde otras apps. La tarjeta flotante mantiene la conversación contigo.", Modifier.padding(top = 12.dp), color = Muted, fontSize = 13.sp, lineHeight = 23.sp)
                TextButton(onClick = { events.onPage(Page.Settings) }, modifier = Modifier.padding(top = 10.dp)) {
                    Text(if (state.handsFreeActive) "Gestionar Manos libres" else "Activar Manos libres", color = Mint)
                }
            }
        }
        item {
            Text("Una conversación abierta a tus ideas.", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 8.dp))
        }
    }
}

@Composable fun AssistantCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(CardShape).background(Panel).border(1.dp, Color(0xFF292B38), CardShape).padding(22.dp), content = content)
}

@Composable fun ConversationScreen(state: AssistantState, events: AssistantCallbacks) {
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.phase) { if (state.messages.isNotEmpty()) list.scrollToItem(state.messages.lastIndex) }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        items(state.messages, key = { it.id }) { message ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.user) Alignment.End else Alignment.Start) {
                if (message.user) {
                    SelectionContainer { Text(message.text, Modifier.widthIn(max = 310.dp).clip(RoundedCornerShape(24.dp, 24.dp, 7.dp, 24.dp)).background(Color(0xFF242738)).padding(18.dp), color = Cream, fontSize = 15.sp, lineHeight = 25.sp) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) { BrandImage(Modifier.size(28.dp)); Text("  XYLON", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp) }
                    Spacer(Modifier.height(12.dp))
                    SelectionContainer { Text(message.text, color = Cream, fontSize = 16.sp, lineHeight = 28.sp) }
                    val clipboard = LocalClipboardManager.current
                    Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }) { Icon(Icons.Outlined.ContentCopy, "Copiar respuesta", tint = Muted, modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = { events.onSpeak(message.text) }) { Icon(Icons.Outlined.VolumeUp, "Leer respuesta en voz alta", tint = Muted, modifier = Modifier.size(20.dp)) }
                        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.time)), color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
        if (state.phase == VoicePhase.Thinking) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), color = Mint, strokeWidth = 2.dp); Text("  Pensando contigo…", color = Muted, fontSize = 14.sp) } }
    }
}

@Composable fun ConversationHistory(state: AssistantState, events: AssistantCallbacks) {
    var query by remember { mutableStateOf("") }
    val filtered = state.conversations.filter { it.title.contains(query, true) || it.messages.any { message -> message.text.contains(query, true) } }
    LazyColumn(Modifier.fillMaxSize().testTag("conversation-history"), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Retoma una buena idea.", color = Cream, fontSize = 28.sp, lineHeight = 35.sp, letterSpacing = (-.8).sp)
            Text("Tu historial permanece en este dispositivo.", Modifier.padding(top = 10.dp), color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(top = 22.dp), placeholder = { Text("Buscar conversaciones") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(18.dp), singleLine = true)
        }
        if (filtered.isEmpty()) item {
            AssistantCard { Icon(Icons.Outlined.Forum, null, tint = Mint, modifier = Modifier.size(30.dp)); Text(if (query.isBlank()) "Tu próxima idea empieza aquí." else "No hay coincidencias.", Modifier.padding(top = 15.dp), color = Cream, fontSize = 18.sp); Text("Las conversaciones que inicies se guardarán aquí.", Modifier.padding(top = 10.dp), color = Muted, fontSize = 13.sp, lineHeight = 22.sp); TextButton(onClick = events.onNew) { Text("Nueva conversación", color = Mint) } }
        }
        items(filtered, key = { it.id }) { item ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).clickable { events.onOpen(item) }.padding(start = 18.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(item.title, color = Cream, fontSize = 15.sp, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("${item.messages.size} mensajes · ${SimpleDateFormat("d MMM", Locale("es")).format(Date(item.updatedAt))}", Modifier.padding(top = 7.dp), color = Muted, fontSize = 11.sp) }
                IconButton(onClick = { events.onDelete(item) }) { Icon(Icons.Outlined.DeleteOutline, "Eliminar conversación", tint = Muted, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}
