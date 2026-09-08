#!/usr/bin/env python3
"""
Asistente Pro - Frontend Kivy
==============================
Interfaz gráfica profesional para Android/Linux con Material Design.
Conecta con el backend Flask (localhost:5000) para dar respuestas de IA
ultra rápidas mediante Groq.

Pantallas:
    - Bienvenida (banner ASCII animado)
    - Principal (chat con IA + historial scrollable)
    - Acciones rápidas (grid de botones del dispositivo)
    - Sistema (información del dispositivo en tiempo real)

Autor: Asistente Pro Team
Version: 1.0
"""

import os
import time
import json
import threading
from datetime import datetime

import requests
from kivy.app import App
from kivy.uix.screenmanager import ScreenManager, Screen, SlideTransition
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.scrollview import ScrollView
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.uix.textinput import TextInput
from kivy.uix.image import Image
from kivy.uix.spinner import Spinner
from kivy.clock import Clock
from kivy.core.window import Window
from kivy.metrics import dp, sp
from kivy.graphics import Color, Rectangle, RoundedRectangle
from kivy.utils import get_color_from_hex

# ---------------------------------------------------------------------------
# Configuración
# ---------------------------------------------------------------------------
BACKEND_URL = "http://127.0.0.1:5000"

# Paleta Material Design oscura
COLORES = {
    "fondo": "#121212",
    "superficie": "#1E1E2E",
    "tarjeta": "#2A2A3E",
    "primario": "#7C4DFF",
    "primario_claro": "#B388FF",
    "secundario": "#03DAC6",
    "error": "#CF6679",
    "texto": "#FFFFFF",
    "texto_tenue": "#B0B0C0",
    "acento": "#00E5FF",
    "verde": "#69F0AE",
}

# Acciones disponibles en el grid
ACCIONES = ["Vibrar", "Linterna", "Batería", "Foto", "Ubicación", "SMS", "Apps"]

# Íconos emoji por acción
ICONOS = {
    "Vibrar": "📳",
    "Linterna": "🔦",
    "Batería": "🔋",
    "Foto": "📷",
    "Ubicación": "📍",
    "SMS": "💬",
    "Apps": "📱",
}

try:
    Window.clearcolor = get_color_from_hex(COLORES["fondo"])
    Window.size = (420, 780)
except Exception:
    pass


# ---------------------------------------------------------------------------
# Utilidades
# ---------------------------------------------------------------------------
def peticion_post(ruta, datos=None, archivo=None):
    """Realiza una petición POST al backend y devuelve JSON."""
    url = f"{BACKEND_URL}{ruta}"
    try:
        if archivo is not None:
            with open(archivo, "rb") as f:
                resp = requests.post(url, files={"audio": f}, timeout=15)
        else:
            resp = requests.post(url, json=datos or {}, timeout=30)
        return resp.json() if resp.ok else {"error": "Backend respondió mal"}
    except requests.exceptions.ConnectionError:
        return {"error": "No se pudo conectar al backend"}
    except Exception:
        return {"error": "Error de comunicación con el backend"}


def peticion_get(ruta):
    """Realiza una petición GET al backend."""
    try:
        resp = requests.get(f"{BACKEND_URL}{ruta}", timeout=10)
        return resp.json() if resp.ok else {}
    except Exception:
        return {}


# ---------------------------------------------------------------------------
# Widget reutilizable: botón con redondeado
# ---------------------------------------------------------------------------
class BotonRedondo(Button):
    def __init__(
        self,
        texto="",
        color_fondo="primario",
        tamano=0.12,
        bold=False,
        tamano_texto=16,
        **kwargs,
    ):
        super().__init__(**kwargs)
        self.text = texto
        self.background_normal = ""
        self.background_color = (0, 0, 0, 0)
        self.color = get_color_from_hex(COLORES["texto"])
        self.font_size = sp(tamano_texto)
        self.size_hint_y = tamano
        self.bold = bold
        self.color_fondo_hex = COLORES[color_fondo]
        self.bind(pos=self._dibujar, size=self._dibujar)

    def _dibujar(self, *args):
        self.canvas.before.clear()
        with self.canvas.before:
            Color(*get_color_from_hex(self.color_fondo_hex))
            RoundedRectangle(
                pos=self.pos, size=self.size, radius=[dp(16), dp(16), dp(16), dp(16)]
            )


# ---------------------------------------------------------------------------
# Widget: burbuja de chat
# ---------------------------------------------------------------------------
class BurbujaChat(BoxLayout):
    def __init__(self, mensaje, es_usuario=False, **kwargs):
        super().__init__(**kwargs)
        self.orientation = "horizontal"
        self.size_hint_y = None
        self.padding = (dp(8), dp(4))
        self.spacing = dp(8)
        self.es_usuario = es_usuario

        color = COLORES["primario"] if es_usuario else COLORES["tarjeta"]
        alineacion = "right" if es_usuario else "left"

        etiqueta = Label(
            text=mensaje,
            size_hint_y=None,
            font_size=sp(15),
            color=get_color_from_hex(COLORES["texto"]),
            halign=alineacion,
            valign="middle",
        )
        etiqueta.bind(
            texture_size=lambda *a: setattr(etiqueta, "height", etiqueta.texture_size[1] + dp(16))
        )
        self.add_widget(etiqueta)
        self.bind(size=lambda *a: setattr(etiqueta, "height", etiqueta.texture_size[1] + dp(16)))

        # Fondo burbuja
        self.etiqueta = etiqueta
        self.color_hex = color
        self.bind(pos=self._pintar, size=self._pintar)

    def _pintar(self, *args):
        pass


# ---------------------------------------------------------------------------
# Pantalla 1: Bienvenida con banner animado
# ---------------------------------------------------------------------------
class PantallaBienvenida(Screen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.name = "bienvenida"

        layout = BoxLayout(orientation="vertical", padding=dp(24), spacing=dp(12))
        layout.bind(size=self._redimensionar)

        self.lbl_titulo = Label(
            text="ASISTENTE PRO",
            font_size=sp(34),
            bold=True,
            color=get_color_from_hex(COLORES["primario_claro"]),
            halign="center",
        )
        layout.add_widget(self.lbl_titulo)

        self.lbl_banner = Label(
            text="",
            font_size=sp(10),
            color=get_color_from_hex(COLORES["acento"]),
            halign="center",
            font_name="DejaVuSansMono",
        )
        layout.add_widget(self.lbl_banner)

        self.lbl_sub = Label(
            text="IA potente con Groq · Control por voz y texto",
            font_size=sp(15),
            color=get_color_from_hex(COLORES["texto_tenue"]),
            halign="center",
        )
        layout.add_widget(self.lbl_sub)

        layout.add_widget(Label(size_hint_y=0.3))

        btn_iniciar = BotonRedondo("▶  INICIAR ASISTENTE", "primario", 0.1, True, 18)
        btn_iniciar.bind(on_release=lambda *a: self.ir_principal())
        layout.add_widget(btn_iniciar)

        btn_ayuda = BotonRedondo("¿Cómo funciona?", "tarjeta", 0.08, False, 14)
        btn_ayuda.bind(on_release=lambda *a: self.mostrar_ayuda())
        layout.add_widget(btn_ayuda)

        self.estado_backend = Label(
            text="Conectando con el backend...",
            font_size=sp(12),
            color=get_color_from_hex(COLORES["texto_tenue"]),
            halign="center",
        )
        self.estado_backend.bind(on_texture_size=lambda *a: setattr(
            self.estado_backend, "text_size", (self.width * 0.9, None)))
        layout.add_widget(self.estado_backend)

        self.add_widget(layout)

        # Animar banner
        self.frames = 0
        self.animacion = Clock.schedule_interval(self.animar_banner, 1.0 / 15.0)
        Clock.schedule_once(self.verificar_backend, 0.1)

    def _redimensionar(self, *args):
        self.lbl_titulo.text_size = (self.width, None)
        self.lbl_banner.text_size = (self.width, None)
        self.lbl_sub.text_size = (self.width * 0.9, None)

    def _texto_robot(self, cambios=2):
        """Simula un robot ASCII para el banner animado."""
        patron = [
            "   ___________________  ",
            "   |  ________________ |  ",
            "   | |  ============  | |",
            "   | |  <  <  <  <  <  | |",
            "   | |_________________| |",
            "   |_____________________|",
            "      ____[      ]____    ",
            "     /\\ |   |  |   | /\\   ",
            "    /  \\|   |  |   |/  \\  ",
            "   /    \\____|__|____/    ",
            "   \\     |    |    |     / ",
            "    \\    |____|____|    /  ",
            "     \\_________________/   ",
            "      |   \\___/   |        ",
            "      \\___________/        ",
        ]
        if cambios and self.frames % 8 == 0:
            # Ojos brillantes en momentos alternos
            if self.frames % 16 < 8:
                patron[3] = "   | |  *  *  *  *  *  | |"
        return "\n".join(patron)

    def animar_banner(self, dt):
        self.frames += 1
        base = self._texto_robot()
        self.lbl_banner.text = base
        if self.frames % 5 == 0:
            color = get_color_from_hex(
                COLORES["acento"] if self.frames % 10 < 5 else COLORES["secundario"]
            )
            self.lbl_banner.color = color

    def verificar_backend(self, dt):
        estado = peticion_get("/")
        if estado.get("status") == "online":
            self.estado_backend.text = (
                f"✅ Backend {estado.get('status')} · modelo {estado.get('modelo')}"
            )
            self.estado_backend.color = get_color_from_hex(COLORES["verde"])
        else:
            self.estado_backend.text = (
                "⚠️ Backend no detectado. Ejecuta: ./run.sh"
            )
            self.estado_backend.color = get_color_from_hex(COLORES["error"])
            Clock.schedule_once(self.verificar_backend, 3)

    def ir_principal(self):
        self.animacion.cancel()
        app = App.get_running_app()
        app.cambiar_pantalla("principal")

    def mostrar_ayuda(self):
        app = App.get_running_app()
        app.mostrar_toast(
            "Envía texto o usa el micrófono. Prueba: 'manda vibrar', "
            "'dame el estado de la batería' o 'captura una foto'."
        )


# ---------------------------------------------------------------------------
# Pantalla 2: Chat principal
# ---------------------------------------------------------------------------
class PantallaPrincipal(Screen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.name = "principal"
        self.historial = []
        self.escribiendo = False

        layout = BoxLayout(orientation="vertical", spacing=dp(6), padding=(dp(10), dp(6)))

        # Barra superior
        barra = BoxLayout(size_hint_y=None, height=dp(52), spacing=dp(10))
        btn_menu = Button(
            text="☰",
            size_hint=(None, 1),
            width=dp(48),
            font_size=sp(22),
            background_normal="",
            background_color=get_color_from_hex(COLORES["tarjeta"]),
            color=get_color_from_hex(COLORES["texto"]),
        )
        btn_menu.bind(on_release=lambda *a: self.abrir_menu())
        barra.add_widget(btn_menu)

        self.lbl_titulo = Label(
            text="Asistente Pro 🤖",
            font_size=sp(20),
            bold=True,
            color=get_color_from_hex(COLORES["texto"]),
            halign="left",
        )
        barra.add_widget(self.lbl_titulo)

        btn_buscar = Button(
            text="🔍",
            size_hint=(None, 1),
            width=dp(48),
            font_size=sp(18),
            background_normal="",
            background_color=get_color_from_hex(COLORES["tarjeta"]),
            color=get_color_from_hex(COLORES["texto"]),
        )
        btn_buscar.bind(on_release=lambda *a: self.abrir_buscador())
        barra.add_widget(btn_buscar)
        layout.add_widget(barra)

        # Indicador escribiendo
        self.lbl_escribiendo = Label(
            text="",
            size_hint_y=None,
            height=dp(0),
            font_size=sp(13),
            color=get_color_from_hex(COLORES["secundario"]),
            halign="left",
        )
        layout.add_widget(self.lbl_escribiendo)

        # Área de historial (scroll)
        self.scroll = ScrollView(size_hint=(1, 1))
        self.contenedor_chat = BoxLayout(
            orientation="vertical", size_hint_y=None, spacing=dp(8), padding=dp(6)
        )
        self.contenedor_chat.bind(minimum_height=self.contenedor_chat.setter("height"))
        self.scroll.add_widget(self.contenedor_chat)
        layout.add_widget(self.scroll)

        self.mensaje_inicial()

        # Entrada de texto
        input_superior = BoxLayout(size_hint_y=None, height=dp(64), spacing=dp(8))
        self.input_texto = TextInput(
            hint_text="Escribe tu mensaje...",
            multiline=False,
            font_size=sp(16),
            padding=(dp(12), dp(12)),
            background_color=get_color_from_hex(COLORES["tarjeta"]),
            foreground_color=get_color_from_hex(COLORES["texto"]),
            cursor_color=get_color_from_hex(COLORES["primario_claro"]),
            hint_text_color=get_color_from_hex(COLORES["texto_tenue"]),
            size_hint_x=0.75,
        )
        self.input_texto.bind(on_text_validate=lambda *a: self.enviar_mensaje())
        input_superior.add_widget(self.input_texto)

        btn_mic = Button(
            text="🎤",
            size_hint_x=0.12,
            font_size=sp(18),
            background_normal="",
            background_color=get_color_from_hex(COLORES["secundario"]),
            color=get_color_from_hex(COLORES["fondo"]),
        )
        btn_mic.bind(on_release=lambda *a: self.reconocer_voz())
        input_superior.add_widget(btn_mic)

        btn_enviar = Button(
            text="➤",
            size_hint_x=0.13,
            font_size=sp(20),
            bold=True,
            background_normal="",
            background_color=get_color_from_hex(COLORES["primario"]),
            color=get_color_from_hex(COLORES["texto"]),
        )
        btn_enviar.bind(on_release=lambda *a: self.enviar_mensaje())
        input_superior.add_widget(btn_enviar)
        layout.add_widget(input_superior)

        self.add_widget(layout)

    def mensaje_inicial(self):
        self.agregar_mensaje(
            "¡Hola! Soy Asistente Pro 🤖\n"
            "Pregúntame lo que quieras o usa las acciones rápidas. "
            "Soy impulsado por Groq: respuesta ultra rápida 🚀",
            es_usuario=False,
        )

    def abrir_menu(self):
        app = App.get_running_app()
        app.ir_menu()

    def abrir_buscador(self):
        app = App.get_running_app()
        app.abrir_buscador()

    def agregar_mensaje(self, texto, es_usuario=True):
        """Añade una burbuja al historial y hace scroll."""
        burbuja = BurbujaChat(texto, es_usuario=es_usuario)
        self.contenedor_chat.add_widget(burbuja)
        self.historial.append({"texto": texto, "usuario": es_usuario})
        Clock.schedule_once(self._scroll_final, 0.1)

    def _scroll_final(self, dt):
        self.scroll.scroll_y = 0

    def set_escribiendo(self, activo):
        def _set(dt):
            if activo:
                self.lbl_escribiendo.text = "🤖 Escribiendo..."
                self.lbl_escribiendo.height = dp(28)
            else:
                self.lbl_escribiendo.text = ""
                self.lbl_escribiendo.height = dp(0)
        Clock.schedule_once(_set)

    def enviar_mensaje(self, texto=None):
        if self.escribiendo:
            return
        texto = (texto or self.input_texto.text).strip()
        if not texto:
            return
        self.input_texto.text = ""
        self.agregar_mensaje(texto, es_usuario=True)
        self.set_escribiendo(True)
        self.escribiendo = True
        threading.Thread(target=self._consulta_ia, args=(texto,), daemon=True).start()

    def _consulta_ia(self, texto):
        resultado = peticion_post("/comando", {"texto": texto})
        if "error" in resultado:
            respuesta = f"⚠️ {resultado['error']}"
        else:
            respuesta = resultado.get("respuesta", "Sin respuesta")
            tiempo = resultado.get("tiempo", "")
            respuesta = f"{respuesta}\n\n(Duración: {tiempo})"
        self.set_escribiendo(False)
        self.escribiendo = False
        self.agregar_mensaje(respuesta, es_usuario=False)

    def reconocer_voz(self):
        """Graba y convierte voz a texto usando AndroidSpeechRecognizer si existe."""
        app = App.get_running_app()
        try:
            from android_speech import AndroidSpeechRecognizer
        except ImportError:
            app.mostrar_toast("🎤 Voz no disponible en esta plataforma")
            return

        def grabar():
            recon = AndroidSpeechRecognizer()
            recon.startListening()
            time.sleep(3)
            recon.stopListening()
            texto = recon.getResults().get("bestResult", "")
            if texto:
                self.enviar_mensaje(texto)
            else:
                app.mostrar_toast("No se escuchó nada")

        threading.Thread(target=grabar, daemon=True).start()
        app.mostrar_toast("🎙️ Escuchando...")


# ---------------------------------------------------------------------------
# Pantalla 3: Acciones rápidas
# ---------------------------------------------------------------------------
class PantallaAcciones(Screen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.name = "acciones"

        layout = BoxLayout(orientation="vertical", padding=dp(16), spacing=dp(12))

        cabecera = BoxLayout(size_hint_y=None, height=dp(52), spacing=dp(10))
        btn_volver = Button(
            text="←",
            size_hint=(None, 1),
            width=dp(48),
            font_size=sp(22),
            background_normal="",
            background_color=get_color_from_hex(COLORES["tarjeta"]),
            color=get_color_from_hex(COLORES["texto"]),
        )
        btn_volver.bind(on_release=lambda *a: App.get_running_app().cambiar_pantalla("principal"))
        cabecera.add_widget(btn_volver)
        cabecera.add_widget(Label(
            text="⚡ Acciones rápidas",
            font_size=sp(22),
            bold=True,
            color=get_color_from_hex(COLORES["texto"]),
        ))
        layout.add_widget(cabecera)

        grid = GridLayout(cols=2, spacing=dp(14), size_hint_y=1)
        grid.bind(minimum_height=grid.setter("height"))

        colores_btn = ["primario", "secundario", "verde", "acento",
                       "primario_claro", "primario", "secundario", "verde"]

        for i, nombre in enumerate(ACCIONES):
            accion_key = nombre.lower()
            btn = Button(
                text=f"{ICONOS.get(nombre, '🔧')}\n{nombre}",
                size_hint_y=None,
                height=dp(100),
                font_size=sp(16),
                background_normal="",
                background_color=get_color_from_hex(COLORES[colores_btn[i]]),
                color=get_color_from_hex(COLORES["fondo"]),
                bold=True,
            )
            btn.bind(
                on_release=lambda b, n=accion_key: self.ejecutar_accion(n)
            )
            grid.add_widget(btn)

        layout.add_widget(grid)
        barra_texto = BoxLayout(size_hint_y=None, height=dp(56), spacing=dp(10))
        self.input_accion = TextInput(
            hint_text="O escribe tu acción...",
            multiline=False,
            font_size=sp(15),
            background_color=get_color_from_hex(COLORES["tarjeta"]),
            foreground_color=get_color_from_hex(COLORES["texto"]),
            cursor_color=get_color_from_hex(COLORES["primario_claro"]),
        )
        self.input_accion.bind(
            on_text_validate=lambda *a: self.enviar_texto_accion()
        )
        barra_texto.add_widget(self.input_accion)
        btn_ok = BotonRedondo("Ejecutar", "primario", 1, True, 14)
        btn_ok.size_hint_x = 0.3
        btn_ok.bind(on_release=lambda *a: self.enviar_texto_accion())
        barra_texto.add_widget(btn_ok)
        layout.add_widget(barra_texto)

        self.lbl_resultado = Label(
            text="Selecciona una acción para ejecutarla",
            font_size=sp(14),
            color=get_color_from_hex(COLORES["texto_tenue"]),
            halign="center",
            size_hint_y=None,
            height=dp(40),
        )
        layout.add_widget(self.lbl_resultado)

        self.add_widget(layout)

    def ejecutar_accion(self, nombre):
        self.lbl_resultado.text = "⏳ Ejecutando..."
        self.lbl_resultado.color = get_color_from_hex(COLORES["acento"])
        threading.Thread(target=self._chequear, args=(nombre,), daemon=True).start()

    def _chequear(self, nombre):
        resultado = peticion_post(f"/accion/{nombre}")
        def _actualizar():
            if "error" in resultado:
                self.lbl_resultado.text = f"❌ {resultado['error']}"
                self.lbl_resultado.color = get_color_from_hex(COLORES["error"])
            else:
                self.lbl_resultado.text = f"✅ {resultado.get('mensaje', 'Hecho')}"
                self.lbl_resultado.color = get_color_from_hex(COLORES["verde"])
        Clock.schedule_once(_actualizar)

    def enviar_texto_accion(self):
        texto = self.input_accion.text.strip().lower()
        if not texto:
            return
        self.input_accion.text = ""
        self.ejecutar_accion(texto.replace(" ", ""))


# ---------------------------------------------------------------------------
# Pantalla 4: Sistema
# ---------------------------------------------------------------------------
class PantallaSistema(Screen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.name = "sistema"

        layout = BoxLayout(orientation="vertical", padding=dp(16), spacing=dp(12))

        cabecera = BoxLayout(size_hint_y=None, height=dp(52), spacing=dp(10))
        btn_volver = Button(
            text="←",
            size_hint=(None, 1),
            width=dp(48),
            font_size=sp(22),
            background_normal="",
            background_color=get_color_from_hex(COLORES["tarjeta"]),
            color=get_color_from_hex(COLORES["texto"]),
        )
        btn_volver.bind(on_release=lambda *a: App.get_running_app().cambiar_pantalla("principal"))
        cabecera.add_widget(btn_volver)
        cabecera.add_widget(Label(
            text="🖥️ Sistema",
            font_size=sp(22),
            bold=True,
            color=get_color_from_hex(COLORES["texto"]),
        ))
        layout.add_widget(cabecera)

        self.lbl_info = Label(
            text="Cargando información del sistema...",
            font_size=sp(16),
            color=get_color_from_hex(COLORES["texto"]),
            halign="left",
            valign="top",
            size_hint_y=1,
        )
        self.lbl_info.bind(
            on_texture_size=lambda *a: setattr(
                self.lbl_info, "text_size", (self.width * 0.9, None)
            )
        )
        layout.add_widget(self.lbl_info)

        btn_actualizar = BotonRedondo("🔄 Actualizar", "primario", 0.1, True, 16)
        btn_actualizar.bind(on_release=lambda *a: self.actualizar())
        layout.add_widget(btn_actualizar)

        self.add_widget(layout)
        Clock.schedule_once(lambda *a: self.actualizar(), 1.0)
        self.timer = Clock.schedule_interval(lambda *a: self.actualizar(), 5)

    def actualizar(self):
        def _tarea():
            datos = peticion_get("/sistema")
            def _mostrar(dt):
                info = (
                    "📊 Estado del dispositivo\n\n"
                    f"  🖧 CPU:     {datos.get('cpu_percent', 0)}%\n"
                    f"  🧠 RAM:     {datos.get('ram_percent', 0)}%"
                    f" ({self._format_bytes(datos.get('ram_usada', 0))} / "
                    f"{self._format_bytes(datos.get('ram_total', 0))})\n"
                    f"  🔋 Batería: {datos.get('bateria', 100)}%\n"
                    f"  💻 Sistema: {datos.get('sistema', '?')}\n"
                    f"  📟 Host:    {datos.get('hostname', '?')}\n"
                    f"  🗓️  Hora:    {datetime.now().strftime('%H:%M:%S')}"
                )
                self.lbl_info.text = info
            Clock.schedule_once(_mostrar)
        threading.Thread(target=_tarea, daemon=True).start()

    def _format_bytes(self, num):
        for unidad in ["B", "KB", "MB", "GB"]:
            if num < 1024:
                return f"{num:.1f}{unidad}"
            num /= 1024
        return f"{num:.1f}TB"


# ---------------------------------------------------------------------------
# Pantalla 5: Buscador del historial
# ---------------------------------------------------------------------------
class PantallaBuscador(Screen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.name = "buscador"

        layout = BoxLayout(orientation="vertical", padding=dp(16), spacing=dp(10))

        cabecera = BoxLayout(size_hint_y=None, height=dp(52), spacing=dp(10))
        btn_volver = Button(
            text="←",
            size_hint=(None, 1),
            width=dp(48),
            font_size=sp(22),
            background_normal="",
            background_color=get_color_from_hex(COLORES["tarjeta"]),
            color=get_color_from_hex(COLORES["texto"]),
        )
        btn_volver.bind(on_release=lambda *a: App.get_running_app().cambiar_pantalla("principal"))
        cabecera.add_widget(btn_volver)
        cabecera.add_widget(Label(
            text="🔍 Historial",
            font_size=sp(22),
            bold=True,
            color=get_color_from_hex(COLORES["texto"]),
        ))
        layout.add_widget(cabecera)

        self.input_busqueda = TextInput(
            hint_text="Buscar en el historial...",
            multiline=False,
            font_size=sp(16),
            background_color=get_color_from_hex(COLORES["tarjeta"]),
            foreground_color=get_color_from_hex(COLORES["texto"]),
            cursor_color=get_color_from_hex(COLORES["primario_claro"]),
        )
        self.input_busqueda.bind(text=self.filtrar)
        layout.add_widget(self.input_busqueda)

        self.scroll = ScrollView(size_hint=(1, 1))
        self.contenedor = BoxLayout(orientation="vertical", size_hint_y=None, spacing=dp(6))
        self.contenedor.bind(minimum_height=self.contenedor.setter("height"))
        self.scroll.add_widget(self.contenedor)
        layout.add_widget(self.scroll)

        self.add_widget(layout)

    def on_enter(self):
        self.filtrar(self.input_busqueda, "")

    def filtrar(self, *args):
        self.contenedor.clear_widgets()
        busqueda = self.input_busqueda.text.lower().strip()
        principal = App.get_running_app().pantalla_principal
        for item in principal.historial:
            texto = item["texto"]
            if busqueda and busqueda not in texto.lower():
                continue
            prefijo = "🧑 " if item["usuario"] else "🤖 "
            etiqueta = Label(
                text=prefijo + texto,
                size_hint_y=None,
                font_size=sp(13),
                color=get_color_from_hex(
                    COLORES["primario_claro"] if item["usuario"] else COLORES["texto"]
                ),
                halign="left",
                valign="top",
            )
            etiqueta.bind(
                texture_size=lambda e: setattr(e, "height", e.texture_size[1] + dp(8))
                if e.texture_size else None
            )
            self.contenedor.add_widget(etiqueta)


# ---------------------------------------------------------------------------
# Aplicación principal
# ---------------------------------------------------------------------------
class AsistenteProApp(App):
    def build(self):
        self.title = "Asistente Pro"
        self.icon = "icons/icon.png" if os.path.exists("icons/icon.png") else ""

        sm = ScreenManager(transition=SlideTransition(duration=0.3))

        self.pantalla_bienvenida = PantallaBienvenida()
        self.pantalla_principal = PantallaPrincipal()
        self.pantalla_acciones = PantallaAcciones()
        self.pantalla_sistema = PantallaSistema()
        self.pantalla_buscador = PantallaBuscador()

        sm.add_widget(self.pantalla_bienvenida)
        sm.add_widget(self.pantalla_principal)
        sm.add_widget(self.pantalla_acciones)
        sm.add_widget(self.pantalla_sistema)
        sm.add_widget(self.pantalla_buscador)

        sm.current = "bienvenida"
        return sm

    def cambiar_pantalla(self, nombre):
        self.root.current = nombre

    def ir_menu(self):
        """Muestra un menú con opciones: acciones, sistema, buscador."""
        opciones = ["⚡ Acciones", "🖥️ Sistema", "🔍 Historial"]
        from kivy.uix.dropdown import DropDown

        menu = DropDown()
        for opcion in opciones:
            btn = Button(
                text=opcion,
                size_hint_y=None,
                height=dp(44),
                background_normal="",
                background_color=get_color_from_hex(COLORES["tarjeta"]),
                color=get_color_from_hex(COLORES["texto"]),
            )
            if opcion == "⚡ Acciones":
                btn.bind(on_release=lambda b: (menu.dismiss(), self.cambiar_pantalla("acciones")))
            elif opcion == "🖥️ Sistema":
                btn.bind(on_release=lambda b: (menu.dismiss(), self.cambiar_pantalla("sistema")))
            else:
                btn.bind(on_release=lambda b: (menu.dismiss(), self.cambiar_pantalla("buscador")))
            menu.add_widget(btn)

        btn_ref = App.get_running_app().pantalla_principal.children[0].children[0].children[0]
        menu.open(btn_ref)

    def abrir_buscador(self):
        self.cambiar_pantalla("buscador")

    def mostrar_toast(self, mensaje):
        """Muestra un toast simple en la pantalla actual."""
        from kivy.uix.popup import Popup
        from kivy.uix.label import Label

        etiqueta = Label(
            text=mensaje,
            font_size=sp(15),
            color=get_color_from_hex(COLORES["texto"]),
            halign="center",
        )
        popup = Popup(
            title="Asistente Pro",
            content=etiqueta,
            size_hint=(0.8, 0.25),
            background=get_color_from_hex(COLORES["superficie"]),
            title_color=get_color_from_hex(COLORES["primario_claro"]),
        )
        popup.open()
        Clock.schedule_once(lambda *a: popup.dismiss(), 2.5)


if __name__ == "__main__":
    AsistenteProApp().run()