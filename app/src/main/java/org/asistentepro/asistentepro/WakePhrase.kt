package org.asistentepro.asistentepro

import java.text.Normalizer

/** This is a phrase detector, not voice identification: it accepts any speaker. */
object WakePhrase {
    private val prefix = Regex("^(?:(?:oye|hola|hey|ok|okay)\\s+)?(?:aurea|áurea|aura|auria)(?=$|[,.:;!?\\s])[,.:;!?\\s]*", RegexOption.IGNORE_CASE)
    fun stripOptionalName(text: String): String = text.trim().replaceFirst(prefix, "").trim()
    fun extract(text: String): String? {
        val normalized = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")
        return if (prefix.containsMatchIn(normalized.trim())) stripOptionalName(normalized) else null
    }
}
