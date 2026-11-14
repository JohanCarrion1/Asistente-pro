@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.asistentepro.asistentepro

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val Ink = Color(0xFF0D1514)
val Panel = Color(0xFF17211F)
val PanelLight = Color(0xFF202D28)
val Mint = Color(0xFFC1F4D8)
val Cream = Color(0xFFF5F2E9)
val Muted = Color(0xFFA0AFA7)
val Line = Color(0xFF2A3932)

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

@Composable
fun AsistenteApp(state: AssistantState, events: AssistantCallbacks = AssistantCallbacks(), animate: Boolean = true) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Mint, onPrimary = Ink, background = Ink, onBackground = Cream,
            surface = Panel, onSurface = Cream, surfaceVariant = PanelLight, onSurfaceVariant = Muted,
            outline = Line, error = Color(0xFFFFC5B6)),
        typography = Typography(bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp), labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)),
    ) {
        Scaffold(containerColor = Ink, contentWindowInsets = WindowInsets.statusBars,
            topBar = { BrandHeader(state, events) },
            bottomBar = {
                Column(Modifier.background(Ink).navigationBarsPadding().imePadding()) {
                    if (state.page == Page.Assistant) Composer(state, events)
                    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                    if (!keyboard) BottomNavigation(state.page, events.onPage)
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.error != null || state.notice != null) Notice(state.error ?: state.notice!!, state.error != null, events.onDismiss)
                when (state.page) {
                    Page.Assistant -> if (state.messages.isEmpty()) Home(state, events, animate) else Chat(state, events, animate)
                    Page.History -> History(state, events)
                    Page.Settings -> SettingsScreen(state, events)
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(state: AssistantState, events: AssistantCallbacks) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(PanelLight), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_brand), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("asistente", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.7).sp)
            Text("P R O  /  TU COMPAÑERO DIARIO", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        IconButton(onClick = events.onNew, modifier = Modifier.border(1.dp, Line, CircleShape)) {
            Icon(Icons.Outlined.Add, "Nueva conversación", tint = Cream, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Notice(text: String, error: Boolean, dismiss: () -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp))
        .background(if (error) Color(0xFF352923) else PanelLight).padding(start = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f).padding(vertical = 12.dp), color = if (error) Color(0xFFFFD5C3) else Mint, fontSize = 13.sp, lineHeight = 19.sp)
        IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Cerrar aviso", tint = Muted, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun Home(state: AssistantState, events: AssistantCallbacks, animate: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(Mint, CircleShape))
                Text("  UN MOMENTO PARA TI", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp)
            }
            Spacer(Modifier.height(13.dp))
            Text("Haz espacio para\nlo importante.", color = Cream, fontSize = 35.sp, lineHeight = 41.sp,
                fontWeight = FontWeight.Normal, letterSpacing = (-1.6).sp)
            Spacer(Modifier.height(9.dp))
            Text("Tú pones la idea. Yo te ayudo con el resto.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                VoiceOrb(state.phase, animate, Modifier.size(164.dp).clickable(onClickLabel = "Hablar con el asistente", onClick = events.onMic))
                Row(Modifier.clip(CircleShape).background(Panel).padding(horizontal = 13.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.GraphicEq, null, tint = Mint, modifier = Modifier.size(15.dp))
                    Text(if (state.phase == VoicePhase.Listening) "  Te escucho…" else "  A un toque de distancia", fontSize = 11.sp, color = Muted)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Un buen punto de partida", fontSize = 15.sp, color = Cream, fontWeight = FontWeight.Medium)
                Text("PARA TI", color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IdeaCard("Piensa conmigo", "De una idea\na un buen plan.", Icons.Outlined.Lightbulb, Color(0xFFC0DAC5), Modifier.weight(1f)) {
                    events.onSend("Ayúdame a convertir una idea en un plan. Empieza preguntándome qué quiero lograr.")
                }
                IdeaCard("Hazlo más simple", "Menos vueltas.\nMás claridad.", Icons.Outlined.AutoAwesome, Color(0xFFE8D3B8), Modifier.weight(1f)) {
                    events.onSend("Ayúdame a simplificar una tarea o un texto. Pregúntame qué necesito resolver.")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Tu teléfono, a mano", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.Bolt, null, tint = Muted, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DeviceShortcut(if (state.torchOn) "Apagar" else "Linterna", Icons.Outlined.FlashlightOn, state.torchOn) { events.onAction(DeviceAction.Torch(null)) }
                DeviceShortcut("Cámara", Icons.Outlined.PhotoCamera) { events.onAction(DeviceAction.Camera) }
                DeviceShortcut("5 minutos", Icons.Outlined.Timer) { events.onAction(DeviceAction.Timer(300)) }
                DeviceShortcut("Wi-Fi", Icons.Outlined.Wifi) { events.onAction(DeviceAction.Wifi) }
            }
        }
    }
}

@Composable
private fun IdeaCard(title: String, detail: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(23.dp)).background(color).clickable(onClick = onClick).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = Ink, modifier = Modifier.size(23.dp))
            Icon(Icons.Outlined.ArrowOutward, null, tint = Ink.copy(alpha = 0.55f), modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.height(23.dp))
        Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(4.dp))
        Text(detail, color = Ink.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun DeviceShortcut(label: String, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.widthIn(min = 56.dp, max = 76.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(if (selected) Mint else Panel)
            .border(1.dp, if (selected) Mint else Line, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (selected) Ink else Cream, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun VoiceOrb(phase: VoicePhase, animate: Boolean, modifier: Modifier = Modifier) {
    val degrees: Float
    val scale: Float
    if (animate) {
        val transition = rememberInfiniteTransition(label = "orb")
        val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "orbit")
        val pulse by transition.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
        degrees = rotation; scale = pulse
    } else { degrees = 18f; scale = 1f }
    val active = phase == VoicePhase.Listening || phase == VoicePhase.Speaking
    Canvas(modifier.semantics { contentDescription = if (active) "Núcleo de voz activo" else "Núcleo luminoso del asistente" }) {
        val center = this.center
        val radius = size.minDimension * 0.42f
        drawCircle(Brush.radialGradient(listOf(Mint.copy(alpha = if (active) .20f else .10f), Color.Transparent), center, radius * 1.4f), radius * 1.4f, center)
        drawCircle(Mint.copy(alpha = .08f), radius * 1.03f, center, style = Stroke(1.dp.toPx()))
        rotate(degrees, center) {
            for (i in 0..2) {
                rotate(i * 60f, center) {
                    drawOval(Brush.linearGradient(listOf(Mint.copy(alpha = .1f), Mint.copy(alpha = .85f), Color(0xFF538D74).copy(alpha = .45f))),
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - radius * scale, center.y - radius * .45f * scale),
                        size = androidx.compose.ui.geometry.Size(radius * 2 * scale, radius * .9f * scale), style = Stroke(1.8.dp.toPx()))
                }
            }
        }
        val r = radius * .35f
        val path = Path().apply {
            moveTo(center.x, center.y - r)
            cubicTo(center.x + r * .2f, center.y - r * .18f, center.x + r * .2f, center.y - r * .18f, center.x + r, center.y)
            cubicTo(center.x + r * .2f, center.y + r * .18f, center.x + r * .2f, center.y + r * .18f, center.x, center.y + r)
            cubicTo(center.x - r * .2f, center.y + r * .18f, center.x - r * .2f, center.y + r * .18f, center.x - r, center.y)
            cubicTo(center.x - r * .2f, center.y - r * .18f, center.x - r * .2f, center.y - r * .18f, center.x, center.y - r); close()
        }
        drawPath(path, Brush.linearGradient(listOf(Cream, Mint)))
        for (i in 0..5) {
            val angle = (i * 60 + degrees / 2) * PI / 180
            drawCircle(Mint.copy(alpha = .6f), 1.3.dp.toPx(), androidx.compose.ui.geometry.Offset(center.x + cos(angle).toFloat() * radius * 1.1f, center.y + sin(angle).toFloat() * radius * 1.1f))
        }
    }
}

@Composable
private fun Composer(state: AssistantState, events: AssistantCallbacks) {
    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 9.dp, bottom = 6.dp)) {
        if (state.phase == VoicePhase.Listening || state.phase == VoicePhase.Speaking) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.GraphicEq, null, tint = Mint, modifier = Modifier.size(17.dp))
                Text(if (state.phase == VoicePhase.Listening) "  Te escucho. Habla con naturalidad." else "  Leyendo la respuesta…", color = Mint, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(28.dp))
            .padding(start = 17.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(value = state.draft, onValueChange = events.onDraft, modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 120.dp)
                .padding(vertical = 12.dp).semantics { contentDescription = "Escribe tu mensaje" },
                textStyle = TextStyle(color = Cream, fontSize = 15.sp, lineHeight = 21.sp), cursorBrush = SolidColor(Mint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { events.onSend(state.draft) }),
                decorationBox = { inner -> Box { if (state.draft.isEmpty()) Text("Escribe o habla conmigo…", color = Muted, fontSize = 14.sp); inner() } },
            )
            if (state.phase == VoicePhase.Thinking) {
                IconButton(onClick = events.onStop) { Icon(Icons.Outlined.StopCircle, "Detener respuesta", tint = Mint) }
            } else if (state.draft.isNotBlank() && state.phase != VoicePhase.Listening) {
                IconButton(onClick = { events.onSend(state.draft) }, modifier = Modifier.size(46.dp).background(Mint, CircleShape)) {
                    Icon(Icons.AutoMirrored.Outlined.Send, "Enviar mensaje", tint = Ink, modifier = Modifier.size(21.dp))
                }
            } else {
                IconButton(onClick = events.onMic, modifier = Modifier.size(46.dp).background(Mint, CircleShape)) {
                    Icon(if (state.phase == VoicePhase.Listening || state.phase == VoicePhase.Speaking) Icons.Outlined.Stop else Icons.Outlined.MicNone,
                        if (state.phase == VoicePhase.Idle) "Empezar a hablar" else "Detener voz", tint = Ink, modifier = Modifier.size(23.dp))
                }
            }
        }
        if (state.messages.isNotEmpty()) Text("La IA puede equivocarse. Revisa la información importante.", Modifier.fillMaxWidth().padding(top = 8.dp), color = Muted, fontSize = 9.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BottomNavigation(page: Page, onPage: (Page) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceAround) {
        listOf(Triple(Page.Assistant, Icons.Outlined.AutoAwesome, "Asistente"), Triple(Page.History, Icons.Outlined.History, "Historial"), Triple(Page.Settings, Icons.Outlined.Tune, "Ajustes")).forEach { (target, icon, title) ->
            val selected = page == target
            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { onPage(target) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, title, tint = if (selected) Mint else Muted, modifier = Modifier.size(21.dp))
                Text(title, Modifier.padding(top = 4.dp), fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) Mint else Muted)
            }
        }
    }
}

@Composable
private fun Chat(state: AssistantState, events: AssistantCallbacks, animate: Boolean) {
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.phase) { if (state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.lastIndex + if (state.phase == VoicePhase.Thinking) 1 else 0) }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        items(state.messages, key = { it.id }) { message ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.user) Alignment.End else Alignment.Start) {
                if (message.user) {
                    SelectionContainer { Text(message.text, Modifier.widthIn(max = 310.dp).clip(RoundedCornerShape(22.dp, 22.dp, 5.dp, 22.dp)).background(PanelLight).padding(17.dp), color = Cream, fontSize = 15.sp, lineHeight = 24.sp) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = Mint, modifier = Modifier.size(17.dp))
                        Text("  ASISTENTE PRO", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(11.dp))
                    SelectionContainer { Text(message.text, color = Cream, fontSize = 16.sp, lineHeight = 27.sp) }
                    val clipboard = LocalClipboardManager.current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }) { Icon(Icons.Outlined.ContentCopy, "Copiar respuesta", tint = Muted, modifier = Modifier.size(17.dp)) }
                        IconButton(onClick = { events.onSpeak(message.text) }) { Icon(Icons.Outlined.VolumeUp, "Leer respuesta en voz alta", tint = Muted, modifier = Modifier.size(20.dp)) }
                        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.time)), color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
        if (state.phase == VoicePhase.Thinking) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoiceOrb(VoicePhase.Thinking, animate, Modifier.size(44.dp))
                Text("Dándole una vuelta…", color = Muted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun History(state: AssistantState, events: AssistantCallbacks) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionHeading("Ideas que se quedan.", "Tus conversaciones, solo en este dispositivo.") }
        if (state.conversations.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Forum, null, tint = Mint, modifier = Modifier.size(42.dp))
                Text("Aquí empieza tu historia", Modifier.padding(top = 20.dp), color = Cream, fontSize = 20.sp)
                Text("Una pregunta puede ser un buen comienzo.", Modifier.padding(top = 9.dp), color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Button(onClick = events.onNew, modifier = Modifier.padding(top = 22.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint)) { Text("Nueva conversación", color = Ink) }
            }
        }
        items(state.conversations, key = { it.id }) { item ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).clickable { events.onOpen(item) }.padding(start = 17.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = Cream, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${item.messages.size} mensajes · ${SimpleDateFormat("d MMM", Locale("es")).format(Date(item.updatedAt))}", Modifier.padding(top = 7.dp), color = Muted, fontSize = 11.sp)
                }
                IconButton(onClick = { events.onDelete(item) }) { Icon(Icons.Outlined.DeleteOutline, "Eliminar conversación", tint = Muted, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Text(title, color = Cream, fontSize = 30.sp, lineHeight = 37.sp, letterSpacing = (-1).sp)
    Text(subtitle, Modifier.padding(top = 10.dp, bottom = 12.dp), color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
}

@Composable
private fun SettingsScreen(state: AssistantState, events: AssistantCallbacks) {
    var visible by rememberSaveable { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var removeDialog by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { SectionHeading("A tu manera.", "Un asistente que encaja contigo.") }
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Key, null, tint = Mint, modifier = Modifier.size(23.dp))
                    Text("  Inteligencia con Groq", color = Cream, fontWeight = FontWeight.Medium, fontSize = 17.sp)
                }
                Text(if (state.hasKey) "Clave guardada en este dispositivo" else "Conecta tu clave para empezar a conversar.", Modifier.padding(top = 10.dp), color = if (state.hasKey) Mint else Muted, fontSize = 12.sp, lineHeight = 19.sp)
                OutlinedTextField(value = state.keyDraft, onValueChange = events.onKeyDraft, modifier = Modifier.fillMaxWidth().padding(top = 17.dp),
                    label = { Text(if (state.hasKey) "Reemplazar clave" else "Tu clave de Groq") }, placeholder = { Text("gsk_…") }, singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Ocultar clave" else "Mostrar clave") } }, shape = RoundedCornerShape(16.dp))
                Button(onClick = events.onSaveKey, enabled = state.keyDraft.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp)) { Text("Guardar clave") }
                TextButton(onClick = { uri.openUri("https://console.groq.com/keys") }) { Text("Obtener una clave en Groq", color = Mint, fontSize = 12.sp); Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.OpenInNew, null, Modifier.size(14.dp)) }
                if (state.hasKey) TextButton(onClick = { removeDialog = true }) { Text("Eliminar clave guardada", color = Muted, fontSize = 12.sp) }
            }
        }
        item {
            SettingsCard {
                Text("El ritmo de tus respuestas", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                ModelOption("Ágil", "Para el día a día · Llama 3.1 8B", state.model == "llama-3.1-8b-instant") { events.onModel("llama-3.1-8b-instant") }
                ModelOption("Profundo", "Para ideas complejas · Llama 3.3 70B", state.model == "llama-3.3-70b-versatile") { events.onModel("llama-3.3-70b-versatile") }
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Respuestas en voz alta", color = Cream, fontSize = 14.sp)
                        Text("Con la voz en español de Android", Modifier.padding(top = 4.dp), color = Muted, fontSize = 11.sp)
                    }
                    Switch(state.readAloud, events.onReading)
                }
            }
        }
        item {
            SettingsCard {
                Icon(Icons.Outlined.Shield, null, tint = Mint, modifier = Modifier.size(25.dp))
                Text("Tu espacio es tuyo.", Modifier.padding(top = 13.dp), color = Cream, fontSize = 18.sp)
                Text("La clave se cifra con Android Keystore. Tu historial se guarda en este dispositivo. Al consultar la IA, se envían a Groq los últimos mensajes necesarios para responder. La voz utiliza el servicio de Android.", Modifier.padding(top = 10.dp), color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
                TextButton(onClick = { clearDialog = true }, enabled = state.conversations.isNotEmpty()) { Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(17.dp)); Text("  Borrar historial", fontSize = 12.sp) }
            }
        }
        item { Text("ASISTENTE PRO  /  2.0\nHecho para estar cerca, sin estorbar.", Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Muted, textAlign = TextAlign.Center, fontSize = 10.sp, lineHeight = 19.sp) }
    }
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text("¿Borrar tu historial?") }, text = { Text("Se eliminarán las conversaciones guardadas en este dispositivo.") }, confirmButton = { TextButton(onClick = { events.onClearHistory(); clearDialog = false }) { Text("Borrar") } }, dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancelar") } })
    if (removeDialog) AlertDialog(onDismissRequest = { removeDialog = false }, title = { Text("¿Eliminar la clave?") }, text = { Text("Podrás seguir usando las acciones del teléfono. Para conversar con la IA tendrás que añadir una clave.") }, confirmButton = { TextButton(onClick = { events.onRemoveKey(); removeDialog = false }) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = { removeDialog = false }) { Text("Cancelar") } })
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(24.dp)).padding(20.dp), content = content)
}

@Composable
private fun ModelOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Column { Text(title, color = Cream, fontSize = 14.sp); Text(subtitle, color = Muted, fontSize = 10.sp, lineHeight = 16.sp) }
    }
}
