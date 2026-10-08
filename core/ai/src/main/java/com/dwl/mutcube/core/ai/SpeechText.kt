package com.dwl.mutcube.core.ai

object SpeechText {
    fun segments(text: String): List<String> {
        val plain = text.replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("!?\\[([^]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("(?m)^\\s{0,3}[#>]+\\s*"), "")
            .replace(Regex("[*_`~]"), "").trim()
        val result = mutableListOf<String>()
        var start = 0
        var limit = 90 // A short first segment minimizes time to first speech.
        while (start < plain.length) {
            val end = (start + limit).coerceAtMost(plain.length)
            val boundary = (start until end).lastOrNull { plain[it] in "。！？.!?；;\n" }
            var cut = if (boundary != null && boundary > start) boundary + 1 else end
            if (cut < plain.length && cut > start && plain[cut - 1].isHighSurrogate()) cut--
            plain.substring(start, cut).trim().takeIf(String::isNotEmpty)?.let(result::add)
            start = cut
            limit = 260
        }
        return result
    }
}
