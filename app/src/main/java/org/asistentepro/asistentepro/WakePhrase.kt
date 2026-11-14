package org.asistentepro.asistentepro

import java.text.Normalizer

/** This is a phrase detector, not voice identification: it accepts any speaker. */
object WakePhrase {
    // Common phonetic spellings of Xy-lon produced by the Spanish recognizer.
    private val prefix = Regex("^(?:(?:oye|hola|hey|ok|okay)\\s+)?(?:xyl[oó]n|cyl[oó]n|s[aá]ilon|sai lon|saig[oó]n|sil[oó]n|xil[oó]n)(?=$|[,.:;!?\\s])[,.:;!?\\s]*", RegexOption.IGNORE_CASE)
    fun stripOptionalName(text: String): String = text.trim().replaceFirst(prefix, "").trim()
    fun extract(text: String): String? {
        val normalized = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")
        return if (prefix.containsMatchIn(normalized.trim())) stripOptionalName(normalized) else null
    }
}
