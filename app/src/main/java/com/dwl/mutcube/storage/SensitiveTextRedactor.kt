package com.dwl.mutcube.storage

/** Defense in depth for optional diagnostics; raw request and response bodies are never logged. */
internal object SensitiveTextRedactor {
    private val credentialField = "(?:api[_ -]?key|access[_ -]?key|secret|token|password|authorization)"
    fun redact(value: String): String = value
        .replace(Regex("(?i)\\bBearer\\s+[^\\s,;\\\"']+"), "Bearer [已隐藏]")
        .replace(Regex("\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b"), "[访问密钥已隐藏]")
        .replace(Regex("\\beyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"), "[JWT已隐藏]")
        .replace(Regex("sk-[A-Za-z0-9_-]{10,}"), "sk-[已隐藏]")
        .replace(Regex("(?i)([\\\"']?$credentialField[\\\"']?\\s*[:=]\\s*[\\\"'])([^\\\"']+)([\\\"'])")) {
            it.groupValues[1] + "[已隐藏]" + it.groupValues[3]
        }
        .replace(Regex("(?i)([\\\"']?$credentialField[\\\"']?\\s*[:=]\\s*)([^,}\\s&\\\"']+)")) {
            it.groupValues[1] + "[已隐藏]"
        }
}
