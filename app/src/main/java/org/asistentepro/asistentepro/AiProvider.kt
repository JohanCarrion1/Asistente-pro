package org.asistentepro.asistentepro

enum class AiProvider(val title: String, val defaultModel: String, val keysUrl: String, val endpoint: String) {
    Groq("Groq", "openai/gpt-oss-120b", "https://console.groq.com/keys", "https://api.groq.com/openai/v1/chat/completions"),
    Gemini("Gemini", "gemini-3.6-flash", "https://aistudio.google.com/apikey", "https://generativelanguage.googleapis.com/v1beta/models/"),
    Anthropic("Anthropic", "claude-sonnet-4-6", "https://platform.claude.com/settings/keys", "https://api.anthropic.com/v1/messages"),
    OpenRouter("OpenRouter", "openrouter/auto", "https://openrouter.ai/settings/keys", "https://openrouter.ai/api/v1/chat/completions"),
    OpenAI("OpenAI", "gpt-4.1-mini", "https://platform.openai.com/api-keys", "https://api.openai.com/v1/chat/completions");
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: Groq }
}
