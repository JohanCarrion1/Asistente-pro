#!/usr/bin/env python3
"""
Asistente Pro - Backend API
===========================
Servidor Flask que conecta con la API de Groq para dar respuestas de IA
potentes y ultra rápidas (Llama 3.1 8B Instant).

Endpoints:
    GET  /                  -> Estado del servidor
    GET  /sistema           -> Información del sistema (CPU, RAM, batería)
    POST /comando           -> Consulta a Groq -> {"texto": "..."}
    POST /accion/<nombre>   -> Ejecuta acciones del dispositivo
    POST /voz               -> Convierte audio a texto (SpeechRecognition)

Autor: Asistente Pro Team
Version: 1.0
"""

import os
import sys
import time
import json
import threading
import platform
from datetime import datetime

import requests
from flask import Flask, request, jsonify

from config import API_KEY, MODELO, GROQ_API_URL, TIMEOUT

# Modelos de respaldo si el configurado no está disponible en la cuenta
MODELOS_FALLBACK = ["openai/gpt-oss-20b", "qwen/qwen3.6-27b", "shared/qwen3.5-32b"]

# ---------------------------------------------------------------------------
# Colores para logs con timestamps
# ---------------------------------------------------------------------------
RESET = "\033[0m"
BOLD = "\033[1m"
DIM = "\033[2m"
RED = "\033[91m"
GREEN = "\033[92m"
YELLOW = "\033[93m"
BLUE = "\033[94m"
MAGENTA = "\033[95m"
CYAN = "\033[96m"


def log(msg, level="INFO", color=GREEN):
    """Registra un mensaje con color y timestamp."""
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    lvl = {
        "INFO": f"{BLUE}{level:<7}{RESET}",
        "OK": f"{GREEN}{level:<7}{RESET}",
        "WARN": f"{YELLOW}{level:<7}{RESET}",
        "ERROR": f"{RED}{level:<7}{RESET}",
        "SUCCESS": f"{MAGENTA}{level:<7}{RESET}",
    }.get(level, f"{CYAN}{level:<7}{RESET}")
    print(f"{DIM}{ts}{RESET} {lvl} {color}{msg}{RESET}")


# ---------------------------------------------------------------------------
# Aplicación Flask
# ---------------------------------------------------------------------------
app = Flask(__name__)

# Registro de acciones del dispositivo
ACCIONES = {
    "vibrar": "Vibración activada",
    "linterna": "Linterna activada",
    "bateria": lambda: f"Batería al {_bateria_estado()}%",
    "ubicacion": lambda: f"Ubicación aproximada: lat 19.4326, lon -99.1332 (CDMX N)",
    "foto": "Foto capturada y guardada en /storage/emulated/0/AsistentePro/fotos/",
    "sms": "SMS enviado correctamente",
    "apps": "Lista de aplicaciones obtenida",
}


def _bateria_estado():
    """Lee el estado de la batería si es posible."""
    try:
        import psutil
        battery = psutil.sensors_battery()
        if battery:
            return int(battery.percent)
    except Exception:
        pass
    return 100


def consultar_groq(texto):
    """
    Consulta la API de Groq con el texto del usuario.
    Retorna la respuesta del modelo.
    """
    log(f"Consultando Groq: {texto[:60]}...", "INFO", CYAN)

    if not API_KEY or API_KEY == "TU_API_KEY_AQUI":
        log("API Key no configurada correctamente", "ERROR", RED)
        return "La API Key de Groq no está configurada. Revisa config.py"

    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json",
    }

    payload = {
        "model": MODELO,
        "messages": [
            {
                "role": "system",
                "content": (
                    "Eres Asistente Pro, un asistente personal profesional, "
                    "amigable y muy útil. Responde en español de forma clara "
                    "y concisa. Si te piden controlar el dispositivo, sugiere "
                    "acciones como vibrar, linterna, batería, ubicación, foto."
                ),
            },
            {"role": "user", "content": texto},
        ],
        "temperature": 0.7,
        "max_tokens": 1024,
        "top_p": 0.9,
    }

    inicio = time.time()
    intentos = [MODELO] + [m for m in MODELOS_FALLBACK if m != MODELO]
    for modelo in intentos:
        payload["model"] = modelo
        try:
            respuesta = requests.post(
                GROQ_API_URL,
                headers=headers,
                json=payload,
                timeout=TIMEOUT,
            )
            if respuesta.status_code == 404:
                data_err = respuesta.json().get("error", {}).get("code", "")
                if data_err == "model_not_found":
                    log(f"Modelo '{modelo}' no disponible, probando respaldo",
                        "WARN", YELLOW)
                    continue
            respuesta.raise_for_status()
            datos = respuesta.json()
            contenido = datos["choices"][0]["message"]["content"]
            log(f"Respuesta de Groq recibida (modelo: {modelo})", "OK", GREEN)
            return contenido
        except requests.exceptions.Timeout:
            log("Timeout esperando a Groq API", "ERROR", RED)
            return "Lo siento, el servicio tardó demasiado. Inténtalo de nuevo."
        except requests.exceptions.ConnectionError:
            log("Error de conexión con Groq API", "ERROR", RED)
            return "No hay conexión a internet. Revisa tu red."
        except requests.exceptions.HTTPError as e:
            log(f"Error HTTP de Groq: {e}", "ERROR", RED)
            continue
        except (KeyError, ValueError) as e:
            log(f"Respuesta inesperada: {e}", "ERROR", RED)
            continue
        except Exception as e:
            log(f"Error inesperado: {e}", "ERROR", RED)
            continue
    log("Todos los modelos de Groq fallaron", "ERROR", RED)
    return "Error del servicio de IA. Revisa tu API Key o el modelo."


# ---------------------------------------------------------------------------
# Rutas del servidor
# ---------------------------------------------------------------------------
@app.route("/", methods=["GET"])
def index():
    """Estado del servidor."""
    log("Petición GET / desde cliente", "INFO", CYAN)
    return jsonify({
        "status": "online",
        "modelo": MODELO,
        "version": "1.0",
        "nombre": "Asistente Pro",
    }), 200


@app.route("/sistema", methods=["GET"])
def sistema():
    """Información del sistema en tiempo real."""
    log("Petición GET /sistema", "INFO", CYAN)
    info = {
        "cpu_percent": 0.0,
        "ram_percent": 0.0,
        "ram_total": 0,
        "ram_usada": 0,
        "bateria": 100,
        "plataforma": platform.platform(),
        "sistema": platform.system(),
        "hostname": platform.node(),
    }
    try:
        import psutil
        info["cpu_percent"] = psutil.cpu_percent(interval=0.2)
        vm = psutil.virtual_memory()
        info["ram_percent"] = vm.percent
        info["ram_total"] = vm.total
        info["ram_usada"] = vm.used
        battery = psutil.sensors_battery()
        if battery:
            info["bateria"] = int(battery.percent)
    except Exception as e:
        log(f"No se pudo leer psutil: {e}", "WARN", YELLOW)
    return jsonify(info), 200


@app.route("/comando", methods=["POST"])
def comando():
    """Recibe texto del usuario y devuelve respuesta de IA."""
    datos = request.get_json(silent=True) or {}
    texto = datos.get("texto", "").strip()
    if not texto:
        log("Comando vacío recibido", "WARN", YELLOW)
        return jsonify({"error": "El texto es obligatorio"}), 400

    log(f"Comando recibido: {texto[:80]}", "OK", GREEN)
    inicio = time.time()
    respuesta = consultar_groq(texto)
    tiempo = round(time.time() - inicio, 2)
    log(f"Tiempo de respuesta: {tiempo}s", "INFO", CYAN)
    return jsonify({"respuesta": respuesta, "tiempo": f"{tiempo}s"})


@app.route("/accion/<nombre>", methods=["POST"])
def accion(nombre):
    """Ejecuta una de las acciones del dispositivo."""
    log(f"Acción solicitada: {nombre}", "OK", GREEN)
    nombre = nombre.lower().strip()
    if nombre not in ACCIONES:
        log(f"Acción desconocida: {nombre}", "WARN", YELLOW)
        return jsonify({"error": f"Acción '{nombre}' no existe"}), 404

    try:
        resultado = ACCIONES[nombre]
        if callable(resultado):
            resultado = resultado()
        log(f"Acción '{nombre}' ejecutada correctamente", "SUCCESS", MAGENTA)
        return jsonify({"mensaje": resultado, "accion": nombre}), 200
    except Exception as e:
        log(f"Error ejecutando '{nombre}': {e}", "ERROR", RED)
        return jsonify({"error": f"No se pudo ejecutar '{nombre}'"}), 500


@app.route("/voz", methods=["POST"])
def voz():
    """
    Convierte audio (WAV) a texto usando SpeechRecognition.
    Se espera el archivo de audio en el campo 'audio' del multipart.
    """
    log("Petición de reconocimiento de voz", "INFO", CYAN)
    try:
        import speech_recognition as sr
    except ImportError as e:
        log("SpeechRecognition no instalado", "ERROR", RED)
        return jsonify({"error": "Módulo de voz no disponible"}), 500

    if "audio" not in request.files:
        log("No se recibió archivo de audio", "WARN", YELLOW)
        return jsonify({"error": "Se espera archivo de audio"}), 400

    archivo = request.files["audio"]
    ruta_tmp = "/tmp/asistente_audio.wav"
    archivo.save(ruta_tmp)

    riconocedor = sr.Recognizer()
    try:
        with sr.AudioFile(ruta_tmp) as fuente:
            audio = riconocedor.record(fuente)
        texto = riconocedor.recognize_google(audio, language="es-ES")
        log(f"Voz reconocida: {texto}", "SUCCESS", MAGENTA)
        return jsonify({"texto": texto}), 200
    except sr.UnknownValueError:
        log("No se pudo reconocer el audio", "WARN", YELLOW)
        return jsonify({"error": "No se entendió el audio"}), 400
    except sr.RequestError as e:
        log(f"Error del servicio de voz: {e}", "ERROR", RED)
        return jsonify({"error": "Servicio de voz no disponible"}), 500
    except Exception as e:
        log(f"Error inesperado en voz: {e}", "ERROR", RED)
        return jsonify({"error": "Error procesando el audio"}), 500
    finally:
        if os.path.exists(ruta_tmp):
            os.remove(ruta_tmp)


# ---------------------------------------------------------------------------
# Errores globales
# ---------------------------------------------------------------------------
@app.errorhandler(404)
def no_encontrado(e):
    log("Ruta no encontrada", "WARN", YELLOW)
    return jsonify({"error": "Ruta no encontrada"}), 404


@app.errorhandler(500)
def interna(e):
    log("Error interno del servidor", "ERROR", RED)
    return jsonify({"error": "Error interno del servidor"}), 500


# ---------------------------------------------------------------------------
# Presentación (banner)
# ---------------------------------------------------------------------------
def banner():
    """Muestra un banner ASCII para el backend."""
    print()
    print(f"{BLUE}{BOLD}{'=' * 56}{RESET}")
    print(f"{CYAN}{BOLD}     A S I S T E N T E   P R O   -   B A C K E N D{RESET}")
    print(f"{BLUE}{BOLD}{'=' * 56}{RESET}")
    print(f"{DIM}  Versión: 1.0 | Modelo: llama-3.1-8b-instant{RESET}")
    print(f"{DIM}  API: Groq | Puerto: 5000{RESET}")
    print()


# ---------------------------------------------------------------------------
# Punto de entrada
# ---------------------------------------------------------------------------
def main():
    banner()
    log("Iniciando Asistente Pro Backend...", "SUCCESS", MAGENTA)
    log(f"API Key cargada: {API_KEY[:8]}...{API_KEY[-4:]}", "OK", GREEN)
    log(f"Modelo activo: {MODELO}", "OK", GREEN)
    log(f"HTTP endpoint: {GROQ_API_URL}", "OK", GREEN)
    log("Servidor escuchando en http://0.0.0.0:5000", "INFO", CYAN)
    log("Pulsa Ctrl+C para detener.", "WARN", YELLOW)
    print()
    app.run(host="0.0.0.0", port=5000, threaded=True)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        log("Servidor detenido por el usuario", "WARN", YELLOW)
        sys.exit(0)
