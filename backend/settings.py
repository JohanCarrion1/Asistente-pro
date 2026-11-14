"""Valores sin secretos; se puede conservar un config.py local en escritorio."""
import os

API_KEY = os.environ.get("GROQ_API_KEY", "")
MODELO = os.environ.get("GROQ_MODEL", "llama-3.1-8b-instant")
GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
TIMEOUT = 30
