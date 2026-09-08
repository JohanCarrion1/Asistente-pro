#!/bin/bash
# Asistente Pro - Compilar APK con Buildozer

set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

echo "=============================================="
echo "   ASISTENTE PRO - Compilando APK"
echo "=============================================="

# Generar icono si no existe
if [ ! -f icons/icon.png ]; then
    echo "🎨 Generando icono..."
    python3 icons/generar_icono.py
fi

# Verificar buildozer
if ! command -v buildozer >/dev/null 2>&1; then
    echo "❌ buildozer no está instalado."
    echo "   Instálalo con: pip3 install --user buildozer"
    exit 1
fi

echo "🔨 Compilando (esto puede tardar varios minutos)..."
buildozer android debug || buildozer -v android debug

echo ""
echo "=============================================="
echo "   ✅ APK generado correctamente:"
echo "      $(ls -1 bin/*.apk 2>/dev/null | head -1)"
echo "=============================================="
echo "   Transfiere el APK a tu teléfono e instálalo."
echo "   (Habilita 'Instalar apps desconocidas' si es necesario)"
echo "=============================================="