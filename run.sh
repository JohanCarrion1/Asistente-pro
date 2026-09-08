#!/bin/bash
# Asistente Pro - Script de inicio (backend + app)

set -e

DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=============================================="
echo "   ASISTENTE PRO - Iniciando sistema"
echo "=============================================="

cd "$DIR/backend"
echo "🚀 Iniciando backend Flask en http://0.0.0.0:5000 ..."
python3 api.py &
BACKEND_PID=$!

sleep 2

if ! kill -0 $BACKEND_PID 2>/dev/null; then
    echo "❌ El backend no arrancó correctamente. Revisa los logs."
    exit 1
fi

cd "$DIR"
echo "📱 Iniciando aplicación Kivy..."
python3 app.py

echo "🛑 Deteniendo backend..."
kill $BACKEND_PID 2>/dev/null || true