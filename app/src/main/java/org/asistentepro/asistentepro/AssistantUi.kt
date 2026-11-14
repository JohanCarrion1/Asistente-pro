@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.asistentepro.asistentepro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

val Ink = Color(0xFF05060B)
val Panel = Color(0xFF1A1B23)
val PanelLight = Color(0xFF242631)
val Mint = Color(0xFF9CB4FF)
val Cream = Color(0xFFF4F5FB)
val Muted = Color(0xFFA3A5B7)
val Line = Color(0xFF30323F)
val Aurora = Brush.linearGradient(listOf(Color(0xFF3BCAFF), Color(0xFF7188FF), Color(0xFFA879FF)))
val CardShape = RoundedCornerShape(27.dp)

data class AssistantCallbacks(
    val onPage: (Page) -> Unit = {}, val onDraft: (String) -> Unit = {},
    val onSend: (String) -> Unit = {}, val onMic: () -> Unit = {},
    val onStop: () -> Unit = {}, val onNew: () -> Unit = {},
    val onAction: (DeviceAction) -> Unit = {}, val onKeyDraft: (String) -> Unit = {},
    val onSaveKey: () -> Unit = {}, val onRemoveKey: () -> Unit = {},
    val onReading: (Boolean) -> Unit = {}, val onModel: (String) -> Unit = {},
    val onOpen: (Conversation) -> Unit = {}, val onDelete: (Conversation) -> Unit = {},
    val onClearHistory: () -> Unit = {}, val onDismiss: () -> Unit = {}, val onSpeak: (String) -> Unit = {},
)

@Composable fun BrandImage(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.assistant_brand), "Identidad de Asistente Pro", modifier.clip(RoundedCornerShape(18.dp)))
}

@Composable
fun AsistenteApp(state: AssistantState, events: AssistantCallbacks = AssistantCallbacks(), animate: Boolean = true, drawerOpen: Boolean = false) {
    val drawer = rememberDrawerState(if (drawerOpen) DrawerValue.Open else DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var search by rememberSaveable { mutableStateOf("") }
    fun close() { scope.launch { drawer.close() } }
    MaterialTheme(colorScheme = darkColorScheme(primary = Mint, onPrimary = Ink, background = Ink, onBackground = Cream,
        surface = Panel, onSurface = Cream, surfaceVariant = PanelLight, onSurfaceVariant = Muted, outline = Line,
        error = Color(0xFFFFBCB6)), typography = Typography(bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp))) {
        BackHandler(drawer.isOpen) { close() }
        ModalNavigationDrawer(drawerState = drawer, scrimColor = Color.Black.copy(alpha = .65f), drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxWidth(.85f).widthIn(max = 355.dp), drawerContainerColor = Color(0xFF16171E), drawerShape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 22.dp, top = 18.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandImage(Modifier.size(46.dp)); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("Tu espacio.", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Medium); Text("ASISTENTE PRO", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp) }
                    IconButton(onClick = { close() }) { Icon(Icons.Outlined.Close, "Cerrar menú", tint = Muted) }
                }
                NavigationDrawerItem(label = { Text("Nueva conversación") }, selected = false, icon = { Icon(Icons.Outlined.AddComment, null) }, onClick = { events.onNew(); close() }, modifier = Modifier.padding(horizontal = 14.dp))
                NavigationDrawerItem(label = { Text("Conversaciones") }, selected = state.page == Page.History, icon = { Icon(Icons.Outlined.History, null) }, onClick = { events.onPage(Page.History); close() }, modifier = Modifier.padding(horizontal = 14.dp).semantics { contentDescription = "Abrir historial" })
                OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("Buscar en tu historial", fontSize = 13.sp) }, leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(19.dp)) }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(20.dp).fillMaxWidth())
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    item { Text("RECIENTES", color = Muted, fontSize = 10.sp, letterSpacing = 1.6.sp, modifier = Modifier.padding(vertical = 10.dp)) }
                    if (state.conversations.isEmpty()) item { Text("Las conversaciones que inicies aparecerán aquí.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(vertical = 10.dp)) }
                    items(state.conversations.filter { it.title.contains(search, true) }, key = { it.id }) { item ->
                        Text(item.title, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { events.onOpen(item); close() }.padding(vertical = 14.dp, horizontal = 9.dp), color = Cream, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, lineHeight = 21.sp)
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = Line)
                NavigationDrawerItem(label = { Text("Ajustes y privacidad") }, selected = state.page == Page.Settings, icon = { Icon(Icons.Outlined.Settings, null) }, onClick = { events.onPage(Page.Settings); close() }, modifier = Modifier.padding(14.dp))
                Text("Diseñado para acompañarte.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 28.dp, bottom = 24.dp))
            }
        }) {
            Box(Modifier.fillMaxSize().background(Ink)) {
                Canvas(Modifier.fillMaxSize()) { drawCircle(Brush.radialGradient(listOf(Color(0xFF28336F).copy(alpha = .19f), Color.Transparent)), radius = size.width * .75f, center = androidx.compose.ui.geometry.Offset(size.width * .8f, size.height * .78f)) }
                Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets.statusBars,
                    topBar = { AssistantHeader(state, events, { scope.launch { drawer.open() } }) },
                    bottomBar = { if (state.page == Page.Assistant) Composer(state, events) else SettingsFooter(events) },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (state.error != null || state.notice != null) Notice(state.error ?: state.notice!!, state.error != null, events.onDismiss)
                        when (state.page) {
                            Page.Assistant -> if (state.messages.isEmpty()) AssistantHome(state, events) else ConversationScreen(state, events)
                            Page.History -> ConversationHistory(state, events)
                            Page.Settings -> PreferencesScreen(state, events)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun AssistantHeader(state: AssistantState, events: AssistantCallbacks, menu: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = menu) { Icon(Icons.Outlined.Menu, "Abrir menú", tint = Cream, modifier = Modifier.size(26.dp)) }
        Text(when (state.page) { Page.Assistant -> "Asistente"; Page.History -> "Conversaciones"; Page.Settings -> "Ajustes" }, color = Cream, fontSize = 24.sp, letterSpacing = (-.7).sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
        if (state.page == Page.Assistant) {
            IconButton(onClick = { events.onReading(!state.readAloud) }) { Icon(if (state.readAloud) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff, if (state.readAloud) "Desactivar lectura en voz alta" else "Activar lectura en voz alta", tint = if (state.readAloud) Mint else Muted, modifier = Modifier.size(22.dp)) }
            IconButton(onClick = { events.onPage(Page.Settings) }) { Icon(Icons.Outlined.Tune, "Abrir ajustes", tint = Muted, modifier = Modifier.size(22.dp)) }
        } else IconButton(onClick = { events.onPage(Page.Assistant) }) { Icon(Icons.Outlined.Close, "Volver al asistente", tint = Muted) }
    }
}

@Composable private fun Notice(text: String, error: Boolean, dismiss: () -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(if (error) Color(0xFF35252B) else PanelLight).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f).padding(vertical = 13.dp), color = if (error) Color(0xFFFFCDC8) else Mint, fontSize = 13.sp, lineHeight = 20.sp)
        IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Cerrar aviso", tint = Muted, modifier = Modifier.size(18.dp)) }
    }
}

@Composable private fun Composer(state: AssistantState, events: AssistantCallbacks) {
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))).navigationBarsPadding().imePadding().padding(horizontal = 18.dp, vertical = 14.dp)) {
        if (state.phase != VoicePhase.Idle) {
            Text(when (state.phase) { VoicePhase.Listening -> "Te escucho. Habla con naturalidad."; VoicePhase.Thinking -> "Preparando tu respuesta…"; VoicePhase.Speaking -> "Leyendo la respuesta…"; else -> "" }, color = Mint, fontSize = 12.sp, modifier = Modifier.padding(start = 18.dp, bottom = 10.dp))
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Panel).border(1.dp, Brush.linearGradient(listOf(Color(0xFF465271), Line, Color(0xFF493E66))), RoundedCornerShape(30.dp)).padding(start = 10.dp, end = 7.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandImage(Modifier.size(36.dp)); Spacer(Modifier.width(9.dp))
            BasicTextField(state.draft, events.onDraft, modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 120.dp).padding(vertical = 12.dp).semantics { contentDescription = "Escribe tu mensaje" }, textStyle = TextStyle(color = Cream, fontSize = 15.sp, lineHeight = 23.sp), cursorBrush = SolidColor(Mint), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { events.onSend(state.draft) }), decorationBox = { inner -> Box { if (state.draft.isEmpty()) Text("Pregunta o pide algo…", color = Muted, fontSize = 14.sp); inner() } })
            val busy = state.phase != VoicePhase.Idle
            IconButton(onClick = { if (busy) { if (state.phase == VoicePhase.Thinking) events.onStop() else events.onMic() } else if (state.draft.isNotBlank()) events.onSend(state.draft) else events.onMic() }, modifier = Modifier.size(46.dp).clip(CircleShape).background(if (busy || state.draft.isNotBlank()) Aurora else Brush.linearGradient(listOf(PanelLight, PanelLight)))) {
                Icon(if (busy) Icons.Outlined.Stop else if (state.draft.isNotBlank()) Icons.AutoMirrored.Outlined.Send else Icons.Outlined.MicNone, if (busy) "Detener" else if (state.draft.isNotBlank()) "Enviar mensaje" else "Empezar a hablar", tint = if (busy || state.draft.isNotBlank()) Color.White else Mint, modifier = Modifier.size(23.dp))
            }
        }
        if (state.messages.isNotEmpty()) Text("Comprueba la información importante.", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 9.dp))
    }
}

@Composable private fun SettingsFooter(events: AssistantCallbacks) {
    Box(Modifier.fillMaxWidth().background(Ink).navigationBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp)) {
        TextButton(onClick = { events.onPage(Page.Assistant) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Volver al asistente", color = Mint) }
    }
}
